#!/usr/bin/env python3
"""
gen_companion_textures.py -- deterministic generator for every NeroCompanion texture, block/item model,
particle definition and skin/hat data file.

Usage:
    python3 tools/gen_companion_textures.py            # write into the repo (resources tree next to this file)
    python3 tools/gen_companion_textures.py --out DIR  # write into DIR instead (preview)
    python3 tools/gen_companion_textures.py --check    # regenerate in memory and compare with the repo; exit 1 on drift
    python3 tools/gen_companion_textures.py --geometry # print the cuboid tables (markdown) and exit

Pixel art only: no anti-aliasing, no gradients wider than three steps, no letters or numbers (the sleep Z is the
one exception). Entity sheets follow the vanilla box UV layout (top | bottom over right | front | left | back),
so every cube listed in the geometry tables maps 1:1 onto the islands painted here. The widget sheet
(textures/gui/companion_widgets.png) is NOT produced by this script and is left untouched.
"""
import argparse
import io
import json
import os
import random
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "common", "src", "main", "resources")
MOD = "nerocompanion"

# --------------------------------------------------------------------------------------------- palette
PALETTE = {
    "G_DARK": "#1A1D22", "GRAPHITE": "#2C3137", "GRAPHITE_4": "#3A4047", "G_LIGHT": "#8E98A4", "TRIM": "#5B6670",
    "STEEL": "#7A8492", "STEEL_DK": "#424A56", "NERO_ALLOY": "#26A69A", "PLASMA": "#60D4E8", "GLASS": "#0B1119",
    "HAZ_Y": "#FFCE2C", "HAZ_K": "#18181E",
    "SHELL": "#9AA4B2", "SHELL_LT": "#C3CBD6", "SHELL_DK": "#6B7585", "RUBBER": "#23262B",
    "COMPANION_TEAL": "#56CCC4", "TEAL_BRIGHT": "#A8F0EA", "EYE_WHITE": "#E8FCFA", "TEAL_DIM": "#2F6C68",
    "WARN_AMBER": "#FFB347", "LOW_RED": "#FF5C5C", "OK_GREEN": "#58E07A", "ION_BLUE": "#7FD7FF", "VIOLET": "#B48CFF",
    "BOND_PINK": "#FF8CCF", "SCRAP_ORANGE": "#FF8C42", "RUST": "#8A4B2E", "RUST_DK": "#4A2A1C", "AURORA_GREEN": "#7CFFB2",
    # chip slab (shared family chip look)
    "CHIP_BODY": "#2A3140", "CHIP_EDGE": "#3A4458", "CHIP_GREY": "#8A93A6",
    # GUI hull
    "PANEL": "#141C26", "PANEL_EDGE": "#2A3A4D",
    # skin-only tones
    "AURORA_BASE": "#1A2238", "AURORA_LT": "#2A3550", "AURORA_DK": "#111726",
    "SNOW": "#EAF4FF", "NIGHT_BASE": "#1C2230", "NIGHT_LT": "#2A3245", "NIGHT_DK": "#141A26", "NIGHT_LENS": "#C8A8FF",
    "COURIER": "#F2F5F8", "COURIER_LT": "#FFFFFF", "SLATE": "#2E3440", "SLATE_LT": "#3B4252", "MOSS": "#3E8A4C",
    "MOSS_LT": "#58E07A", "ION_DK": "#3F7F9F",
}
TYPES = ["personal_drone", "scout_drone", "mining_bot", "carry_bot", "repair_bot", "combat_bot", "scrapling"]
ROLE = {"personal_drone": "COMPANION_TEAL", "scout_drone": "VIOLET", "mining_bot": "WARN_AMBER",
        "carry_bot": "OK_GREEN", "repair_bot": "ION_BLUE", "combat_bot": "LOW_RED", "scrapling": "SCRAP_ORANGE"}
WHITE = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)


def rgb(name_or_hex, a=255):
    h = PALETTE.get(name_or_hex, name_or_hex).lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(3)) + (255,)


# --------------------------------------------------------------------------------------------- canvas
class Sheet:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.a = np.zeros((h, w, 4), dtype=np.uint8)

    def px(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x] = c

    def rect(self, x, y, w, h, c):
        if w <= 0 or h <= 0:
            return
        x0, y0 = max(0, x), max(0, y)
        x1, y1 = min(self.w, x + w), min(self.h, y + h)
        if x1 > x0 and y1 > y0:
            self.a[y0:y1, x0:x1] = c

    def hline(self, x, y, w, c):
        self.rect(x, y, w, 1, c)

    def vline(self, x, y, h, c):
        self.rect(x, y, 1, h, c)

    def outline(self, x, y, w, h, c):
        self.hline(x, y, w, c)
        self.hline(x, y + h - 1, w, c)
        self.vline(x, y, h, c)
        self.vline(x + w - 1, y, h, c)

    def glyph(self, x, y, rows, colours, default=None):
        """Paint ASCII art: '.' is transparent (skipped), other chars index `colours`."""
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == ".":
                    continue
                c = colours.get(ch, default)
                if c is not None:
                    self.px(x + i, y + j, c)

    def blit(self, other, x, y):
        for j in range(other.h):
            for i in range(other.w):
                c = other.a[j, i]
                if c[3]:
                    self.px(x + i, y + j, tuple(int(v) for v in c))

    def image(self):
        return Image.fromarray(self.a, "RGBA")

    def png_bytes(self):
        buf = io.BytesIO()
        self.image().save(buf, "PNG", optimize=False)
        return buf.getvalue()


# --------------------------------------------------------------------------------------------- box UV
def faces(u, v, w, h, d):
    """Vanilla box UV islands: (x, y, w, h) per face name (right = the companion's own right side)."""
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def footprint(w, h, d):
    return 2 * (w + d), d + h


class Style:
    """A skin: palette remaps plus flags the type painters consult."""

    def __init__(self, name="default", remap=None, **flags):
        self.name = name
        self.remap = remap or {}
        self.flags = flags

    def c(self, key):
        return rgb(self.remap.get(key, key))

    def f(self, key, default=None):
        return self.flags.get(key, default)


# --------------------------------------------------------------------------------------------- materials
def shell_face(s, st, x, y, w, h, side="side", seed=0, panel=True, rivets=0, soft=True, outline=True):
    """Brushed pale shell: horizontal streaks, dark outline ring, soft corners, one panel line, rivets."""
    lt, md, dk = st.c("SHELL_LT"), st.c("SHELL"), st.c("SHELL_DK")
    if side == "top":
        lt, md, dk = lt, lt, md
    elif side == "bottom":
        lt, md, dk = md, dk, dk
    rnd = random.Random(seed * 7919 + w * 131 + h * 17)
    s.rect(x, y, w, h, md)
    for j in range(h):   # horizontal streaks (never vertical on companions)
        r = rnd.random()
        if j == 1 or (j > 1 and r < 0.18):
            s.hline(x, y + j, w, lt)
        elif j == h - 2 or r > 0.86:
            s.hline(x, y + j, w, dk)
    if outline:
        g = rgb("G_DARK")
        s.hline(x, y, w, st.c("STEEL_DK") if side != "bottom" else g)   # lit top edge
        s.hline(x, y + h - 1, w, g)
        s.vline(x, y, h, g)
        s.vline(x + w - 1, y, h, g)
    if soft and w >= 3 and h >= 3:
        for cx, cy in ((x, y), (x + w - 1, y), (x, y + h - 1), (x + w - 1, y + h - 1)):
            s.px(cx, cy, st.c("SHELL_DK"))
    if panel and w >= 5 and h >= 4:
        s.hline(x + 1, y + h - 2 - (h >= 6), w - 2, st.c("STEEL_DK"))
    if rivets and w >= 5 and h >= 4:
        for k in range(min(rivets, 4)):
            rx = x + 1 + (k % 2) * (w - 3)
            ry = y + 1 + (k // 2) * (h - 3)
            s.px(rx, ry, rgb("G_LIGHT"))
            if ry + 1 < y + h - 1:
                s.px(rx, ry + 1, rgb("G_DARK"))


def flat_face(s, x, y, w, h, col, edge=None, hi=None, lo=None):
    s.rect(x, y, w, h, col)
    if hi is not None and h >= 3:
        s.hline(x + 1, y + 1, w - 2, hi)
    if lo is not None and h >= 3:
        s.hline(x + 1, y + h - 2, w - 2, lo)
    if edge is not None:
        s.outline(x, y, w, h, edge)


def rubber_face(s, x, y, w, h, cleats=False, phase=0):
    flat_face(s, x, y, w, h, rgb("RUBBER"), edge=rgb("G_DARK"), hi=rgb("GRAPHITE_4"))
    if cleats:
        for i in range(x + 1, x + w - 1):
            if (i + phase) % 4 == 0:
                s.vline(i, y + 1, h - 2, rgb("STEEL_DK"))


def graphite_face(s, x, y, w, h, seams=True):
    flat_face(s, x, y, w, h, rgb("GRAPHITE"), edge=rgb("G_DARK"), hi=rgb("GRAPHITE_4"))
    if seams and w >= 6 and h >= 4:
        s.vline(x + w // 2, y + 1, h - 2, rgb("TRIM"))


def rust_face(s, x, y, w, h, seed=0, weld=True):
    rnd = random.Random(seed * 31 + 5)
    s.rect(x, y, w, h, rgb("RUST"))
    for j in range(h):
        for i in range(w):
            if rnd.random() < 0.22:
                s.px(x + i, y + j, rgb("RUST_DK"))
    s.outline(x, y, w, h, rgb("G_DARK"))
    if weld and w >= 4 and h >= 3:
        s.hline(x + 1, y + h // 2, w - 2, rgb("SCRAP_ORANGE"))
    if w >= 4 and h >= 4:
        s.px(x + 1, y + 1, rgb("G_LIGHT"))
        s.px(x + w - 2, y + h - 2, rgb("G_LIGHT"))


def glass_inset(s, x, y, w, h):
    s.rect(x, y, w, h, rgb("GLASS"))
    s.px(x, y, rgb("STEEL_DK"))
    s.px(x + w - 1, y, rgb("STEEL_DK"))
    if w >= 4:
        s.px(x + 1, y, rgb("TRIM"))


def paint_box(base, glow, st, u, v, w, h, d, material="shell", seed=0, rivets=0, panel=True, skip=(), **kw):
    """Paint all six faces of a box; `skip` names faces left transparent (hidden against another part)."""
    for name, (x, y, fw, fh) in faces(u, v, w, h, d).items():
        if name in skip or fw <= 0 or fh <= 0:
            continue
        side = "top" if name == "top" else "bottom" if name == "bottom" else "side"
        if material == "shell":
            shell_face(base, st, x, y, fw, fh, side, seed + len(name) * 13, panel=panel, rivets=rivets)
        elif material == "rubber":
            rubber_face(base, x, y, fw, fh, cleats=kw.get("cleats", False) and name != "front", phase=kw.get("phase", 0))
        elif material == "graphite":
            graphite_face(base, x, y, fw, fh, seams=kw.get("seams", True))
        elif material == "rust":
            rust_face(base, x, y, fw, fh, seed + len(name), weld=kw.get("weld", True))
        elif material == "flat":
            flat_face(base, x, y, fw, fh, kw["col"], edge=kw.get("edge", rgb("G_DARK")), hi=kw.get("hi"), lo=kw.get("lo"))
        elif material == "steel":
            flat_face(base, x, y, fw, fh, rgb("STEEL"), edge=rgb("G_DARK"), hi=rgb("G_LIGHT"), lo=rgb("STEEL_DK"))


def band(base, glow, u, v, w, h, d, row, col, faces_=("front", "left", "right", "back"), glow_only=False):
    """One-texel emissive role band around a box at side-face row `row`."""
    for name, (x, y, fw, fh) in faces(u, v, w, h, d).items():
        if name in faces_ and 0 <= row < fh:
            if not glow_only:
                base.hline(x + 1, y + row, fw - 2, col)
            glow.hline(x + 1, y + row, fw - 2, col)


def eye_plate(glow, u, v, w, h, variant, kind="pair"):
    """
    Eye expression islands on the glow sheet. The plate is a w x h x 1 box; only its front face (at u+1, v+1) is
    painted. Eyes are painted WHITE so the renderer tints them with the energy / skin eye colour.
    kind: 'pair' (two 2-px eyes at the plate's ends), 'scrap' (left 2 px, right 1 px), 'lens' (one bar).
    """
    x, y = u + 1, v + 1
    W = WHITE
    soft = (190, 190, 190, 255)
    if kind == "lens":
        if variant == "open":
            glow.rect(x, y, w, 2, W)
            for i in range(x, x + w, 3):
                glow.px(i, y + 1, soft)
        else:
            glow.hline(x, y + 1, w, soft)
        return
    eyes = [(x, 2), (x + w - 2, 2)] if kind == "pair" else [(x, 2), (x + w - 1, 1)]
    for ex, ew in eyes:
        if variant == "open":
            glow.rect(ex, y, ew, 2, W)
            if ew == 2:
                glow.px(ex + 1, y + 1, soft)
        elif variant == "happy":
            glow.rect(ex, y, ew, 1, W)
            glow.px(ex if ex == x else ex + ew - 1, y + 1, W)
        elif variant == "low":
            glow.rect(ex, y + 1, ew, 1, W)
            glow.rect(ex, y, ew, 1, (120, 120, 120, 255))
        elif variant == "closed":
            glow.rect(ex, y + 2 if h >= 3 else y + 1, ew, 1, soft)


# --------------------------------------------------------------------------------------------- geometry
# Model space: vanilla entity-model units (1/16 block), +Y DOWN, y = 24 is the ground under the feet, -Z is forward.
# pivot = the part's PartPose offset (model space for root children, parent space otherwise);
# origin = addBox corner relative to the pivot; size = (w, h, d); uv = texOffs.
def P(name, parent, pivot, origin, size, uv, material="shell", inflate=0.0, mirror=False, note=""):
    return dict(name=name, parent=parent, pivot=pivot, origin=origin, size=size, uv=uv, material=material,
                inflate=inflate, mirror=mirror, note=note)


EYE_UVS = {  # type -> ((u,v) for eyes_open, eyes_happy, eyes_low, eyes_closed), plate size (w,h,d)
    "personal_drone": (((18, 16), (18, 20), (18, 24), (18, 28)), (6, 3, 1)),
    "mining_bot": (((48, 0), (48, 4), (48, 8), (48, 12)), (6, 3, 1)),
    "carry_bot": (((36, 20), (50, 20), (36, 24), (50, 24)), (6, 3, 1)),
    "repair_bot": (((0, 18), (12, 18), (0, 22), (12, 22)), (5, 3, 1)),
    "combat_bot": (((0, 30), (14, 30), (0, 34), (14, 34)), (6, 3, 1)),
    "scrapling": (((0, 13), (12, 13), (24, 13), (36, 13)), (5, 3, 1)),
}
EYE_NAMES = ("eyes_open", "eyes_happy", "eyes_low", "eyes_closed")
EYE_VARIANTS = ("open", "happy", "low", "closed")


def eye_parts(type_, parent, origin):
    uvs, size = EYE_UVS[type_]
    return [P(n, parent, (0, 0, 0), origin, size, uv, "eyes",
              note="glow-sheet island; exactly one visible") for n, uv in zip(EYE_NAMES, uvs)]


GEOMETRY = {
    "personal_drone": dict(tex=(32, 32), parts=[
        P("body", "root", (0, 21, 0), (-4, -3, -4), (8, 6, 8), (0, 0), note="visor 6x3 inset on the front face rows 1-3"),
        P("fin_left", "body", (-4, 0, 0), (-3, -0.5, -3), (3, 1, 6), (0, 16), note="zRot tilts/folds"),
        P("fin_right", "body", (4, 0, 0), (0, -0.5, -3), (3, 1, 6), (0, 16), mirror=True),
        P("nub", "body", (0, -3, 0), (-1, -1, -1), (2, 1, 2), (0, 24), note="dorsal sensor; its sides are the role band"),
        *eye_parts("personal_drone", "body", (-3, -2, -4.5)),
        P("hat", "body", (0, -4, 0), None, None, None, "empty", note="hat anchor, model (0,17,0)"),
    ]),
    "scout_drone": dict(tex=(32, 32), parts=[
        P("body", "root", (0, 21, 0), (-4, -3, -6), (8, 4, 6), (0, 0), note="nose; lens bar on the front face"),
        P("wing_left", "body", (-4, -1, 0), (-5, -0.5, -4), (5, 3, 10), (0, 10), note="leading-edge band glows"),
        P("wing_right", "body", (4, -1, 0), (0, -0.5, -4), (5, 3, 10), (0, 10), mirror=True),
        P("lens_open", "body", (0, 0, 0), (-4, -2, -6.5), (8, 2, 1), (0, 23), "eyes", note="glow island"),
        P("lens_closed", "body", (0, 0, 0), (-4, -2, -6.5), (8, 2, 1), (0, 26), "eyes", note="glow island"),
        P("hat", "body", (0, -3, -2), None, None, None, "empty", note="hat anchor, model (0,18,-2); hat_scale 0.75"),
    ]),
    "mining_bot": dict(tex=(64, 64), parts=[
        P("chassis", "root", (0, 24, 0), (-6, -14, -6), (12, 10, 12), (0, 0), note="y 10..20"),
        P("tread_left_a", "root", (0, 24, 0), (-7, -4, -7), (3, 4, 14), (0, 34), "rubber", note="frame A (still / even)"),
        P("tread_left_b", "root", (0, 24, 0), (-7, -4, -7), (3, 4, 14), (2, 34), "rubber", note="frame B (cleats shifted 2 px)"),
        P("tread_right_a", "root", (0, 24, 0), (4, -4, -7), (3, 4, 14), (0, 34), "rubber"),
        P("tread_right_b", "root", (0, 24, 0), (4, -4, -7), (3, 4, 14), (2, 34), "rubber"),
        P("head", "chassis", (0, -14, -1), (-4, -4, -3), (8, 4, 6), (0, 22), note="amber band row 0"),
        *eye_parts("mining_bot", "head", (-3, -3, -3.5)),
        P("drill_arm", "chassis", (0, -9, -6), (-2, -2, -8), (4, 4, 8), (28, 22), note="hazard chevrons on the guard; xRot presses"),
        P("drill_tip", "drill_arm", (0, 0, -8), (-1, -1, -4), (2, 2, 4), (52, 22), "steel", note="yRot spins"),
        P("hat", "head", (0, -4, 0), None, None, None, "empty", note="hat anchor, model (0,6,-1)"),
    ]),
    "carry_bot": dict(tex=(64, 64), parts=[
        P("tub", "root", (0, 15, 0), (-6, -10, -5), (12, 10, 10), (0, 0), note="top face painted as the open tub interior"),
        P("cab", "root", (0, 20, 0), (-5, -5, -4), (10, 5, 8), (0, 20), note="eyes on the cab front"),
        *eye_parts("carry_bot", "cab", (-3, -4, -4.5)),
        P("handle_left", "tub", (-6, -5, 0), (-2, -1, -3), (2, 2, 6), (12, 33), "steel"),
        P("handle_right", "tub", (6, -5, 0), (0, -1, -3), (2, 2, 6), (12, 33), "steel", mirror=True),
        P("cargo", "tub", (0, -10, 0), (-4, -2, -3), (8, 3, 6), (28, 33), "flat", note="visible only while WORKING in CARRY"),
        P("leg_fl", "root", (-3.5, 20, -2.5), (-1.5, 0, -1.5), (3, 4, 3), (0, 33), "graphite"),
        P("leg_fr", "root", (3.5, 20, -2.5), (-1.5, 0, -1.5), (3, 4, 3), (0, 33), "graphite", mirror=True),
        P("leg_rl", "root", (-3.5, 20, 2.5), (-1.5, 0, -1.5), (3, 4, 3), (0, 33), "graphite"),
        P("leg_rr", "root", (3.5, 20, 2.5), (-1.5, 0, -1.5), (3, 4, 3), (0, 33), "graphite", mirror=True),
        P("hat", "tub", (0, -10, 0), None, None, None, "empty", note="hat anchor on the rim, model (0,5,0)"),
    ]),
    "repair_bot": dict(tex=(64, 64), parts=[
        P("column", "root", (0, 20, 0), (-4, -10, -4), (8, 10, 8), (0, 0), note="blue band row 2"),
        P("head", "column", (0, -10, 0), (-3.5, -5, -3.5), (7, 5, 7), (32, 0)),
        *eye_parts("repair_bot", "head", (-2.5, -4, -4)),
        P("arm_left", "column", (-4, -9, 0), (-2, -7, -1), (2, 7, 2), (0, 40), note="shoulder pivot; xRot"),
        P("arm_right", "column", (4, -9, 0), (0, -7, -1), (2, 7, 2), (0, 40), mirror=True),
        P("tool_wrench", "arm_left", (-1, -7, 0), (-1.5, -2, -1.5), (3, 2, 3), (24, 40), "steel"),
        P("tool_welder", "arm_right", (1, -7, 0), (-1.5, -2, -1.5), (3, 2, 3), (36, 40), "steel", note="nozzle glow texel"),
        P("leg_front", "root", (0, 20, -3), (-1, 0, -1), (2, 4, 2), (28, 18), "graphite"),
        P("leg_left", "root", (-3, 20, 2), (-1, 0, -1), (2, 4, 2), (28, 18), "graphite"),
        P("leg_right", "root", (3, 20, 2), (-1, 0, -1), (2, 4, 2), (28, 18), "graphite"),
        P("hat", "head", (0, -5, 0), None, None, None, "empty", note="hat anchor, model (0,5,0)"),
    ]),
    "combat_bot": dict(tex=(64, 64), parts=[
        P("torso", "root", (0, 16, 0), (-4, -9, -3.5), (8, 9, 7), (24, 11)),
        P("plate", "torso", (0, -9, 0), (-8, -7, -4), (16, 3, 8), (0, 0), note="red band on the front edge, row 1"),
        P("head", "torso", (0, -9, 0), (-3, -4, -3), (6, 5, 6), (0, 11), note="sunk 1 px into the torso; eyes peek under the plate"),
        *eye_parts("combat_bot", "head", (-3, -3, -3.5)),
        P("emitter", "torso", (0, -9, 0), (2, -4, -4), (4, 2, 6), (0, 22), "graphite", note="LOW_RED glow texel on the front"),
        P("leg_front", "root", (0, 16, -2), (-1.5, 0, -1.5), (3, 8, 3), (0, 44), note="rubber foot, bottom 2 rows"),
        P("leg_left", "root", (-2.5, 16, 2), (-1.5, 0, -1.5), (3, 8, 3), (0, 44)),
        P("leg_right", "root", (2.5, 16, 2), (-1.5, 0, -1.5), (3, 8, 3), (0, 44), mirror=True),
        P("hat", "torso", (0, -16, 0), None, None, None, "empty", note="hat anchor on the plate top, model (0,0,0)"),
    ]),
    "scrapling": dict(tex=(64, 32), parts=[
        P("body", "root", (0, 21, 0), (-3.5, -5, -4), (7, 5, 8), (0, 0), "scrap", note="front half SHELL, rear half RUST"),
        P("head", "body", (0, -4, -4), (-2.5, -3, -5), (5, 4, 5), (30, 0), note="cheek texels on the side faces"),
        *eye_parts("scrapling", "head", (-2.5, -2, -5.5)),
        P("tail", "body", (0, -4, 4), (-0.5, -0.5, 0), (1, 1, 5), (40, 20), "steel", note="rest pose xRot -0.5 (curls up)"),
        P("tail_tip", "tail", (0, 0, 5), (-0.5, -2, -0.5), (1, 2, 1), (52, 20), "steel", note="heart glow texel at the top"),
        P("leg_fl", "root", (-2.5, 21, -2), (-1, 0, -1), (2, 3, 2), (0, 20), "scrap"),
        P("leg_fr", "root", (2.5, 21, -2), (-1, 0, -1), (2, 3, 2), (8, 20), "scrap"),
        P("leg_rl", "root", (-2.5, 21, 2), (-1, 0, -1), (2, 3, 2), (16, 20), "scrap"),
        P("leg_rr", "root", (2.5, 21, 2), (-1, 0, -1), (2, 3, 2), (24, 20), "scrap"),
        P("hat", "head", (0, -3, -1.5), None, None, None, "empty", note="hat anchor, model (0,14,-6.5); hat_scale 0.75"),
    ]),
}
# model-override extra part (mossback): a leaf on the tail tip
MOSSBACK_LEAF = P("leaf", "tail_tip", (0, -2, 0), (-1, -1, -1), (2, 1, 2), (56, 20), "flat")


def part(type_, name):
    for p in GEOMETRY[type_]["parts"]:
        if p["name"] == name:
            return p
    raise KeyError(name)


def check_geometry():
    """Every island fits its sheet and no two islands overlap (mirrors / shared UVs excepted)."""
    for t, g in GEOMETRY.items():
        W, H = g["tex"]
        taken = {}
        for p in g["parts"] + ([MOSSBACK_LEAF] if t == "scrapling" else []):
            if p["uv"] is None:
                continue
            w, h, d = p["size"]
            fw, fh = footprint(w, h, d)
            u, v = p["uv"]
            assert u + fw <= W and v + fh <= H, f"{t}.{p['name']} island {fw}x{fh} at {p['uv']} leaves the {W}x{H} sheet"
            for yy in range(v, v + fh):
                for xx in range(u, u + fw):
                    other = taken.get((xx, yy))
                    if other and other[:2] != (p["uv"], p["size"]):
                        if "tread" in p["name"] and "tread" in other[2]:
                            continue   # tread frames deliberately sample one island at two offsets
                        raise AssertionError(f"{t}.{p['name']} overlaps {other[2]} at {(xx, yy)}")
                    taken[(xx, yy)] = (p["uv"], p["size"], p["name"])


def geometry_markdown():
    out = []
    for t in TYPES:
        g = GEOMETRY[t]
        out.append(f"### `{t}` — sheet {g['tex'][0]}×{g['tex'][1]}\n")
        out.append("| part | parent | pivot (x,y,z) | origin | size w×h×d | UV | inflate | material | note |")
        out.append("|---|---|---|---|---|---|---|---|---|")
        parts = g["parts"] + ([MOSSBACK_LEAF] if t == "scrapling" else [])
        for p in parts:
            size = "—" if p["size"] is None else "×".join(str(v) for v in p["size"])
            org = "—" if p["origin"] is None else str(tuple(p["origin"]))
            uv = "—" if p["uv"] is None else str(tuple(p["uv"]))
            mir = " (mirror)" if p["mirror"] else ""
            out.append(f"| `{p['name']}` | `{p['parent']}` | {tuple(p['pivot'])} | {org} | {size} | {uv}{mir} | {p['inflate']} | "
                       f"{p['material']} | {p['note']} |")
        out.append("")
    return "\n".join(out)


# --------------------------------------------------------------------------------------------- entity painters
def paint_parts(base, glow, st, type_, seed=1):
    """Default pass over every cube; type painters then add their signature details."""
    for i, p in enumerate(GEOMETRY[type_]["parts"]):
        if p["uv"] is None or p["material"] in ("eyes", "empty"):
            continue
        u, v = p["uv"]
        w, h, d = p["size"]
        m = p["material"]
        if m == "shell":
            paint_box(base, glow, st, u, v, w, h, d, "shell", seed=seed + i, rivets=2 if max(w, d) >= 7 else 0)
        elif m == "rubber":
            paint_box(base, glow, st, u, v, w, h, d, "rubber", cleats=True, phase=0)
        elif m == "graphite":
            paint_box(base, glow, st, u, v, w, h, d, "graphite", seams=False)
        elif m == "steel":
            paint_box(base, glow, st, u, v, w, h, d, "steel")
        elif m == "flat":
            paint_box(base, glow, st, u, v, w, h, d, "flat", col=rgb("TRIM"), hi=rgb("G_LIGHT"))
        elif m == "scrap":
            paint_box(base, glow, st, u, v, w, h, d, "rust", seed=seed + i)


def paint_eyes(glow, type_):
    if type_ == "scout_drone":
        for name, variant in (("lens_open", "open"), ("lens_closed", "closed")):
            p = part(type_, name)
            eye_plate(glow, p["uv"][0], p["uv"][1], p["size"][0], p["size"][1], variant, "lens")
        return
    uvs, (w, h, d) = EYE_UVS[type_]
    for (u, v), variant in zip(uvs, EYE_VARIANTS):
        eye_plate(glow, u, v, w, h, variant, "scrap" if type_ == "scrapling" else "pair")


def visor(base, type_, head_part, cols, rows, width, height):
    """GLASS inset on the head/body front face."""
    p = part(type_, head_part)
    fx, fy, fw, fh = faces(*p["uv"], *p["size"])["front"]
    glass_inset(base, fx + cols, fy + rows, width, height)


def contacts(base, glow, type_, part_name, pts):
    """Charge contact texels on a bottom face (base: NERO_ALLOY stud; glow: TEAL_BRIGHT)."""
    p = part(type_, part_name)
    bx, by, bw, bh = faces(*p["uv"], *p["size"])["bottom"]
    for (i, j) in pts:
        base.px(bx + i, by + j, rgb("NERO_ALLOY"))
        glow.px(bx + i, by + j, rgb("TEAL_BRIGHT"))


def paint_personal_drone(st):
    base, glow = Sheet(32, 32), Sheet(32, 32)
    paint_parts(base, glow, st, "personal_drone")
    visor(base, "personal_drone", "body", 1, 1, 6, 3)
    body = part("personal_drone", "body")
    u, v = body["uv"]
    nub = part("personal_drone", "nub")
    col = st.c(ROLE["personal_drone"])
    for name, (x, y, fw, fh) in faces(*nub["uv"], *nub["size"]).items():
        if name in ("front", "back", "left", "right"):
            base.rect(x, y, fw, fh, col)
            glow.rect(x, y, fw, fh, col)
    bx, by, bw, bh = faces(u, v, 8, 6, 8)["back"]
    base.hline(bx + 1, by + 2, bw - 2, col)
    glow.hline(bx + 1, by + 2, bw - 2, col)
    contacts(base, glow, "personal_drone", "body", ((2, 5), (5, 5)))
    fin = part("personal_drone", "fin_left")
    fx, fy, fw, fh = faces(*fin["uv"], *fin["size"])["top"]
    tip = rgb(st.f("fin_tip")) if st.f("fin_tip") else st.c("SHELL_LT")
    base.hline(fx, fy, fw, tip)
    if st.f("pinstripe"):
        for name, (x, y, fw, fh) in faces(u, v, 8, 6, 8).items():
            if name in ("front", "back", "left", "right"):
                base.hline(x + 1, y + 4, fw - 2, rgb(st.f("pinstripe")))
    if st.f("snowcap"):
        tx, ty, tw, th = faces(u, v, 8, 6, 8)["top"]
        base.rect(tx + 1, ty + 1, tw - 2, 2, rgb("SNOW"))
        base.rect(tx + 2, ty + 3, tw - 4, 1, rgb("SNOW"))
    paint_eyes(glow, "personal_drone")
    return base, glow


def paint_scout_drone(st):
    base, glow = Sheet(32, 32), Sheet(32, 32)
    paint_parts(base, glow, st, "scout_drone")
    body = part("scout_drone", "body")
    fx, fy, fw, fh = faces(*body["uv"], *body["size"])["front"]
    glass_inset(base, fx, fy + 1, fw, 2)
    col = st.c(ROLE["scout_drone"])
    wing = part("scout_drone", "wing_left")
    wf = faces(*wing["uv"], *wing["size"])
    x, y, w, h = wf["front"]
    base.hline(x, y + 1, w, col)
    glow.hline(x, y + 1, w, col)
    tx, ty, tw, th = wf["top"]
    base.hline(tx, ty, tw, col)
    glow.hline(tx, ty, tw, col)
    if st.f("double_edge"):
        base.hline(tx, ty + th - 1, tw, col)
        glow.hline(tx, ty + th - 1, tw, col)
        base.vline(tx + tw - 1, ty, th, col)
    bx, by, bw, bh = wf["back"]
    for i in (1, 3):
        base.px(bx + i, by + 1, rgb("GLASS"))
        glow.px(bx + i, by + 1, col)
    paint_eyes(glow, "scout_drone")
    return base, glow


def hazard_stripes(s, x, y, w, h, phase=0):
    for j in range(h):
        for i in range(w):
            s.px(x + i, y + j, rgb("HAZ_Y") if ((i + j + phase) // 2) % 2 == 0 else rgb("HAZ_K"))


def paint_mining_bot(st):
    base, glow = Sheet(64, 64), Sheet(64, 64)
    paint_parts(base, glow, st, "mining_bot")
    visor(base, "mining_bot", "head", 1, 1, 6, 3)
    col = st.c(ROLE["mining_bot"])
    head = part("mining_bot", "head")
    band(base, glow, *head["uv"], *head["size"], 0, col)
    # tread island: 36 wide (frame B samples it shifted 2 px) - continuous cleat pattern
    tread = part("mining_bot", "tread_left_a")
    u, v = tread["uv"]
    w, h, d = tread["size"]
    for name, (x, y, fw_, fh_) in faces(u, v, w, h, d).items():
        rubber_face(base, x, y, fw_ + (2 if name == "back" else 0), fh_, cleats=name != "front", phase=0)
    # drill guard: hazard chevrons on the arm's faces - the only hazard stripes on a companion
    arm = part("mining_bot", "drill_arm")
    af = faces(*arm["uv"], *arm["size"])
    for name in ("left", "right", "top", "bottom"):
        x, y, fw_, fh_ = af[name]
        hazard_stripes(base, x + 1, y + 1, 3, fh_ - 2)
    x, y, fw_, fh_ = af["front"]
    graphite_face(base, x, y, fw_, fh_, seams=False)
    if st.f("chevrons"):
        ch = part("mining_bot", "chassis")
        cf = faces(*ch["uv"], *ch["size"])
        for name in ("left", "right"):
            x, y, fw_, fh_ = cf[name]
            hazard_stripes(base, x + 1, y + 3, fw_ - 2, 3, phase=1)
    contacts(base, glow, "mining_bot", "chassis", ((3, 6), (8, 6)))
    paint_eyes(glow, "mining_bot")
    return base, glow


def paint_carry_bot(st):
    base, glow = Sheet(64, 64), Sheet(64, 64)
    paint_parts(base, glow, st, "carry_bot")
    visor(base, "carry_bot", "cab", 2, 1, 6, 3)
    col = st.c(ROLE["carry_bot"])
    tub = part("carry_bot", "tub")
    band(base, glow, *tub["uv"], *tub["size"], 0, col)
    tx, ty, tw, th = faces(*tub["uv"], *tub["size"])["top"]   # open tub: interior floor + rim ring
    base.rect(tx, ty, tw, th, st.c("SHELL_LT"))
    base.rect(tx + 1, ty + 1, tw - 2, th - 2, rgb("GRAPHITE"))
    base.rect(tx + 2, ty + 2, tw - 4, th - 4, rgb("GRAPHITE_4"))
    base.outline(tx, ty, tw, th, rgb("G_DARK"))
    for cx, cy in ((tx, ty), (tx + tw - 1, ty), (tx, ty + th - 1), (tx + tw - 1, ty + th - 1)):
        base.px(cx, cy, st.c("SHELL_DK"))
    if st.f("diagonal"):
        for name in ("left", "right", "front", "back"):
            x, y, fw_, fh_ = faces(*tub["uv"], *tub["size"])[name]
            for j in range(1, fh_ - 1):
                i = 1 + (j * (fw_ - 3)) // max(1, fh_ - 2)
                base.px(x + i, y + j, rgb("COMPANION_TEAL"))
    contacts(base, glow, "carry_bot", "cab", ((2, 6), (7, 6)))
    paint_eyes(glow, "carry_bot")
    return base, glow


def paint_repair_bot(st):
    base, glow = Sheet(64, 64), Sheet(64, 64)
    paint_parts(base, glow, st, "repair_bot")
    visor(base, "repair_bot", "head", 1, 1, 5, 3)
    col = st.c(ROLE["repair_bot"])
    column = part("repair_bot", "column")
    band(base, glow, *column["uv"], *column["size"], 2, col)
    if st.f("panels"):
        for name in ("left", "right", "back"):
            x, y, fw_, fh_ = faces(*column["uv"], *column["size"])[name]
            base.rect(x + 1, y + 4, fw_ - 2, fh_ - 6, rgb("ION_DK"))
            base.hline(x + 1, y + 4, fw_ - 2, rgb("ION_BLUE"))
    wr = part("repair_bot", "tool_wrench")
    x, y, fw_, fh_ = faces(*wr["uv"], *wr["size"])["top"]
    base.px(x + 1, y + 1, rgb("G_DARK"))
    we = part("repair_bot", "tool_welder")
    x, y, fw_, fh_ = faces(*we["uv"], *we["size"])["top"]
    base.px(x + 1, y + 1, rgb("GLASS"))
    glow.px(x + 1, y + 1, rgb("WARN_AMBER"))
    if st.f("rust_tools"):
        for tp in (wr, we):
            for name, (x, y, fw_, fh_) in faces(*tp["uv"], *tp["size"]).items():
                rust_face(base, x, y, fw_, fh_, seed=3, weld=False)
    contacts(base, glow, "repair_bot", "column", ((2, 6), (5, 6)))
    paint_eyes(glow, "repair_bot")
    return base, glow


def paint_combat_bot(st):
    base, glow = Sheet(64, 64), Sheet(64, 64)
    paint_parts(base, glow, st, "combat_bot")
    visor(base, "combat_bot", "head", 0, 1, 6, 3)
    col = st.c(ROLE["combat_bot"])
    plate = part("combat_bot", "plate")
    pf = faces(*plate["uv"], *plate["size"])
    if st.f("slate"):
        for name, (x, y, fw_, fh_) in pf.items():
            flat_face(base, x, y, fw_, fh_, rgb("SLATE"), edge=rgb("PLASMA"), hi=rgb("SLATE_LT"))
    band(base, glow, *plate["uv"], *plate["size"], 1, col, faces_=("front",))
    leg = part("combat_bot", "leg_front")
    for name, (x, y, fw_, fh_) in faces(*leg["uv"], *leg["size"]).items():
        if name in ("front", "back", "left", "right"):
            base.rect(x, y + fh_ - 2, fw_, 2, rgb("RUBBER"))
        elif name == "bottom":
            base.rect(x, y, fw_, fh_, rgb("RUBBER"))
    em = part("combat_bot", "emitter")
    x, y, fw_, fh_ = faces(*em["uv"], *em["size"])["front"]
    base.rect(x + 1, y, fw_ - 2, fh_, rgb("GLASS"))
    glow.rect(x + 1, y, fw_ - 2, fh_, col)
    contacts(base, glow, "combat_bot", "torso", ((2, 5), (5, 5)))
    paint_eyes(glow, "combat_bot")
    return base, glow


def paint_scrapling(st):
    base, glow = Sheet(64, 32), Sheet(64, 32)
    paint_parts(base, glow, st, "scrapling")
    body = part("scrapling", "body")
    bf = faces(*body["uv"], *body["size"])
    rear_mat = st.f("rear", "rust")

    def rear(x, y, w, h, seed):
        if rear_mat == "moss":
            flat_face(base, x, y, w, h, rgb("MOSS"), edge=rgb("G_DARK"), hi=rgb("MOSS_LT"))
            base.px(x + 1, y + 1, rgb("RUST"))
        elif rear_mat == "tin":
            shell_face(base, st, x, y, w, h, "side", seed, rivets=2)
        else:
            rust_face(base, x, y, w, h, seed=seed, weld=False)

    for name, (x, y, fw_, fh_) in bf.items():
        if name in ("left", "right", "top", "bottom"):
            half = fw_ // 2
            shell_face(base, st, x, y, half, fh_, "top" if name == "top" else "bottom" if name == "bottom" else "side", 5)
            rear(x + half, y, fw_ - half, fh_, 11)
            base.vline(x + half, y, fh_, rgb("SCRAP_ORANGE"))
            glow.vline(x + half, y + 1, fh_ - 2, rgb("SCRAP_ORANGE"))
        elif name == "front":
            shell_face(base, st, x, y, fw_, fh_, "side", 6)
        else:
            rear(x, y, fw_, fh_, 13)
    head = part("scrapling", "head")
    hf = faces(*head["uv"], *head["size"])
    for name, (x, y, fw_, fh_) in hf.items():
        shell_face(base, st, x, y, fw_, fh_, "top" if name == "top" else "bottom" if name == "bottom" else "side",
                   21 + len(name), panel=False)
    x, y, fw_, fh_ = hf["front"]
    glass_inset(base, x, y + 1, fw_, 3)
    for name in ("left", "right"):   # cheeks: the two pink texels, front-bottom of each side face
        x, y, fw_, fh_ = hf[name]
        base.px(x + (fw_ - 1 if name == "right" else 0), y + fh_ - 2, rgb("BOND_PINK"))
    for i, leg in enumerate(("leg_fl", "leg_fr", "leg_rl", "leg_rr")):   # legs: each a different RUST/SHELL mix
        lp = part("scrapling", leg)
        for name, (x, y, fw_, fh_) in faces(*lp["uv"], *lp["size"]).items():
            if rear_mat == "tin" or (i + len(name)) % 2 == 0:
                shell_face(base, st, x, y, fw_, fh_, "side", 30 + i, panel=False, soft=False)
            else:
                rust_face(base, x, y, fw_, fh_, seed=40 + i, weld=False)
    tip = part("scrapling", "tail_tip")   # heart glow texel on the tip
    x, y, fw_, fh_ = faces(*tip["uv"], *tip["size"])["top"]
    base.px(x, y, rgb("GLASS"))
    glow.px(x, y, rgb("BOND_PINK"))
    for name in ("front", "back"):
        x, y, fw_, fh_ = faces(*tip["uv"], *tip["size"])[name]
        base.px(x, y, rgb("BOND_PINK"))
        glow.px(x, y, rgb("BOND_PINK"))
    if st.f("leaf"):
        u, v = MOSSBACK_LEAF["uv"]
        paint_box(base, glow, st, u, v, 2, 1, 2, "flat", col=rgb("MOSS_LT"), edge=rgb("MOSS"))
        tl = part("scrapling", "tail")
        x, y, fw_, fh_ = faces(*tl["uv"], *tl["size"])["top"]
        base.px(x, y + 1, rgb("MOSS_LT"))
    paint_eyes(glow, "scrapling")
    return base, glow


PAINTERS = {"personal_drone": paint_personal_drone, "scout_drone": paint_scout_drone, "mining_bot": paint_mining_bot,
            "carry_bot": paint_carry_bot, "repair_bot": paint_repair_bot, "combat_bot": paint_combat_bot,
            "scrapling": paint_scrapling}


# --------------------------------------------------------------------------------------------- skins
# (skin name, type or "*", Style, unlock, eye_color or None)
SKINS = [
    ("default", "*", Style("default"), {"rule": "default"}, None),
    ("aurora", "*", Style("aurora", {"SHELL": "AURORA_BASE", "SHELL_LT": "AURORA_LT", "SHELL_DK": "AURORA_DK"}),
     {"rule": "hook", "hook": f"{MOD}:hook/skin/aurora"}, "AURORA_GREEN"),
    ("graphite", "personal_drone", Style("graphite", {"SHELL": "GRAPHITE", "SHELL_LT": "GRAPHITE_4", "SHELL_DK": "G_DARK"},
                                         pinstripe="WARN_AMBER"), {"rule": "bond", "level": 2}, "#FFE2B0"),
    ("snowdrift", "personal_drone", Style("snowdrift", {"SHELL": "SHELL_LT", "SHELL_LT": "SNOW", "SHELL_DK": "SHELL"},
                                          snowcap=True, fin_tip="SNOW"), {"rule": "season", "season": "winter"}, "ION_BLUE"),
    ("nightwatch", "scout_drone", Style("nightwatch", {"SHELL": "NIGHT_BASE", "SHELL_LT": "NIGHT_LT", "SHELL_DK": "NIGHT_DK"},
                                        double_edge=True), {"rule": "bond", "level": 3}, "NIGHT_LENS"),
    ("hazard", "mining_bot", Style("hazard", chevrons=True), {"rule": "item", "item": f"{MOD}:hazard_decal"}, "WARN_AMBER"),
    ("courier", "carry_bot", Style("courier", {"SHELL": "COURIER", "SHELL_LT": "COURIER_LT", "SHELL_DK": "SHELL_LT"},
                                   diagonal=True), {"rule": "shop", "price": 250}, None),
    ("workshop", "repair_bot", Style("workshop", panels=True, rust_tools=True), {"rule": "bond", "level": 2}, "ION_BLUE"),
    ("sentinel", "combat_bot", Style("sentinel", {"SHELL": "SHELL_DK", "SHELL_LT": "SHELL", "SHELL_DK": "STEEL_DK"}, slate=True),
     {"rule": "bond", "level": 4}, None),
    ("mossback", "scrapling", Style("mossback", rear="moss", leaf=True), {"rule": "bond", "level": 3}, "AURORA_GREEN"),
    ("tinplate", "scrapling", Style("tinplate", {"SHELL": "SHELL_LT", "SHELL_LT": "SNOW", "SHELL_DK": "SHELL"}, rear="tin"),
     {"rule": "advancement", "id": f"{MOD}:pet_adopted"}, None),
]
AURORA_FRAMES, AURORA_FRAMETIME = 8, 4


def aurora_glow_frame(glow, type_, k):
    """Frame k of the aurora sweep: a 3-colour band drifting across every top face, role band recoloured."""
    out = Sheet(glow.w, glow.h)
    out.a[:] = glow.a
    cols = [rgb("AURORA_GREEN"), rgb("VIOLET"), rgb("PLASMA")]
    for p in GEOMETRY[type_]["parts"]:
        if p["uv"] is None or p["material"] in ("eyes", "empty", "rubber", "graphite"):
            continue
        x, y, fw, fh = faces(*p["uv"], *p["size"])["top"]
        if fw < 3 or fh < 3:
            continue
        for i in range(1, fw - 1):
            phase = (i * 3 + k * 2) % 16
            if phase < 6:
                c = cols[0] if phase < 3 else cols[1]
            elif phase < 10:
                c = cols[2] if phase < 8 else cols[1]
            else:
                continue
            for j in range(1, fh - 1):
                if (i + j + k) % 3 != 0:
                    out.px(x + i, y + j, c)
    role = rgb(ROLE[type_])
    mask = (glow.a[:, :, 0] == role[0]) & (glow.a[:, :, 1] == role[1]) & (glow.a[:, :, 2] == role[2]) & (glow.a[:, :, 3] > 0)
    out.a[mask] = cols[k % 3]
    return out


# --------------------------------------------------------------------------------------------- hats
# cubes: (name, origin, size, uv, colour key or "rust", flags); all sit at y <= 0 in `hat` part space (grow upward)
HATS = {
    "hard_hat": dict(types=["*"], unlock={"rule": "item", "item": f"{MOD}:hat_hard_hat"}, glow=False, cubes=[
        ("dome", (-4, -5, -4), (8, 4, 8), (0, 0), "HAZ_Y", {}),
        ("brim", (-5, -1, -5), (10, 1, 10), (0, 12), "HAZ_Y", {}),
    ]),
    "bobble_beanie": dict(types=["*"], unlock={"rule": "bond", "level": 1}, glow=False, cubes=[
        ("dome", (-4, -5, -4), (8, 5, 8), (0, 0), "COMPANION_TEAL", {"knit": True}),
        ("bobble", (-1, -7, -1), (2, 2, 2), (32, 0), "EYE_WHITE", {}),
    ]),
    "tiny_crown": dict(types=["*"], unlock={"rule": "bond", "level": 4}, glow=False, cubes=[
        ("ring", (-3, -3, -3), (6, 3, 6), (0, 0), "WARN_AMBER", {}),
        ("point_fl", (-3, -4, -3), (1, 1, 1), (24, 0), "WARN_AMBER", {}),
        ("point_fr", (2, -4, -3), (1, 1, 1), (24, 0), "WARN_AMBER", {}),
        ("point_bl", (-3, -4, 2), (1, 1, 1), (24, 0), "WARN_AMBER", {}),
        ("point_br", (2, -4, 2), (1, 1, 1), (24, 0), "WARN_AMBER", {}),
        ("jewel", (-0.5, -2, -3.5), (1, 1, 1), (28, 0), "BOND_PINK", {}),
    ]),
    "sprout": dict(types=["*"], unlock={"rule": "advancement", "id": f"{MOD}:first_deploy"}, glow=False, cubes=[
        ("stem", (-0.5, -4, -0.5), (1, 4, 1), (0, 0), "OK_GREEN", {"edge": "MOSS"}),
        ("leaf_left", (-2.5, -4, -0.5), (2, 1, 1), (4, 0), "OK_GREEN", {"edge": "MOSS"}),
        ("leaf_right", (0.5, -3, -0.5), (2, 1, 1), (4, 0), "OK_GREEN", {"edge": "MOSS"}),
    ]),
    "halo_ring": dict(types=["*"], unlock={"rule": "bond", "level": 3}, glow=True, cubes=[
        ("ring_front", (-4, -3, -4), (8, 1, 1), (0, 0), "TEAL_BRIGHT", {"glow": True}),
        ("ring_back", (-4, -3, 3), (8, 1, 1), (0, 0), "TEAL_BRIGHT", {"glow": True}),
        ("ring_left", (-4, -3, -3), (1, 1, 6), (0, 2), "TEAL_BRIGHT", {"glow": True}),
        ("ring_right", (3, -3, -3), (1, 1, 6), (0, 2), "TEAL_BRIGHT", {"glow": True}),
    ]),
    "sun_visor": dict(types=["personal_drone", "mining_bot", "carry_bot", "repair_bot", "combat_bot", "scrapling"],
                      unlock={"rule": "item", "item": f"{MOD}:hat_sun_visor"}, glow=False, cubes=[
        ("brim", (-5, -1, -7), (10, 1, 6), (0, 0), "GRAPHITE", {}),
        ("glass", (-4, 0, -7), (8, 2, 1), (0, 7), "ION_BLUE", {"glass": True}),
    ]),
    "party_cone": dict(types=["*"], unlock={"rule": "season", "season": "festive"}, glow=False, cubes=[
        ("cone_base", (-3, -3, -3), (6, 3, 6), (0, 0), "BOND_PINK", {"stripes": True}),
        ("cone_mid", (-2, -6, -2), (4, 3, 4), (0, 9), "BOND_PINK", {"stripes": True}),
        ("pom", (-1, -8, -1), (2, 2, 2), (16, 9), "WARN_AMBER", {}),
    ]),
    "scrap_bucket": dict(types=["scrapling", "mining_bot"], unlock={"rule": "item", "item": f"{MOD}:scrap_shell"}, glow=False, cubes=[
        ("bucket", (-3.5, -6, -3.5), (7, 6, 7), (0, 0), "rust", {}),
    ]),
    "antenna_bow": dict(types=["personal_drone", "scout_drone", "scrapling"], unlock={"rule": "bond", "level": 2}, glow=False, cubes=[
        ("antenna", (-0.5, -3, -0.5), (1, 3, 1), (0, 0), "STEEL", {}),
        ("bow", (-2, -5, -0.5), (4, 2, 1), (4, 0), "BOND_PINK", {"knot": True}),
    ]),
    "headlamp": dict(types=["mining_bot", "carry_bot", "repair_bot", "combat_bot"], unlock={"rule": "item", "item": f"{MOD}:hat_headlamp"},
                     glow=True, cubes=[
        ("housing", (-2, -3, -4), (4, 3, 3), (0, 0), "STEEL", {"lens": True}),
    ]),
}
HAT_TEX = (64, 32)


def paint_hat(name):
    spec = HATS[name]
    base, glow = Sheet(*HAT_TEX), Sheet(*HAT_TEX)
    for cname, origin, size, uv, colour, flags in spec["cubes"]:
        w, h, d = size
        u, v = uv
        fw, fh = footprint(w, h, d)
        assert u + fw <= HAT_TEX[0] and v + fh <= HAT_TEX[1], f"hat {name}.{cname} island leaves the sheet"
        if colour == "rust":
            for fname, (x, y, fw_, fh_) in faces(u, v, w, h, d).items():
                rust_face(base, x, y, fw_, fh_, seed=7, weld=fname == "front")
                if fname in ("front", "back", "left", "right"):
                    base.hline(x, y + fh_ - 1, fw_, rgb("G_LIGHT"))   # the rim
            x, y, fw_, fh_ = faces(u, v, w, h, d)["left"]
            base.rect(x + 2, y + 2, 2, 2, rgb("RUST_DK"))              # the dent
            continue
        col = rgb(colour)
        edge = rgb(flags.get("edge", "G_DARK"))
        hi = mix(col, (255, 255, 255, 255), 0.35)
        lo = mix(col, (0, 0, 0, 255), 0.3)
        for fname, (x, y, fw_, fh_) in faces(u, v, w, h, d).items():
            if fw_ <= 0 or fh_ <= 0:
                continue
            flat_face(base, x, y, fw_, fh_, col, edge=edge if min(fw_, fh_) >= 3 else None,
                      hi=hi if fname != "bottom" else None, lo=lo)
            if flags.get("knit") and fname in ("front", "back", "left", "right"):
                for i in range(1, fw_ - 1, 2):
                    base.vline(x + i, y + 1, fh_ - 2, lo)
                base.hline(x, y + fh_ - 1, fw_, rgb("TEAL_DIM"))     # the rolled rim
            if flags.get("stripes") and fname in ("front", "back", "left", "right"):
                for j in range(fh_):
                    for i in range(fw_):
                        if ((i + j) // 2) % 2 == 1:
                            base.px(x + i, y + j, rgb("EYE_WHITE"))
            if flags.get("glass") and fname == "front":
                base.rect(x + 1, y, fw_ - 2, fh_, rgb("GLASS"))
                base.hline(x + 1, y, fw_ - 2, rgb("ION_BLUE"))
            if flags.get("knot") and fname in ("front", "back"):
                base.vline(x + fw_ // 2 - 1, y, fh_, lo)
                base.vline(x + fw_ // 2, y, fh_, lo)
            if flags.get("lens") and fname == "front":
                base.rect(x + 1, y, 2, 2, rgb("GLASS"))
                glow.rect(x + 1, y, 2, 2, rgb("WARN_AMBER"))
            if flags.get("glow"):
                glow.rect(x, y, fw_, fh_, col)
    return base, (glow if spec["glow"] else None)


def hats_markdown():
    out = ["| hat | types | unlock | cube | origin | size w×h×d | UV | colour |", "|---|---|---|---|---|---|---|---|"]
    for name, spec in HATS.items():
        for i, (cname, origin, size, uv, colour, flags) in enumerate(spec["cubes"]):
            head = f"`{name}` | {', '.join(spec['types'])} | `{json.dumps(spec['unlock'])}`" if i == 0 else " | | "
            out.append(f"| {head} | `{cname}` | {tuple(origin)} | {'×'.join(str(v) for v in size)} | {tuple(uv)} | {colour} |")
    return "\n".join(out)


# --------------------------------------------------------------------------------------------- 5x5 glyphs
TYPE_GLYPH = {
    "personal_drone": [".###.", "#####", "#.#.#", "#####", ".###."],
    "scout_drone": ["..#..", "..#..", ".###.", "#####", "#...#"],
    "mining_bot": ["#####", "#.#.#", "#####", "##.##", "##.##"],
    "carry_bot": ["#...#", "#####", "#...#", "#...#", "#####"],
    "repair_bot": ["...##", "..###", ".##..", "##...", "#...."],
    "combat_bot": ["#####", "#...#", "#...#", ".#.#.", "..#.."],
    "scrapling": [".#.#.", "#####", "#####", ".###.", "..#.."],
}
CHIP_GLYPH = {
    "stat": ["..#..", ".###.", "#.#.#", "..#..", "..#.."],
    "capability": ["###..", "#.#..", "###..", "..#..", "..##."],
    "cosmetic": ["..#..", ".###.", "#####", "#####", ".###."],
}
CHIP_BAND = {"stat": "ION_BLUE", "capability": "WARN_AMBER", "cosmetic": "BOND_PINK"}
CHIPS = {  # kind -> (category, tiers)
    "speed": ("stat", 3), "efficiency": ("stat", 3), "range": ("stat", 3), "capacity": ("stat", 3), "armour": ("stat", 3),
    "vitality": ("stat", 3), "guard_protocol": ("capability", 1), "ore_filter": ("capability", 1),
    "auto_deposit": ("capability", 1), "cargo_rack": ("capability", 1), "lamp": ("capability", 1), "salvage": ("capability", 1),
    "silent_running": ("capability", 1), "rad_shielding": ("capability", 1), "chatterbox": ("cosmetic", 1), "hush": ("cosmetic", 1),
    "emote_pack": ("cosmetic", 1), "sparkle_trail": ("cosmetic", 1),
}
# a per-kind 3x3 mark inside the band so kinds within a category differ (shapes only, never a letter)
KIND_MARK = {
    "speed": [".#.", "..#", ".#."], "efficiency": ["#.#", ".#.", "#.#"], "range": ["#.#", "...", "#.#"],
    "capacity": ["###", "#.#", "###"], "armour": ["###", ".#.", ".#."], "vitality": [".#.", "###", ".#."],
    "guard_protocol": ["#.#", "###", ".#."], "ore_filter": [".#.", "#.#", ".#."], "auto_deposit": [".#.", ".#.", "###"],
    "cargo_rack": ["###", "###", "###"], "lamp": [".#.", "###", "..."], "salvage": ["#..", ".#.", "..#"],
    "silent_running": ["...", "###", "..."], "rad_shielding": ["#.#", ".#.", "#.#"], "chatterbox": ["###", "#..", "#.."],
    "hush": ["...", ".#.", "..."], "emote_pack": ["#.#", "...", ".#."], "sparkle_trail": ["..#", ".#.", "#.."],
}


# --------------------------------------------------------------------------------------------- blocks
def tex_pad_top():
    s = Sheet(16, 16)
    flat_face(s, 0, 0, 16, 16, rgb("STEEL"), edge=rgb("STEEL_DK"), hi=rgb("G_LIGHT"))
    s.rect(2, 2, 12, 12, rgb("GRAPHITE"))
    s.outline(2, 2, 12, 12, rgb("G_DARK"))
    s.hline(3, 8, 10, rgb("TRIM"))
    s.vline(8, 3, 10, rgb("TRIM"))
    for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):
        s.rect(x, y, 2, 2, rgb("NERO_ALLOY"))
    return s


def tex_pad_side():
    s = Sheet(16, 16)
    s.rect(0, 0, 16, 16, rgb("STEEL_DK"))
    s.hline(0, 14, 16, rgb("STEEL"))
    s.hline(0, 15, 16, rgb("G_DARK"))
    for x in (0, 15):
        s.px(x, 14, rgb("NERO_ALLOY"))
    return s


def tex_pad_ring(col, radius=5, alpha=255):
    s = Sheet(16, 16)
    c = rgb(col, alpha)
    x0, y0 = 8 - radius, 8 - radius
    s.outline(x0, y0, radius * 2, radius * 2, c)
    for cx, cy in ((x0, y0), (x0 + 2 * radius - 1, y0), (x0, y0 + 2 * radius - 1), (x0 + 2 * radius - 1, y0 + 2 * radius - 1)):
        s.px(cx, cy, CLEAR)
    return s


def tex_pad_ring_active():
    """8 frames, 16x128: the ring breathes between radius 4 and 6 and brightens on the way out."""
    strip = Sheet(16, 128)
    seq = [(4, 150), (5, 200), (6, 255), (6, 255), (6, 200), (5, 170), (4, 150), (4, 130)]
    for k, (r, a) in enumerate(seq):
        f = tex_pad_ring("PLASMA" if k in (2, 3) else "COMPANION_TEAL", r, a)
        if k in (2, 3):
            for cx, cy in ((8 - r, 8), (7 + r, 8), (8, 8 - r), (8, 7 + r)):
                f.px(cx, cy, rgb("TEAL_BRIGHT"))
        strip.blit(f, 0, k * 16)
    return strip


def tex_bay_front():
    s = Sheet(16, 16)
    flat_face(s, 0, 0, 16, 16, rgb("STEEL"), edge=rgb("STEEL_DK"), hi=rgb("G_LIGHT"))
    for px_, py_ in ((1, 1), (9, 1), (1, 9), (9, 9)):
        s.rect(px_, py_, 6, 6, rgb("TRIM"))
        s.rect(px_ + 1, py_ + 1, 4, 4, rgb("GLASS"))
        s.px(px_ + 4, py_ + 1, rgb("G_DARK"))                 # LED socket (lit by the LED strips / BER)
    s.hline(1, 8, 14, rgb("STEEL_DK"))
    s.vline(8, 1, 14, rgb("STEEL_DK"))
    return s


def tex_bay_leds(n):
    s = Sheet(16, 16)
    for i, (px_, py_) in enumerate(((1, 1), (9, 1), (1, 9), (9, 9))):
        if i < n:
            s.px(px_ + 4, py_ + 1, rgb("COMPANION_TEAL"))
    return s


def tex_bay_top():
    s = Sheet(16, 16)
    s.rect(0, 0, 16, 16, rgb("NERO_ALLOY"))
    s.outline(0, 0, 16, 16, rgb("TEAL_DIM"))
    s.rect(4, 4, 8, 8, rgb("GRAPHITE"))
    s.outline(4, 4, 8, 8, rgb("G_DARK"))
    for j in range(5, 11, 2):
        s.hline(5, j, 6, rgb("GRAPHITE_4"))
    return s


def tex_bay_side():
    s = Sheet(16, 16)
    s.rect(0, 0, 16, 16, rgb("STEEL"))
    rnd = random.Random(42)
    for j in range(16):
        r = rnd.random()
        if r < 0.2:
            s.hline(0, j, 16, rgb("G_LIGHT"))
        elif r > 0.82:
            s.hline(0, j, 16, rgb("STEEL_DK"))
    s.outline(0, 0, 16, 16, rgb("STEEL_DK"))
    s.hline(0, 12, 16, rgb("TRIM"))
    return s


def tex_bay_back():
    s = tex_bay_side()
    s.rect(5, 5, 6, 6, rgb("TRIM"))
    s.rect(6, 6, 4, 4, rgb("GLASS"))
    s.rect(7, 7, 2, 2, rgb("NERO_ALLOY"))
    return s


def tex_crank_base():
    s = Sheet(16, 16)
    flat_face(s, 0, 0, 16, 16, rgb("STEEL"), edge=rgb("STEEL_DK"), hi=rgb("G_LIGHT"))
    s.rect(0, 7, 16, 2, rgb("NERO_ALLOY"))        # belt line
    s.hline(0, 15, 16, rgb("G_DARK"))
    s.hline(0, 14, 16, rgb("STEEL_DK"))
    return s


def tex_crank_top():
    s = Sheet(16, 16)
    flat_face(s, 0, 0, 16, 16, rgb("STEEL"), edge=rgb("STEEL_DK"), hi=rgb("G_LIGHT"))
    s.rect(5, 5, 6, 6, rgb("GRAPHITE"))
    s.outline(5, 5, 6, 6, rgb("G_DARK"))
    s.rect(7, 7, 2, 2, rgb("TEAL_DIM"))
    return s


def tex_crank_contact_glow():
    s = Sheet(16, 16)
    s.outline(5, 5, 6, 6, rgb("COMPANION_TEAL"))
    s.rect(7, 7, 2, 2, rgb("TEAL_BRIGHT"))
    return s


def tex_crank_housing():
    s = tex_crank_base()
    s.rect(4, 4, 8, 8, rgb("GRAPHITE"))
    s.outline(4, 4, 8, 8, rgb("G_DARK"))
    for i in range(5, 11, 2):           # gear teeth
        s.px(i, 4, rgb("G_LIGHT"))
        s.px(i, 11, rgb("G_LIGHT"))
        s.px(4, i, rgb("G_LIGHT"))
        s.px(11, i, rgb("G_LIGHT"))
    s.rect(7, 7, 2, 2, rgb("STEEL_DK"))
    return s


def tex_crank_handle_strip():
    """16x64, four frames: the handle arm at 0/90/180/270 degrees around the hub at (7..8, 7..8), knob at its tip."""
    strip = Sheet(16, 64)
    for k, (dx, dy) in enumerate(((0, -1), (1, 0), (0, 1), (-1, 0))):
        f = Sheet(16, 16)
        for i in range(1, 5):
            f.px(7 + dx * i, 7 + dy * i, rgb("STEEL"))
            f.px(8 + dx * i, 8 + dy * i, rgb("STEEL"))
            f.px(7 + dx * i + (1 if dy else 0), 7 + dy * i + (1 if dx else 0), rgb("STEEL"))
        kx, ky = 7 + dx * 5, 7 + dy * 5
        f.rect(kx - 1, ky - 1, 4, 4, rgb("G_DARK"))
        f.rect(kx, ky, 2, 2, rgb("RUBBER"))
        f.px(kx, ky, rgb("GRAPHITE_4"))
        f.rect(7, 7, 2, 2, rgb("STEEL_DK"))
        strip.blit(f, 0, k * 16)
    return strip


def tex_crank_knob():
    s = Sheet(16, 16)
    s.rect(0, 0, 2, 2, rgb("RUBBER"))
    s.px(0, 0, rgb("GRAPHITE_4"))
    return s


def _face(uv, tex, cull=None):
    f = {"uv": list(uv), "texture": tex}
    if cull:
        f["cullface"] = cull
    return f


def model_block_bay():
    t = f"{MOD}:block/companion_bay"
    return {"parent": "minecraft:block/cube", "textures": {
        "particle": t + "_side", "down": t + "_side", "up": t + "_top", "north": t + "_front", "south": t + "_back",
        "east": t + "_side", "west": t + "_side"}}


def model_block_pad(ring_tex):
    t = f"{MOD}:block/charging_pad"
    el = [
        {"from": [0, 0, 0], "to": [16, 2, 16], "faces": {
            "down": _face((0, 0, 16, 16), "#side", "down"), "up": _face((0, 0, 16, 16), "#top"),
            "north": _face((0, 14, 16, 16), "#side"), "south": _face((0, 14, 16, 16), "#side"),
            "west": _face((0, 14, 16, 16), "#side"), "east": _face((0, 14, 16, 16), "#side")}},
        {"from": [0, 2, 0], "to": [16, 2.01, 16], "shade": False,
         "faces": {"up": _face((0, 0, 16, 16), "#ring")}},
    ]
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"particle": t + "_side", "top": t + "_top", "side": t + "_side", "ring": ring_tex}, "elements": el}


def model_block_crank(cranking):
    t = f"{MOD}:block/hand_crank"
    hub = {"from": [7, 6, 1], "to": [9, 8, 2], "faces": {
        "north": _face((7, 7, 9, 9), "#housing"), "up": _face((7, 7, 9, 9), "#housing"),
        "down": _face((7, 7, 9, 9), "#housing"), "west": _face((7, 7, 9, 9), "#housing"),
        "east": _face((7, 7, 9, 9), "#housing")}}
    el = [
        {"from": [0, 0, 0], "to": [16, 2, 16], "faces": {
            "down": _face((0, 0, 16, 16), "#base", "down"), "up": _face((0, 0, 16, 16), "#base"),
            "north": _face((0, 14, 16, 16), "#base"), "south": _face((0, 14, 16, 16), "#base"),
            "west": _face((0, 14, 16, 16), "#base"), "east": _face((0, 14, 16, 16), "#base")}},
        {"from": [2, 2, 2], "to": [14, 12, 14], "faces": {
            "up": _face((2, 2, 14, 14), "#top"), "north": _face((2, 4, 14, 14), "#housing"),
            "south": _face((2, 4, 14, 14), "#base"), "west": _face((2, 4, 14, 14), "#base"),
            "east": _face((2, 4, 14, 14), "#base")}},
        {"from": [5, 12, 5], "to": [11, 12.01, 11], "shade": False,
         "faces": {"up": _face((5, 5, 11, 11), "#contact")}},
        hub,
    ]
    if cranking:
        el.append({"from": [2, 1, 1], "to": [14, 13, 1.5], "shade": False, "faces": {
            "north": _face((2, 3, 14, 15), "#handle"), "south": _face((14, 3, 2, 15), "#handle")}})
    else:
        el.append({"from": [7.5, 7, 1], "to": [8.5, 12, 2], "faces": {   # the arm, pointing up at rest
            "north": _face((7, 2, 8, 7), "#housing"), "south": _face((7, 2, 8, 7), "#housing"),
            "west": _face((7, 2, 8, 7), "#housing"), "east": _face((7, 2, 8, 7), "#housing"),
            "up": _face((7, 2, 8, 3), "#housing")}})
        el.append({"from": [7, 11, 0], "to": [9, 13, 2], "faces": {   # the knob
            d: _face((0, 0, 2, 2), "#knob") for d in ("north", "south", "west", "east", "up", "down")}})
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": {
        "particle": t + "_base", "base": t + "_base", "top": t + "_top", "housing": t + "_housing",
        "contact": t + "_contact_glow", "handle": t + "_handle", "knob": t + "_knob"}, "elements": el}


# --------------------------------------------------------------------------------------------- items
def item_capsule(type_=None):
    s = Sheet(16, 16)
    x, y = 4, 1
    s.rect(x, y, 7, 13, rgb("SHELL"))
    s.hline(x + 1, y, 5, rgb("SHELL_LT"))
    s.vline(x, y + 1, 11, rgb("SHELL_LT"))
    s.vline(x + 6, y + 1, 11, rgb("SHELL_DK"))
    s.hline(x + 1, y + 12, 5, rgb("SHELL_DK"))
    s.outline(x - 1, y - 1, 9, 15, rgb("G_DARK"))
    for cx, cy in ((x - 1, y - 1), (x + 7, y - 1), (x - 1, y + 13), (x + 7, y + 13)):
        s.px(cx, cy, CLEAR)
    for cx, cy in ((x, y), (x + 6, y), (x, y + 12), (x + 6, y + 12)):
        s.px(cx, cy, rgb("G_DARK"))
    s.hline(x, y + 7, 7, rgb("STEEL_DK"))                       # seam
    s.rect(x + 1, y + 1, 5, 5, rgb("GLASS"))                    # window rows 2-6
    s.rect(x + 1, y + 9, 5, 2, rgb("GRAPHITE"))                 # grip
    if type_:
        col = rgb(ROLE[type_])
        s.glyph(x + 1, y + 1, TYPE_GLYPH[type_], {"#": col})
        s.vline(x + 6, y + 1, 11, col)                          # band at the right edge
    else:
        s.px(x + 2, y + 2, rgb("GRAPHITE_4"))
    return s


def item_remote():
    s = Sheet(16, 16)
    x, y = 5, 2
    s.rect(x, y, 6, 12, rgb("GRAPHITE"))
    s.outline(x, y, 6, 12, rgb("SHELL_LT"))
    s.outline(x - 1, y - 1, 8, 14, rgb("G_DARK"))
    for cx, cy in ((x - 1, y - 1), (x + 6, y - 1), (x - 1, y + 12), (x + 6, y + 12)):
        s.px(cx, cy, CLEAR)
    s.rect(x + 1, y + 2, 4, 3, rgb("COMPANION_TEAL"))
    s.px(x + 1, y + 2, rgb("TEAL_BRIGHT"))
    s.glyph(x + 1, y + 6, [".#..", "#.#.", ".#.."], {"#": rgb("G_LIGHT")})
    s.rect(x + 2, y + 10, 2, 1, rgb("BOND_PINK"))
    s.rect(x + 2, y - 2, 2, 1, rgb("STEEL"))                   # fob ring
    return s


def item_fetch_ball():
    s = Sheet(16, 16)
    art = ["...kkkk...", ".kkLLLLkk.", ".kLLssssk.", "kLLssssssk", "kLsssssssk", "kttttttttk", "kDsssssssk",
           ".kDsssspk.", ".kkDDDDkk.", "...kkkk..."]
    s.glyph(3, 3, art, {"k": rgb("G_DARK"), "L": rgb("SHELL_LT"), "s": rgb("SHELL"), "D": rgb("SHELL_DK"),
                        "t": rgb("COMPANION_TEAL"), "p": rgb("BOND_PINK")})
    return s


def item_scrap_egg(stage):
    s = Sheet(16, 16)
    art = ["...kkk...", "..kRRsk..", ".kRRRssk.", ".kRrRsLk.", "kRRRsssLk", "kRrRRssLk", "kRRRRsssk", "kDRRRRDsk",
           ".kDRRDDk.", "..kDDDk..", "...kkk..."]
    s.glyph(4, 2, art, {"k": rgb("G_DARK"), "R": rgb("RUST"), "r": rgb("G_LIGHT"), "s": rgb("SHELL"), "L": rgb("SHELL_LT"),
                        "D": rgb("RUST_DK")})
    if stage >= 1:
        crack = rgb("SCRAP_ORANGE") if stage == 1 else rgb("TEAL_BRIGHT")
        for (cx, cy) in ((8, 5), (9, 6), (8, 7), (9, 8), (10, 9)):
            s.px(cx, cy, crack)
        if stage >= 2:
            for (cx, cy) in ((7, 6), (10, 7), (7, 9)):
                s.px(cx, cy, rgb("COMPANION_TEAL"))
    return s


def item_chip(item=None):
    """The family chip slab (10x14): band + category glyph + kind mark + tier pips; blank chip = grey band."""
    s = Sheet(16, 16)
    x, y = 3, 1
    s.rect(x, y, 10, 14, rgb("CHIP_BODY"))
    s.outline(x, y, 10, 14, rgb("CHIP_EDGE"))
    s.rect(x, y, 2, 2, CLEAR)                                    # notch top-left
    s.px(x + 2, y, rgb("CHIP_EDGE")); s.px(x, y + 2, rgb("CHIP_EDGE")); s.px(x + 1, y + 1, rgb("CHIP_EDGE"))
    for i in range(4):
        s.px(x + 2 + i * 2, y + 13, rgb("WARN_AMBER"))          # contact pins
    if item is None:
        s.rect(x, y + 4, 10, 3, rgb("CHIP_GREY"))
        return s
    kind, tier = item, 1
    if "_" in kind and kind.rsplit("_", 1)[-1].isdigit():
        kind, tier = kind.rsplit("_", 1)
        tier = int(tier)
    cat, tiers = CHIPS[kind]
    band_col = rgb(CHIP_BAND[cat])
    s.rect(x, y + 4, 10, 3, band_col)
    s.glyph(x + 1, y + 4, KIND_MARK[kind], {"#": mix(band_col, (0, 0, 0, 255), 0.45)})
    s.glyph(x + 3, y + 8, CHIP_GLYPH[cat], {"#": band_col})
    for p in range(tier):
        s.px(x + 8 - p * 2, y + 7, band_col)                     # pips, right-aligned under the band
    if tier == 3:
        s.px(x + 2, y, rgb("EYE_WHITE"))
    return s


def item_spawn_egg(type_):
    s = Sheet(16, 16)
    art = [".....kkkkk......", "....kLLLLsk.....", "...kLLLssssk....", "..kLLsssssssk...", "..kLsssssssssk..",
           ".kLssssssssssDk.", ".kssssssssssDDk.", ".kssssssssssDDk.", ".kssssssssssDDk.", ".kDsssssssssDDk.",
           "..kDsssssssDDk..", "..kDDsssssDDDk..", "...kDDDDDDDDk...", "....kkkkkkkk...."]
    s.glyph(0, 1, art, {"k": rgb("G_DARK"), "L": rgb("SHELL_LT"), "s": rgb("SHELL"), "D": rgb("SHELL_DK")})
    col = rgb(ROLE[type_])
    for (cx, cy) in ((5, 5), (6, 5), (5, 6), (9, 4), (10, 7), (11, 7), (10, 8), (4, 10), (7, 11), (8, 11), (12, 10)):
        s.px(cx, cy, col)
    return s


def item_hazard_decal():
    s = Sheet(16, 16)
    s.rect(3, 4, 10, 8, rgb("HAZ_K"))
    hazard_stripes(s, 4, 5, 8, 6)
    s.outline(3, 4, 10, 8, rgb("G_DARK"))
    return s


def item_hat_icon(hat):
    """16x16 inventory icon for a hat: a front view of its cubes stacked on a base line at y 13, one dark outline."""
    s = Sheet(16, 16)
    for cname, origin, size, uv, colour, flags in HATS[hat]["cubes"]:
        w, h, d = size
        ox, oy, oz = origin
        col = rgb("RUST") if colour == "rust" else rgb(colour)
        x0, y0 = int(round(8 + ox)), int(round(13 + oy))
        s.rect(x0, y0, int(w), int(h), col)
        if h >= 2 and w >= 2:
            s.hline(x0, y0, int(w), mix(col, (255, 255, 255, 255), 0.35))
    mask = s.a[:, :, 3] > 0
    out = Sheet(16, 16)
    out.a[:] = s.a
    for yy in range(16):
        for xx in range(16):
            if not mask[yy, xx] and any(0 <= yy + dy < 16 and 0 <= xx + dx < 16 and mask[yy + dy, xx + dx]
                                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.px(xx, yy, rgb("G_DARK"))
    return out


# --------------------------------------------------------------------------------------------- emotes
EMOTE_COLS = {"w": "EYE_WHITE", "k": "G_DARK", "p": "BOND_PINK", "a": "WARN_AMBER", "r": "LOW_RED", "b": "ION_BLUE",
              "g": "OK_GREEN", "t": "TEAL_BRIGHT", "v": "VIOLET", "s": "SHELL", "d": "SHELL_DK", "o": "SCRAP_ORANGE",
              "y": "HAZ_Y", "R": "RUST"}
EMOTE_ORDER = ["heart", "zz", "question", "exclaim", "ore", "danger", "bolt", "wrench", "note", "sweat", "star", "puff",
               "spiral", "eye", "sparkle", "box", "pick", "shield", "target", "leaf", "dots", "tick", "cross", "home",
               "ball", "egg", "hat", "sun", "moon", "blush", "hourglass", "wave"]
EMOTES = {
    "heart": ["..kkk...kkk..", ".kpppk.kpppk.", "kpwppppkppppk", "kpppppppppppk", "kpppppppppppk", ".kpppppppppk.",
              "..kpppppppk..", "...kpppppk...", "....kpppk....", ".....kpk.....", "......k......"],
    "zz": [".........kkkkk.", ".........kwwwwk", ".........kkkwwk", "...........kwk.", "..kkkk....kwk..", "..kwwwk..kwwwwk",
           "..kkkwk..kkkkk.", "....kwk........", "...kwk.........", "..kwwwk........", "..kkkkk........"],
    "question": ["...kkkkk...", "..kwwwwwk..", ".kwwkkkwwk.", ".kwwk.kwwk.", "..kk..kwwk.", ".....kwwk..", "....kwwk...",
                 "....kwwk...", "....kkkk...", "....kwwk...", "....kkkk..."],
    "exclaim": ["..kkkk..", ".kaaaak.", ".kaaaak.", ".kaaaak.", ".kaaaak.", "..kaak..", "..kaak..", "..kkkk..", "..kaak..",
                "..kkkk.."],
    "ore": ["....kkk....", "...kbbbk...", "..kbwbbbk..", ".kbbbbbbbk.", "kbbbbbbbbbk", ".kbbbbbbbk.", "..kbbbbbk..",
            "...kbbbk...", "....kbk....", ".....k....."],
    "danger": [".....kk.....", "....krrk....", "....krrk....", "...krkkrk...", "...krkkrk...", "..krrkkrrk..", "..krrkkrrk..",
               ".krrrrrrrrk.", ".krrrkkrrrk.", "krrrrrrrrrrk", "kkkkkkkkkkkk"],
    "bolt": [".....kkkk.", "....kaaak.", "...kaaak..", "..kaaak...", ".kaaaaakk.", ".kkkaaaak.", "...kaaak..", "..kaaak...",
             ".kaak.....", "kak.......", "k........."],
    "wrench": ["........kkkk.", ".......kwwwwk", "......kwwkkwk", "......kwwwwk.", "....kkwwwwk..", "...kwwwwkk...",
               "..kwwwwk.....", ".kwwwwk......", "kwwwwk.......", "kwwwk........", "kkkk........."],
    "note": ["......kkkk", "......kwwk", "......kwkk", "......kwk.", "......kwk.", "......kwk.", "..kkkkkwk.", ".kwwwwwwk.",
             "kwwwwwwwk.", "kwwwwwwk..", ".kkkkkk..."],
    "sweat": ["....kk....", "...kbbk...", "...kbbk...", "..kbbbbk..", "..kbwbbk..", ".kbbwbbbk.", ".kbbbbbbk.", ".kbbbbbbk.",
              "..kbbbbk..", "...kkkk..."],
    "star": [".....kk.....", ".....kak....", "....kaaak...", "kkkkkaaakkkk", "kaaaaaaaaaak", ".kaaaaaaaak.", "..kaaaaaak..",
             "..kaaaaaak..", ".kaaakkaaak.", ".kakk..kkak.", "kk......kk.."],
    "puff": ["....kkk..kkk.", "...kdddkkdddk", "..kdsddddsddk", ".kdddddddddk.", "kddddkkdddk..", "kdddk..kkk...",
             ".kddk........", "..kk.........", ".............", "kkk..........", "kdk.........."],
    "spiral": ["...kkkkkk...", "..kwwwwwwk..", ".kwkkkkkkwk.", "kwk.kkkk.kwk", "kwk.kwwk.kwk", "kwk.kwwwkkwk", "kwk.kwwwwwwk",
               "kwk.kkkkkkk.", ".kwk........", "..kwwwwwwk..", "...kkkkkkk.."],
    "eye": ["....kkkkk....", "..kkwwwwwkk..", ".kwwwkkkwwwk.", "kwwwkdddkwwwk", "kwwkdwdddkwwk", "kwwkdddddkwwk", "kwwwkdddkwwwk",
            ".kwwwkkkwwwk.", "..kkwwwwwkk..", "....kkkkk...."],
    "sparkle": [".....kk.....", ".....ktk....", ".....ktk....", "....kttk....", "kkkkttttkkkk", "kttttwttttk.", "kkkkttttkkkk",
                "....kttk....", ".....ktk....", ".....ktk....", ".....kk....."],
    "box": ["kkkkkkkkkkkk", "kssssssssssk", "kskkkkkkkksk", "kskddddddksk", "kskkkkkkkksk", "kssssssssssk", "kssskkkksssk",
            "kssskddksssk", "kssskkkksssk", "kssssssssssk", "kkkkkkkkkkkk"],
    "pick": ["....kkkkk...", "..kkwwwwwkk.", ".kwwkkkkkwwk", "kwwk.kk..kwk", "kwk.kwwk..kk", "kk.kwwk.....", "..kwwk......",
             ".kwwk.......", "kwwk........", "kwk.........", "kk.........."],
    "shield": [".kkkkkkkkkk.", "kwwwwwwwwwwk", "kwwkkkkkkwwk", "kwwkddddkwwk", "kwwkddddkwwk", "kwwkddddkwwk", ".kwkddddkwk.",
               ".kwwkddkwwk.", "..kwwkkwwk..", "...kwwwwk...", "....kkkk...."],
    "target": ["...kkkkkk...", "..krrrrrrk..", ".krkkkkkkrk.", "krk......krk", "krk..kk..krk", "krk.krrk.krk", "krk.krrk.krk",
               "krk..kk..krk", "krk......krk", ".krkkkkkkrk.", "..krrrrrrk..", "...kkkkkk..."],
    "leaf": ["........kkk.", "......kkgggk", "....kkggggkk", "...kgggggk..", "..kggggggk..", ".kgggkggggk.", ".kggkggggk..",
             "kggkgggggk..", "kgkggggkk...", "kgkkkkk.....", "kk.........."],
    "dots": ["kkk..kkk..kkk", "kwk..kwk..kwk", "kkk..kkk..kkk"],
    "tick": [".........kk.", "........kggk", ".......kggk.", "......kggk..", "kk...kggk...", "kggk.kggk...", ".kggkggk....",
             "..kgggk.....", "...kgk......", "....k......."],
    "cross": ["kk.......kk", "krrk...krrk", ".krrk.krrk.", "..krrkrrk..", "...krrrk...", "...krrrk...", "..krrkrrk..",
              ".krrk.krrk.", "krrk...krrk", "kk.......kk"],
    "home": [".....kk.....", "....kwwk....", "...kwwwwk...", "..kwwwwwwk..", ".kwwwwwwwwk.", "kwwwwwwwwwwk", "kkkwwwwwwkkk",
             "..kwwkkwwk..", "..kwwkkwwk..", "..kwwkkwwk..", "..kkkkkkkk.."],
    "ball": ["...kkkk...", ".kksssskk.", ".kssssssk.", "kssssssssk", "kttttttttk", "kdssssspdk", ".kdssssdk.", ".kkddddkk.",
             "...kkkk..."],
    "egg": ["....kkk....", "...kRRsk...", "..kRRRssk..", ".kRRRRsssk.", ".kRRRRsssk.", "kRRRRRssssk", "kRRRRRRsssk",
            "kdRRRRRRRsk", ".kdRRRRRRk.", "..kddRRdk..", "...kkkkk..."],
    "hat": ["....kkkkkk....", "...kssssssk...", "...kssssssk...", "...kddddddk...", "...kssssssk...", "kkkkssssssskkk",
            "kssssssssssssk", "kkkkkkkkkkkkkk"],
    "sun": [".....kk......", "kk...kak...kk", "kak.kkakk.kak", ".kkkaaaaakkk.", "..kaaaaaaak..", "kkkaaawaaakkk", "kaaaaaaaaaaak",
            "kkkaaaaaaakkk", "..kaaaaaaak..", ".kkkaaaaakkk.", "kak.kkakk.kak", "kk...kak...kk", ".....kk......"],
    "moon": ["....kkkkk..", "..kkwwwwwkk", ".kwwwwkkkkk", "kwwwwk.....", "kwwwk......", "kwwwk......", "kwwwk......",
             "kwwwwk.....", ".kwwwwkkkkk", "..kkwwwwwkk", "....kkkkk.."],
    "blush": ["kkk.....kkk", "kppk...kppk", "kpppk.kpppk", "kppk...kppk", "kkk.....kkk"],
    "hourglass": ["kkkkkkkk", "kwwwwwwk", "kwaaaawk", ".kwaawk.", "..kwwk..", "..kwwk..", ".kwwwwk.", "kwwaawwk", "kwaaaawk",
                  "kkkkkkkk"],
    "wave": ["......kk..", ".....kssk.", "....kssk..", "...kssk...", "kk.kssk...", "kskkssk...", "ksssssk...", ".kssssk...",
             "..kssssk..", "...kkkkk.."],
}


def tex_emotes():
    sheet = Sheet(128, 64)
    cols = {k: rgb(v) for k, v in EMOTE_COLS.items()}
    for idx, name in enumerate(EMOTE_ORDER):
        rows = EMOTES[name]
        h, w = len(rows), max(len(r) for r in rows)
        assert w <= 16 and h <= 16, name
        sheet.glyph((idx % 8) * 16 + (16 - w) // 2, (idx // 8) * 16 + (16 - h) // 2, rows, cols)
    return sheet


def tex_emote_bubble():
    s = Sheet(20, 18)
    s.rect(0, 0, 20, 15, rgb("PANEL"))
    s.outline(0, 0, 20, 15, rgb("TEAL_BRIGHT"))
    for cx, cy in ((0, 0), (19, 0), (0, 14), (19, 14)):
        s.px(cx, cy, CLEAR)
    s.glyph(8, 15, ["ttt", ".t.", ".t."], {"t": rgb("TEAL_BRIGHT")})
    s.px(9, 15, rgb("PANEL"))
    return s


# --------------------------------------------------------------------------------------------- particles
PARTICLES = {"charge_spark": 4, "sleep_z": 2, "scout_ping": 4, "bond_heart": 1, "scrap_puff": 3}


def particle_frames():
    out = {}
    tb, ct = rgb("TEAL_BRIGHT"), rgb("COMPANION_TEAL")
    for k in range(4):
        s = Sheet(8, 8)
        s.rect(3, 3, 2, 2, tb)
        if k >= 1:
            s.px(2, 3, ct); s.px(5, 4, ct)
        if k >= 2:
            s.px(3, 2, ct); s.px(4, 5, ct)
        if k == 3:
            s.px(1, 3, ct); s.px(6, 4, ct)
        out[f"charge_spark_{k}"] = s
    for k in range(2):
        s = Sheet(8, 8)
        z = ["wwww", "...w", "..w.", ".w..", "wwww"] if k else ["www", "..w", ".w.", "www"]
        s.glyph(2 if k else 3, 2, z, {"w": rgb("EYE_WHITE", 153)})
        out[f"sleep_z_{k}"] = s
    for k, r in enumerate((1, 2, 3, 4)):
        s = Sheet(8, 8)
        s.outline(4 - r, 4 - r, 2 * r, 2 * r, rgb("VIOLET", 255 - k * 40))
        out[f"scout_ping_{k}"] = s
    s = Sheet(8, 8)
    s.glyph(1, 1, [".kk.kk.", "kppkppk", "kpppppk", ".kpppk.", "..kpk..", "...k..."], {"k": rgb("G_DARK"), "p": rgb("BOND_PINK")})
    out["bond_heart_0"] = s
    for k in range(3):
        s = Sheet(8, 8)
        r = 2 + k
        s.rect(4 - r // 2 - 1, 4 - r // 2 - 1, r + 1, r + 1, rgb("SHELL_DK", 230 - k * 50))
        s.px(4, 4, rgb("RUST"))
        out[f"scrap_puff_{k}"] = s
    return out


# --------------------------------------------------------------------------------------------- build
def jsonb(obj):
    return (json.dumps(obj, indent=2) + "\n").encode()


def gen_item_model(tex):
    return jsonb({"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{tex}"}})


def build():
    """Return {relative path under resources/: bytes} for everything this script owns."""
    check_geometry()
    A = f"assets/{MOD}/"
    D = f"data/{MOD}/"
    out = {}

    # entities: default + skins (+ aurora frames)
    for skin, types, st, unlock, eye in SKINS:
        for t in (TYPES if types == "*" else [types]):
            base, glow = PAINTERS[t](st)
            out[f"{A}textures/entity/{t}/{skin}.png"] = base.png_bytes()
            out[f"{A}textures/entity/{t}/{skin}_glow.png"] = glow.png_bytes()
            if skin == "aurora":
                for k in range(AURORA_FRAMES):
                    out[f"{A}textures/entity/{t}/aurora_glow_{k}.png"] = aurora_glow_frame(glow, t, k).png_bytes()
        if types == "*":
            data = {"types": ["*"], "texture": f"{MOD}:entity/{{type}}/{skin}", "glow": f"{MOD}:entity/{{type}}/{skin}_glow",
                    "unlock": unlock}
            if eye:
                data["eye_color"] = PALETTE.get(eye, eye)
            if skin == "aurora":
                data["animated_glow"] = {"frames": AURORA_FRAMES, "frametime": AURORA_FRAMETIME, "interpolate": True,
                                         "frame_texture": f"{MOD}:entity/{{type}}/aurora_glow_{{frame}}"}
            if skin == "default":
                for t in TYPES:
                    out[f"{D}skins/{t}/default.json"] = jsonb({"types": [f"{MOD}:{t}"], "texture": f"{MOD}:entity/{t}/default",
                                                              "glow": f"{MOD}:entity/{t}/default_glow", "unlock": unlock})
            else:
                out[f"{D}skins/{skin}.json"] = jsonb(data)
        else:
            data = {"types": [f"{MOD}:{types}"], "texture": f"{MOD}:entity/{types}/{skin}",
                    "glow": f"{MOD}:entity/{types}/{skin}_glow", "unlock": unlock}
            if eye:
                data["eye_color"] = PALETTE.get(eye, eye)
            if skin == "mossback":
                data["model_override"] = f"{MOD}:scrapling/mossback"
            out[f"{D}skins/{types}/{skin}.json"] = jsonb(data)

    # hats
    for hat, spec in HATS.items():
        base, glow = paint_hat(hat)
        out[f"{A}textures/entity/hat/{hat}.png"] = base.png_bytes()
        data = {"types": ["*"] if spec["types"] == ["*"] else [f"{MOD}:{t}" for t in spec["types"]],
                "texture": f"{MOD}:entity/hat/{hat}", "glow": None, "unlock": spec["unlock"]}
        if glow is not None:
            out[f"{A}textures/entity/hat/{hat}_glow.png"] = glow.png_bytes()
            data["glow"] = f"{MOD}:entity/hat/{hat}_glow"
        out[f"{D}hats/{hat}.json"] = jsonb(data)
        out[f"{A}textures/item/hat_{hat}.png"] = item_hat_icon(hat).png_bytes()

    # blocks
    B = f"{A}textures/block/"
    out[B + "charging_pad_top.png"] = tex_pad_top().png_bytes()
    out[B + "charging_pad_side.png"] = tex_pad_side().png_bytes()
    out[B + "charging_pad_ring_glow.png"] = tex_pad_ring("PLASMA").png_bytes()
    out[B + "charging_pad_ring_dim.png"] = tex_pad_ring("TEAL_DIM").png_bytes()
    out[B + "charging_pad_ring_active.png"] = tex_pad_ring_active().png_bytes()
    out[B + "charging_pad_ring_active.png.mcmeta"] = jsonb({"animation": {"frametime": 3, "interpolate": True}})
    out[B + "companion_bay_front.png"] = tex_bay_front().png_bytes()
    out[B + "companion_bay_top.png"] = tex_bay_top().png_bytes()
    out[B + "companion_bay_side.png"] = tex_bay_side().png_bytes()
    out[B + "companion_bay_back.png"] = tex_bay_back().png_bytes()
    for n in range(5):
        out[B + f"bay_leds_glow_{n}.png"] = tex_bay_leds(n).png_bytes()
    out[B + "hand_crank_base.png"] = tex_crank_base().png_bytes()
    out[B + "hand_crank_top.png"] = tex_crank_top().png_bytes()
    out[B + "hand_crank_housing.png"] = tex_crank_housing().png_bytes()
    out[B + "hand_crank_handle.png"] = tex_crank_handle_strip().png_bytes()
    out[B + "hand_crank_handle.png.mcmeta"] = jsonb({"animation": {"frametime": 2}})
    out[B + "hand_crank_contact_glow.png"] = tex_crank_contact_glow().png_bytes()
    out[B + "hand_crank_knob.png"] = tex_crank_knob().png_bytes()
    M = f"{A}models/block/"
    out[M + "charging_pad.json"] = jsonb(model_block_pad(f"{MOD}:block/charging_pad_ring_dim"))
    out[M + "charging_pad_powered.json"] = jsonb(model_block_pad(f"{MOD}:block/charging_pad_ring_glow"))
    out[M + "charging_pad_charging.json"] = jsonb(model_block_pad(f"{MOD}:block/charging_pad_ring_active"))
    out[M + "companion_bay.json"] = jsonb(model_block_bay())
    out[M + "hand_crank_charger.json"] = jsonb(model_block_crank(False))
    out[M + "hand_crank_charger_cranking.json"] = jsonb(model_block_crank(True))
    out[f"{A}blockstates/charging_pad.json"] = jsonb({"variants": {
        "charging=false,powered=false": {"model": f"{MOD}:block/charging_pad"},
        "charging=false,powered=true": {"model": f"{MOD}:block/charging_pad_powered"},
        "charging=true,powered=false": {"model": f"{MOD}:block/charging_pad_charging"},
        "charging=true,powered=true": {"model": f"{MOD}:block/charging_pad_charging"}}})

    # items
    I = f"{A}textures/item/"
    IM = f"{A}models/item/"
    out[I + "capsule.png"] = item_capsule().png_bytes()
    for t in TYPES:
        out[I + f"capsule_{t}.png"] = item_capsule(t).png_bytes()
        out[IM + f"capsule_{t}.json"] = gen_item_model(f"capsule_{t}")
        out[I + f"{t}_spawn_egg.png"] = item_spawn_egg(t).png_bytes()
    out[I + "remote.png"] = item_remote().png_bytes()
    out[I + "fetch_ball.png"] = item_fetch_ball().png_bytes()
    for k in range(3):
        out[I + f"scrap_egg_{k}.png"] = item_scrap_egg(k).png_bytes()
        out[IM + f"scrap_egg_{k}.json"] = gen_item_model(f"scrap_egg_{k}")
    out[I + "scrap_egg.png"] = item_scrap_egg(0).png_bytes()
    out[I + "blank_chip.png"] = item_chip(None).png_bytes()
    for kind, (cat, tiers) in CHIPS.items():
        items = [f"{kind}_chip_{n}" for n in range(1, tiers + 1)] if tiers > 1 else [f"{kind}_chip"]
        for item in items:
            out[I + f"{item}.png"] = item_chip(item.replace("_chip", "")).png_bytes()
    out[I + "hazard_decal.png"] = item_hazard_decal().png_bytes()

    # gui, particles
    out[f"{A}textures/gui/emotes.png"] = tex_emotes().png_bytes()
    out[f"{A}textures/gui/emote_bubble.png"] = tex_emote_bubble().png_bytes()
    frames = particle_frames()
    for pid, n in PARTICLES.items():
        for k in range(n):
            out[f"{A}textures/particle/{pid}_{k}.png"] = frames[f"{pid}_{k}"].png_bytes()
        out[f"{A}particles/{pid}.json"] = jsonb({"textures": [f"{MOD}:{pid}_{k}" for k in range(n)]})
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", help="write under this directory instead of the repo resources tree")
    ap.add_argument("--check", action="store_true", help="compare with the files on disk; exit 1 on any difference")
    ap.add_argument("--geometry", action="store_true", help="print the model geometry tables as markdown and exit")
    args = ap.parse_args(argv)
    if args.geometry:
        print(geometry_markdown())
        print(hats_markdown())
        return 0
    files = build()
    root = args.out or RES
    if args.check:
        bad = []
        for rel, data in sorted(files.items()):
            path = os.path.join(root, rel)
            if not os.path.exists(path):
                bad.append("missing  " + rel)
                continue
            with open(path, "rb") as fh:
                if fh.read() != data:
                    bad.append("differs  " + rel)
        for line in bad:
            print(line)
        print(f"{len(files) - len(bad)} of {len(files)} files match")
        return 1 if bad else 0
    for rel, data in sorted(files.items()):
        path = os.path.join(root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        # write in place (truncate) so the file identity is kept
        with open(path, "wb") as fh:
            fh.write(data)
    print(f"wrote {len(files)} files under {root}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
