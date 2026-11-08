#!/usr/bin/env python3
"""NeroCompanion asset checker (decision 010 section 10).

Cross-references everything the Java registers against the resources it needs, and checks the resource tree:

* the texture and sound generators' ``--check`` (no drift between the generators and the files on disk);
* every registered entity, item, block, sound event and particle has its model / texture / blockstate / lang /
  subtitle; every shipped skin and hat has its textures (per type it allows), name, and a hat model;
* every JSON file under ``common/src/main/resources`` parses, and ``lang/en_us.json`` keys are sorted;
* no private planning names anywhere under ``common/src/main/resources``, ``wiki/`` or ``tools/``.

Usage::

    python3 tools/check_companion_assets.py            # all checks; exit 1 on any problem
    python3 tools/check_companion_assets.py --no-gen   # skip the two generator --check runs (no numpy/Pillow)

Registered ids are read from the Java source (the registries, ``ChipKinds``, ``ModSounds``, ``ModParticles``,
``CompanionGeometry``), so a new registration without its assets fails here before it reaches a client.
"""

import argparse
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "common", "src", "main", "java", "za", "co", "neroland", "nerocompanion")
RES = os.path.join(ROOT, "common", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "nerocompanion")
DATA = os.path.join(RES, "data", "nerocompanion")
MOD = "nerocompanion"

# Assembled from pieces so this file does not trip its own scan.
PRIVATE = [
    "neroland" + "-mc-" + "ecosystem",
    "BUILD" + "-PROMPT",
    "PLAN" + "-0.5.0",
    "SMOKE" + "-TEST",
    "AUD" + "IT.md",
    "AUD" + "IT-0.5.0",
]
PRIVATE_CI = ["mod" + "rinth"]
TEXT_EXT = (".json", ".md", ".py", ".txt", ".mcmeta", ".properties", ".toml", ".cfg", ".yml", ".yaml", ".prefs",
            ".java", ".lang", ".ps1", ".sh", "")

problems = []


def problem(msg):
    problems.append(msg)


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def java(rel):
    return read(os.path.join(JAVA, rel))


def load_json(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError) as e:
        problem(f"{os.path.relpath(path, ROOT)}: does not parse ({e})")
        return None


def exists(rel_under_assets):
    return os.path.isfile(os.path.join(ASSETS, rel_under_assets))


def sprite_file(sprite):
    """nerocompanion:item/x -> textures/item/x.png (only our namespace is checked)."""
    ns, _, path = sprite.partition(":") if ":" in sprite else ("minecraft", "", sprite)
    return None if ns != MOD else f"textures/{path}.png"


# ------------------------------------------------------------------------------------------- registries
def registered():
    ids = {}
    ents = java("registry/ModEntities.java")
    ids["entity"] = sorted(set(re.findall(r'ENTITY_TYPES\.register\(\s*"([a-z0-9_]+)"', ents)))
    blocks = java("registry/ModBlocks.java")
    ids["block"] = sorted(set(re.findall(r'BLOCKS\.register\(\s*"([a-z0-9_]+)"', blocks)))
    items_src = java("registry/ModItems.java")
    items = set(re.findall(r'ITEMS\.register\(\s*"([a-z0-9_]+)"', items_src))
    items |= set(re.findall(r'blockItem\(\s*"([a-z0-9_]+)"', items_src))
    types = [t for t in ids["entity"] if t != "fetch_ball"]
    items |= {t + "_spawn_egg" for t in types}
    kinds = java("chip/ChipKinds.java")
    for name in re.findall(r'=\s*stat\(\s*"([a-z0-9_]+)"', kinds):
        items |= {f"{name}_chip_{t}" for t in range(1, 4)}
    for name in re.findall(r'=\s*(?:capability|cosmetic)\(\s*"([a-z0-9_]+)"', kinds):
        items.add(f"{name}_chip")
    for name, tiers in re.findall(r'=\s*add\(\s*"([a-z0-9_]+)"\s*,\s*ChipCategory\.\w+\s*,\s*(\d+)', kinds):
        tiers = int(tiers)
        items |= {f"{name}_chip_{t}" for t in range(1, tiers + 1)} if tiers > 1 else {f"{name}_chip"}
    ids["item"] = sorted(items)
    ids["types"] = types
    ids["sound"] = re.findall(r'register\("([a-z0-9_.]+)"\)', java("registry/ModSounds.java"))
    ids["particle"] = re.findall(r'register\("([a-z0-9_]+)"\)', java("registry/ModParticles.java"))
    geo = java("client/model/CompanionGeometry.java")
    m = re.search(r'HATS = List\.of\(([^;]*)\);', geo, re.S)
    ids["hat_models"] = re.findall(r'"([a-z0-9_]+)"', m.group(1)) if m else []
    return ids


# ------------------------------------------------------------------------------------------------ checks
def check_json_tree():
    for base, _, files in os.walk(RES):
        for name in files:
            if name.endswith(".json") or name.endswith(".mcmeta"):
                load_json(os.path.join(base, name))
    lang = load_json(os.path.join(ASSETS, "lang", "en_us.json")) or {}
    keys = list(lang.keys())
    if keys != sorted(keys):
        problem("lang/en_us.json: keys are not sorted")
    return lang


def model_textures(model_path, seen=None):
    """Every nerocompanion texture a model (and its nerocompanion parents) names."""
    seen = seen or set()
    if model_path in seen:
        return []
    seen.add(model_path)
    data = load_json(os.path.join(ASSETS, "models", model_path + ".json"))
    if data is None:
        return []
    out = [v for v in (data.get("textures") or {}).values() if isinstance(v, str) and not v.startswith("#")]
    parent = data.get("parent", "")
    if parent.startswith(MOD + ":"):
        if not exists("models/" + parent.split(":", 1)[1] + ".json"):
            problem(f"models/{model_path}.json: parent {parent} missing")
        else:
            out += model_textures(parent.split(":", 1)[1], seen)
    for override in data.get("overrides") or []:
        ref = override.get("model", "")
        if ref.startswith(MOD + ":") and not exists("models/" + ref.split(":", 1)[1] + ".json"):
            problem(f"models/{model_path}.json: override model {ref} missing")
    return out


def item_definition_models(node):
    """Every model id an items/*.json definition references."""
    out = []
    if isinstance(node, dict):
        if node.get("type") in ("minecraft:model", "model") and isinstance(node.get("model"), str):
            out.append(node["model"])
        for v in node.values():
            out += item_definition_models(v)
    elif isinstance(node, list):
        for v in node:
            out += item_definition_models(v)
    return out


def check_models_textures(model_id, where):
    if not model_id.startswith(MOD + ":"):
        return
    path = model_id.split(":", 1)[1]
    if not exists(f"models/{path}.json"):
        problem(f"{where}: model {model_id} missing")
        return
    for tex in model_textures(path):
        f = sprite_file(tex)
        if f and not exists(f):
            problem(f"models/{path}.json: texture {tex} missing")


def check_items(ids, lang):
    block_items = set(ids["block"])
    for item in ids["item"]:
        if not exists(f"models/item/{item}.json"):
            problem(f"item {item}: models/item/{item}.json missing")
        else:
            check_models_textures(f"{MOD}:item/{item}", f"item {item}")
        definition = os.path.join(ASSETS, "items", f"{item}.json")
        if not os.path.isfile(definition):
            problem(f"item {item}: items/{item}.json missing (1.21.4+ item definition)")
        else:
            for model in item_definition_models(load_json(definition)):
                check_models_textures(model, f"items/{item}.json")
        key = (f"block.{MOD}.{item}" if item in block_items else f"item.{MOD}.{item}")
        if key not in lang:
            problem(f"item {item}: lang {key} missing")


def check_blocks(ids, lang):
    for block in ids["block"]:
        state = os.path.join(ASSETS, "blockstates", f"{block}.json")
        if not os.path.isfile(state):
            problem(f"block {block}: blockstates/{block}.json missing")
        else:
            data = load_json(state) or {}
            models = []
            for variant in (data.get("variants") or {}).values():
                for v in (variant if isinstance(variant, list) else [variant]):
                    models.append(v.get("model", ""))
            for part in data.get("multipart") or []:
                apply = part.get("apply", {})
                for v in (apply if isinstance(apply, list) else [apply]):
                    models.append(v.get("model", ""))
            for model in models:
                check_models_textures(model, f"blockstates/{block}.json")
        if f"block.{MOD}.{block}" not in lang:
            problem(f"block {block}: lang block.{MOD}.{block} missing")
        if not os.path.isfile(os.path.join(DATA, "loot_table", "blocks", f"{block}.json")):
            problem(f"block {block}: loot_table/blocks/{block}.json missing")


def check_entities(ids, lang):
    for entity in ids["entity"]:
        if f"entity.{MOD}.{entity}" not in lang:
            problem(f"entity {entity}: lang entity.{MOD}.{entity} missing")
    for t in ids["types"]:
        for suffix in ("", "_glow"):
            if not exists(f"textures/entity/{t}/default{suffix}.png"):
                problem(f"entity {t}: textures/entity/{t}/default{suffix}.png missing")


def check_sounds(ids, lang):
    sounds = load_json(os.path.join(ASSETS, "sounds.json")) or {}
    for key in ids["sound"]:
        entry = sounds.get(key)
        if entry is None:
            problem(f"sound {key}: not in sounds.json")
            continue
        subtitle = entry.get("subtitle")
        if not subtitle:
            problem(f"sound {key}: no subtitle in sounds.json")
        elif subtitle not in lang:
            problem(f"sound {key}: subtitle {subtitle} not in lang")
        for s in entry.get("sounds", []):
            name = s if isinstance(s, str) else s.get("name", "")
            if isinstance(s, dict) and s.get("type") == "event":
                continue
            if name.startswith(MOD + ":") and not exists("sounds/" + name.split(":", 1)[1] + ".ogg"):
                problem(f"sound {key}: sounds/{name.split(':', 1)[1]}.ogg missing")
    for key in sounds:
        if key not in ids["sound"]:
            problem(f"sounds.json: {key} has no registered SoundEvent")


def check_particles(ids):
    for pid in ids["particle"]:
        path = os.path.join(ASSETS, "particles", f"{pid}.json")
        if not os.path.isfile(path):
            problem(f"particle {pid}: particles/{pid}.json missing")
            continue
        for tex in (load_json(path) or {}).get("textures", []):
            ns, _, p = tex.partition(":")
            if ns == MOD and not exists(f"textures/particle/{p}.png"):
                problem(f"particle {pid}: textures/particle/{p}.png missing")


def looks(kind):
    base = os.path.join(DATA, kind)
    out = {}
    for d, _, files in os.walk(base):
        for name in files:
            if name.endswith(".json"):
                rel = os.path.relpath(os.path.join(d, name), base).replace(os.sep, "/")[:-5]
                out[rel] = load_json(os.path.join(d, name))
    return out


def texture_of(template, type_, frame=0):
    sprite = template.replace("{type}", type_).replace("{frame}", str(frame))
    return sprite_file(sprite)


def check_looks(ids, lang):
    for kind, prefix in (("skins", "skin"), ("hats", "hat")):
        for look_id, data in sorted(looks(kind).items()):
            if not isinstance(data, dict):
                continue
            types = data.get("types") or []
            allowed = ids["types"] if "*" in types else [t.split(":", 1)[1] for t in types if ":" in t]
            if not allowed:
                problem(f"{kind}/{look_id}: no known type")
            key = f"{prefix}.{MOD}.{look_id.replace('/', '.')}"
            if key not in lang:
                problem(f"{kind}/{look_id}: lang {key} missing")
            for t in allowed:
                for field in ("texture", "glow"):
                    template = data.get(field)
                    if template:
                        f = texture_of(template, t)
                        if f and not exists(f):
                            problem(f"{kind}/{look_id}: {field} {f} missing for {t}")
                anim = data.get("animated_glow") or {}
                for frame in range(int(anim.get("frames", 0) or 0)):
                    f = texture_of(anim.get("frame_texture", ""), t, frame)
                    if f and not exists(f):
                        problem(f"{kind}/{look_id}: glow frame {f} missing for {t}")
            unlock = data.get("unlock") or {}
            item = unlock.get("item") if unlock.get("rule") == "item" else None
            if item and item.startswith(MOD + ":") and item.split(":", 1)[1] not in ids["item"]:
                problem(f"{kind}/{look_id}: unlock item {item} is not registered")
            if kind == "hats" and look_id not in ids["hat_models"]:
                problem(f"hats/{look_id}: no hat model in CompanionGeometry.HATS")


def check_private():
    for top in (RES, os.path.join(ROOT, "wiki"), os.path.join(ROOT, "tools")):
        for base, dirs, files in os.walk(top):
            dirs[:] = [d for d in dirs if d not in ("__pycache__", "build", ".gradle")]
            for name in files:
                path = os.path.join(base, name)
                if os.path.splitext(name)[1].lower() not in TEXT_EXT:
                    continue
                try:
                    text = read(path)
                except (OSError, UnicodeDecodeError):
                    continue
                rel = os.path.relpath(path, ROOT)
                for word in PRIVATE:
                    if word in text:
                        problem(f"{rel}: private name '{word}'")
                low = text.lower()
                for word in PRIVATE_CI:
                    if word in low:
                        problem(f"{rel}: private name '{word}'")


def run_generators():
    for script in ("gen_companion_textures.py", "gen_companion_sounds.py"):
        result = subprocess.run([sys.executable, os.path.join(ROOT, "tools", script), "--check"],
                                capture_output=True, text=True, cwd=ROOT)
        if result.returncode != 0:
            tail = (result.stdout + result.stderr).strip().splitlines()[-3:]
            problem(f"{script} --check failed: {' | '.join(tail)}")


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--no-gen", action="store_true", help="skip the generators' --check runs")
    args = ap.parse_args(argv)
    if not args.no_gen:
        run_generators()
    lang = check_json_tree()
    ids = registered()
    check_items(ids, lang)
    check_blocks(ids, lang)
    check_entities(ids, lang)
    check_sounds(ids, lang)
    check_particles(ids)
    check_looks(ids, lang)
    check_private()
    counts = ", ".join(f"{len(ids[k])} {k}" for k in ("entity", "item", "block", "sound", "particle"))
    if problems:
        for p in problems:
            print("FAIL " + p)
        print(f"check_companion_assets: {len(problems)} problem(s) ({counts})")
        return 1
    print(f"check_companion_assets: OK ({counts}, {len(looks('skins'))} skins, {len(looks('hats'))} hats)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
