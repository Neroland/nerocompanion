package za.co.neroland.nerocompanion.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Checks the shipped quest pack ({@code data/nerocompanion/neroquests}) against the quest-mod pack rules
 * without that mod on the classpath: every quest and chapter id resolves, titles/descriptions are
 * translation keys present in {@code en_us.json}, prerequisites stay inside this pack, objective/reward
 * types are the shipped set, every {@code gate_open} / {@code mod_hook} gate has a {@code neroland_gates}
 * file in this jar, and hook gates are player-scope records without {@code requires}. The pack is inert
 * data, so a mistake here would otherwise only surface as a dropped quest on a running server.
 */
class QuestPackTest {

    static final String NAMESPACE = "nerocompanion";
    static final Set<String> OBJECTIVES = Set.of("collect_item", "craft_item", "kill_entity", "reach_dimension",
            "visit_structure", "gate_open", "quest_complete", "custom_event", "economy", "manual");
    static final Set<String> REWARDS = Set.of("item", "xp", "currency", "mod_hook");
    static final String KEY = "[a-z0-9_-]+(\\.[a-z0-9_-]+)+";

    @Test
    void packFollowsTheQuestFormat() throws IOException, URISyntaxException {
        Path root = resourcesRoot();
        Path data = root.resolve("data/" + NAMESPACE + "/neroquests");
        Map<String, JsonObject> quests = read(data.resolve("quests"));
        Map<String, JsonObject> chapters = read(data.resolve("chapters"));
        Map<String, JsonObject> gates = read(root.resolve("data/" + NAMESPACE + "/neroland_gates"));
        JsonObject lang = JsonParser.parseString(Files.readString(
                root.resolve("assets/" + NAMESPACE + "/lang/en_us.json"))).getAsJsonObject();
        assertEquals(6, quests.size(), "the Companion Log chapter ships six quests");
        assertEquals(1, chapters.size(), "one chapter");

        List<String> problems = new ArrayList<>();
        Set<String> serverOk = new HashSet<>();
        Set<String> listed = new HashSet<>();
        chapters.forEach((id, c) -> {
            key(problems, lang, id, c, "title", true);
            for (JsonElement e : c.getAsJsonArray("quests")) {
                String q = e.getAsJsonObject().get("quest").getAsString();
                if (!quests.containsKey(q)) {
                    problems.add(id + " lists unknown/foreign quest " + q);
                }
                if (!listed.add(q)) {
                    problems.add(id + " lists " + q + " twice");
                }
                if (c.has("allow_server_scope") && c.get("allow_server_scope").getAsBoolean()) {
                    serverOk.add(q);
                }
            }
        });
        quests.forEach((id, q) -> {
            if (!listed.contains(id)) {
                problems.add(id + ": not placed in any chapter");
            }
            key(problems, lang, id, q, "title", true);
            key(problems, lang, id, q, "description", false);
            boolean server = q.has("scope") && "server".equals(q.get("scope").getAsString());
            if (server && !serverOk.contains(id)) {
                problems.add(id + ": scope server needs allow_server_scope");
            }
            if (q.has("prerequisites")) {
                q.getAsJsonArray("prerequisites").forEach(p -> ref(problems, quests, id, p.getAsString()));
            }
            JsonArray objectives = q.getAsJsonArray("objectives");
            if (objectives == null || objectives.isEmpty()) {
                problems.add(id + ": no objectives");
            } else {
                for (JsonElement e : objectives) {
                    JsonObject o = e.getAsJsonObject();
                    String type = o.get("type").getAsString().replace("neroquests:", "");
                    if (!OBJECTIVES.contains(type)) {
                        problems.add(id + ": unknown objective " + type);
                    }
                    if (type.equals("quest_complete")) {
                        ref(problems, quests, id, o.get("quest").getAsString());
                    }
                    if (type.equals("custom_event")) {
                        problems.add(id + ": this mod publishes no channels, so custom_event can never advance");
                    }
                    if (type.equals("gate_open")) {
                        String gate = o.get("gate").getAsString();
                        if (gate.startsWith(NAMESPACE + ":")) {
                            JsonObject def = gates.get(gate);
                            if (def == null) {
                                problems.add(id + ": gate_open names " + gate + " but no neroland_gates file ships it");
                            } else if (!"player".equals(def.get("scope").getAsString()) || def.has("requires")) {
                                problems.add(id + ": milestone gate " + gate + " must be a player-scope record without requires");
                            }
                        }
                    }
                }
            }
            if (q.has("rewards")) {
                for (JsonElement e : q.getAsJsonArray("rewards")) {
                    JsonObject r = e.getAsJsonObject();
                    String type = r.get("type").getAsString().replace("neroquests:", "");
                    if (!REWARDS.contains(type)) {
                        problems.add(id + ": reward " + type + " not allowed in a pack");
                    }
                    if (type.equals("mod_hook")) {
                        String gate = r.get("gate").getAsString();
                        if (!gate.startsWith(NAMESPACE + ":hook/")) {
                            problems.add(id + ": mod_hook gate must be " + NAMESPACE + ":hook/<id>");
                        }
                        JsonObject def = gates.get(gate);
                        if (def == null) {
                            problems.add(id + ": mod_hook names " + gate + " but no neroland_gates file ships it");
                        } else if (!"player".equals(def.get("scope").getAsString()) || def.has("requires")) {
                            problems.add(id + ": hook gate " + gate + " must be a player-scope record without requires");
                        }
                    }
                }
            }
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void gatesTheCodeNamesAreShippedAndPlayerScoped() throws IOException, URISyntaxException {
        Map<String, JsonObject> gates = read(resourcesRoot().resolve("data/" + NAMESPACE + "/neroland_gates"));
        List<String> expected = new ArrayList<>(List.of(CompanionMilestones.FIRST_DEPLOY, CompanionMilestones.FIRST_CHIP,
                CompanionMilestones.PET_ADOPTED, CompanionMilestones.FULL_SQUAD).stream().map(Object::toString).toList());
        for (String skin : QuestHooks.SKINS) {
            expected.add(QuestHooks.skinHook(skin).toString());
        }
        for (String gate : expected) {
            JsonObject def = gates.get(gate);
            assertTrue(def != null && "player".equals(def.get("scope").getAsString()) && !def.has("requires"),
                    gate + " must ship as a player-scope record gate without requires");
        }
        for (String gate : gates.keySet()) {
            if (gate.startsWith(NAMESPACE + ":" + QuestHooks.SKIN_HOOK_PREFIX)) {
                String skin = gate.substring((NAMESPACE + ":" + QuestHooks.SKIN_HOOK_PREFIX).length());
                assertTrue(QuestHooks.SKINS.contains(skin), "QuestHooks.SKINS does not list shipped skin hook " + skin);
            }
        }
        assertEquals(expected.size(), gates.size(), "every shipped gate is one the code names");
    }

    @Test
    void langKeysAreSorted() throws IOException, URISyntaxException {
        JsonObject lang = JsonParser.parseString(Files.readString(
                resourcesRoot().resolve("assets/" + NAMESPACE + "/lang/en_us.json"))).getAsJsonObject();
        List<String> keys = new ArrayList<>(lang.keySet());
        List<String> sorted = new ArrayList<>(keys);
        sorted.sort(null);
        assertEquals(sorted, keys, "en_us.json keys must be sorted");
        assertTrue(lang.has("message.nerocompanion.skin_unlocked"), "QuestHooks chat line has a lang key");
    }

    /** The common resources root, found through the test classpath (skips when packaged as a jar). */
    static Path resourcesRoot() throws URISyntaxException {
        URL marker = QuestPackTest.class.getClassLoader()
                .getResource("data/" + NAMESPACE + "/neroquests/chapters/companion_log.json");
        assumeTrue(marker != null && "file".equals(marker.getProtocol()), "pack resources not on the classpath as files");
        return Path.of(marker.toURI()).getParent().getParent().getParent().getParent().getParent();
    }

    static void ref(List<String> problems, Map<String, JsonObject> quests, String id, String target) {
        if (target.startsWith("neroquests:")) {
            return;
        }
        if (!target.startsWith(NAMESPACE + ":")) {
            problems.add(id + ": depends on another pack's quest " + target);
        } else if (!quests.containsKey(target)) {
            problems.add(id + ": unknown quest " + target);
        }
    }

    static void key(List<String> problems, JsonObject lang, String id, JsonObject o, String field, boolean required) {
        if (!o.has(field)) {
            if (required) {
                problems.add(id + ": missing " + field);
            }
            return;
        }
        String key = o.get(field).getAsString();
        if (!key.matches(KEY)) {
            problems.add(id + ": " + field + " is not a translation key");
        } else if (!lang.has(key)) {
            problems.add(id + ": en_us.json has no " + key);
        }
    }

    static Map<String, JsonObject> read(Path dir) throws IOException {
        Map<String, JsonObject> out = new TreeMap<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String rel = dir.relativize(f).toString().replace('\\', '/');
                out.put(NAMESPACE + ":" + rel.substring(0, rel.length() - 5),
                        JsonParser.parseString(Files.readString(f)).getAsJsonObject());
            }
        }
        return out;
    }
}
