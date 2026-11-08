#!/usr/bin/env python3
"""
gen_companion_sounds.py -- synthesises every NeroCompanion sound from scratch (no samples, no downloads).

Usage:
    python3 tools/gen_companion_sounds.py            # render WAVs in a temp dir, encode mono OGG into the repo, write sounds.json
    python3 tools/gen_companion_sounds.py --wav DIR  # render WAVs into DIR only (no encoder needed)
    python3 tools/gen_companion_sounds.py --check    # confirm every key has its .ogg and sounds.json matches

Every sound is stdlib `wave` + numpy with a fixed seed, so the WAV renders are byte-identical run to run.
Encoding to Ogg Vorbis uses `ffmpeg` (libvorbis); if it is missing the WAVs are left in --wav DIR and the exact
command to convert them is printed. Minecraft needs mono Vorbis: that is what the encoder call produces.
"""
import argparse
import json
import math
import os
import shutil
import subprocess
import sys
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "common", "src", "main", "resources")
MOD = "nerocompanion"
SR = 32000
SEED = 79

# --------------------------------------------------------------------------------------------- synth helpers
def t(seconds):
    return np.arange(int(SR * seconds)) / SR


def note(name):
    names = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
    n, octave = names[name[0]], int(name[-1])
    semi = n + (1 if "#" in name else 0) + 12 * (octave - 4)
    return 440.0 * 2 ** ((semi - 9) / 12.0)


def sine(freq, seconds, phase=0.0):
    f = np.broadcast_to(np.asarray(freq, dtype=float), t(seconds).shape) if np.ndim(freq) else freq
    if np.ndim(freq):
        ph = 2 * np.pi * np.cumsum(f) / SR
    else:
        ph = 2 * np.pi * freq * t(seconds)
    return np.sin(ph + phase)


def square(freq, seconds, duty=0.5):
    tt = t(seconds)
    if np.ndim(freq):
        ph = np.cumsum(np.asarray(freq, dtype=float)) / SR
    else:
        ph = freq * tt
    return np.where((ph % 1.0) < duty, 1.0, -1.0)


def tri(freq, seconds):
    ph = (freq * t(seconds)) % 1.0
    return 4 * np.abs(ph - 0.5) - 1


def saw(freq, seconds):
    ph = (freq * t(seconds)) % 1.0
    return 2 * ph - 1


def noise(seconds, rng):
    return rng.uniform(-1, 1, int(SR * seconds))


def sweep(f0, f1, seconds, curve="exp"):
    tt = t(seconds)
    if curve == "exp":
        return f0 * (f1 / f0) ** (tt / seconds)
    return f0 + (f1 - f0) * tt / seconds


def env(x, attack=0.008, release=0.05, hold=None):
    n = len(x)
    a = max(1, int(attack * SR))
    r = max(1, int(release * SR))
    e = np.ones(n)
    e[:a] = np.linspace(0, 1, a)
    if r < n:
        e[n - r:] = np.linspace(1, 0, r) ** 2
    return x * e


def lowpass(x, cutoff, poles=2):
    rc = 1.0 / (2 * np.pi * cutoff)
    alpha = (1.0 / SR) / (rc + 1.0 / SR)
    y = x.astype(float)
    for _ in range(poles):
        out = np.empty_like(y)
        acc = 0.0
        for i, v in enumerate(y):
            acc += alpha * (v - acc)
            out[i] = acc
        y = out
    return y


def bandpass(x, centre, q=4.0):
    w0 = 2 * np.pi * centre / SR
    alpha = math.sin(w0) / (2 * q)
    b0, b1, b2 = alpha, 0.0, -alpha
    a0, a1, a2 = 1 + alpha, -2 * math.cos(w0), 1 - alpha
    y = np.zeros_like(x, dtype=float)
    x1 = x2 = y1 = y2 = 0.0
    for i, v in enumerate(x):
        out = (b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
        x2, x1, y2, y1 = x1, v, y1, out
        y[i] = out
    return y


def vibrato(freq, seconds, rate=5.0, depth=0.01):
    return freq * (1 + depth * np.sin(2 * np.pi * rate * t(seconds)))


def seq(*parts, gap=0.0):
    g = np.zeros(int(SR * gap))
    out = []
    for i, p in enumerate(parts):
        out.append(p)
        if gap and i < len(parts) - 1:
            out.append(g)
    return np.concatenate(out)


def mixdown(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def norm(x, peak=0.8):
    m = np.max(np.abs(x)) or 1.0
    return x / m * peak


def voice(x):
    """The companion voice: 2-pole low-pass at 2.4 kHz (010 section 7)."""
    return lowpass(x, 2400)


def chirp_note(name, seconds, wave_="square", release=0.04):
    f = note(name)
    x = square(f, seconds, 0.4) if wave_ == "square" else sine(f, seconds)
    return env(x, 0.008, release)


# --------------------------------------------------------------------------------------------- the 30 sounds
def build(rng):
    S = {}
    # trait chirps
    S["chirp.curious"] = voice(seq(chirp_note("E5", 0.11), chirp_note("A5", 0.13), gap=0.01))
    S["chirp.loyal"] = voice(env(sine(vibrato(note("C5"), 0.35, 5.5, 0.012), 0.35), 0.01, 0.12))
    g = sweep(note("A3"), note("F3"), 0.3)
    S["chirp.grumpy"] = voice(env(0.8 * square(g, 0.3, 0.45) + 0.2 * noise(0.3, rng), 0.01, 0.08))
    S["chirp.sleepy"] = voice(env(sine(sweep(note("G4"), note("D4"), 0.5), 0.5), 0.02, 0.25))
    S["chirp.showoff"] = voice(seq(chirp_note("C5", 0.09), chirp_note("E5", 0.09), chirp_note("G5", 0.11), gap=0.005))
    S["chirp.shy"] = 0.3 * voice(env(sine(note("B4"), 0.12), 0.01, 0.07))
    # body
    hover = 0.7 * tri(90, 2.0) + 0.3 * lowpass(noise(2.0, rng), 600)
    S["drone.hover"] = 0.25 * hover / np.max(np.abs(hover))               # loop
    S["bot.step"] = env(0.5 * lowpass(noise(0.08, rng), 1800) + 0.8 * env(sine(180, 0.08), 0.002, 0.06), 0.002, 0.04)
    S["bot.servo"] = env(sine(sweep(300, 600, 0.12), 0.12), 0.01, 0.03)
    hiss = env(lowpass(noise(0.2, rng), 3000) * np.linspace(0.2, 1.0, int(SR * 0.2)), 0.05, 0.05)
    powerup = voice(seq(chirp_note("C4", 0.12), chirp_note("G4", 0.12), chirp_note("C5", 0.16, release=0.1)))
    S["deploy"] = seq(0.6 * hiss, powerup)
    S["recall"] = seq(powerup[::-1][: int(SR * 0.28)], 0.5 * hiss[::-1][: int(SR * 0.12)])
    S["charge.hum"] = 0.3 * norm(0.7 * sine(60, 2.0) + 0.3 * sine(120, 2.0), 1.0)      # loop
    S["charge.spark"] = env(bandpass(noise(0.03, rng), 3000, 3.0), 0.002, 0.015)
    S["charge.full"] = seq(env(sine(note("G5"), 0.18), 0.005, 0.1), env(sine(note("C6"), 0.32), 0.005, 0.4))
    clicks = np.zeros(int(SR * 0.5))
    for i in range(6):
        p = int(SR * (0.02 + i * 0.08))
        c = env(bandpass(noise(0.02, rng), 2200, 2.0), 0.001, 0.015)
        clicks[p:p + len(c)] += c
    S["crank.turn"] = 0.8 * clicks + 0.25 * env(sine(sweep(200, 320, 0.5, "lin"), 0.5), 0.02, 0.1)
    S["low_battery"] = 0.8 * voice(seq(chirp_note("A4", 0.12), chirp_note("A4", 0.12), chirp_note("F4", 0.25, release=0.12), gap=0.03))
    S["danger_alert"] = 0.9 * voice(seq(*[chirp_note("E5", 0.07) for _ in range(4)], gap=0.055))
    ping = env(sine(1200, 0.04), 0.002, 0.03)
    echo = np.zeros(int(SR * 0.35))
    echo[:len(ping)] += ping
    for i, gain in enumerate((0.5, 0.3, 0.18, 0.1)):
        p = int(SR * 0.07 * (i + 1))
        echo[p:p + len(ping)] += gain * ping
    S["scout.ping"] = echo
    swell = lowpass(noise(0.8, rng), 1500) * np.linspace(0.0, 0.25, int(SR * 0.8))
    S["salvage.chime"] = mixdown(voice(seq(chirp_note("E5", 0.18, "sine", 0.1), chirp_note("G5", 0.18, "sine", 0.1),
                                           chirp_note("B5", 0.3, "sine", 0.2), gap=0.05)), 0.4 * env(swell, 0.1, 0.3))
    S["salvage.return"] = seq(env(lowpass(noise(0.15, rng), 2500), 0.01, 0.1), env(sine(note("C6"), 0.32), 0.005, 0.4))
    purr = sine(55, 1.0) * (0.6 + 0.4 * np.sign(np.sin(2 * np.pi * 8 * t(1.0))))
    S["pet.purr"] = mixdown(env(purr, 0.05, 0.2), 0.25 * voice(env(sine(note("G5"), 0.2), 0.01, 0.1)))
    down = env(sine(sweep(600, 80, 0.62), 0.62), 0.01, 0.2)
    click = env(bandpass(noise(0.02, rng), 1500, 2.0), 0.001, 0.015)
    S["sleep.powerdown"] = seq(down, click, gap=0.03)
    S["wake"] = seq(env(sine(sweep(120, 500, 0.4), 0.4), 0.02, 0.05), S["chirp.curious"])
    S["fetch.catch"] = seq(env(sine(400, 0.05), 0.002, 0.04), click)
    S["bond.levelup"] = seq(*[voice(env(0.5 * sine(note(n), 0.16) + 0.5 * square(note(n), 0.16, 0.3), 0.005, 0.06))
                              for n in ("C5", "D5", "E5", "G5")],
                            voice(env(0.5 * sine(note("C6"), 0.5) + 0.5 * square(note("C6"), 0.5, 0.3), 0.005, 0.6)), gap=0.01)
    rattle = np.zeros(int(SR * 0.4))
    for _ in range(6):
        p = int(rng.uniform(0, 0.36) * SR)
        c = env(bandpass(noise(0.03, rng), rng.uniform(1800, 3200), 3.0), 0.001, 0.02)
        rattle[p:p + len(c)] += c
    S["scrapling.hatch"] = seq(0.8 * rattle, voice(seq(chirp_note("F5", 0.11), chirp_note("B5", 0.13), gap=0.01)))
    drill = 0.6 * saw(140, 1.0) + 0.4 * lowpass(noise(1.0, rng), 2200)
    S["mine.drill"] = 0.35 * drill / np.max(np.abs(drill))                 # loop
    weld = np.zeros(int(SR * 0.4))
    for i in range(4):
        p = int(SR * i * 0.1)
        c = env(bandpass(noise(0.08, rng), 2500, 2.5), 0.002, 0.04)
        weld[p:p + len(c)] += c
    S["repair.weld"] = weld
    S["combat.lunge"] = env(bandpass(noise(0.1, rng), 1000, 1.0) * np.linspace(1.0, 0.2, int(SR * 0.1)), 0.005, 0.05)
    S["ui.click"] = env(sine(1000, 0.02), 0.001, 0.012)
    return {k: norm(v, 0.8 if k not in ("drone.hover", "charge.hum", "mine.drill") else float(np.max(np.abs(v)))) for k, v in S.items()}


SUBTITLES = {
    "chirp.curious": "Companion chirps", "chirp.loyal": "Companion hums", "chirp.grumpy": "Companion grumbles",
    "chirp.sleepy": "Companion yawns", "chirp.showoff": "Companion trills", "chirp.shy": "Companion peeps",
    "drone.hover": "Drone hums", "bot.step": "Bot steps", "bot.servo": "Servo whirs", "deploy": "Companion deploys",
    "recall": "Companion recalled", "charge.hum": "Charging hums", "charge.spark": "Charging pad crackles",
    "charge.full": "Charge complete", "crank.turn": "Hand crank turns", "low_battery": "Companion battery low",
    "danger_alert": "Scout raises the alarm", "scout.ping": "Scout pings", "salvage.chime": "Companion kept your things",
    "salvage.return": "Items returned", "pet.purr": "Companion purrs", "sleep.powerdown": "Companion powers down",
    "wake": "Companion wakes", "fetch.catch": "Fetch ball caught", "bond.levelup": "Bond grows",
    "scrapling.hatch": "Scrapling hatches", "mine.drill": "Drill grinds", "repair.weld": "Welder crackles",
    "combat.lunge": "Combat bot lunges", "ui.click": "Click",
}
LOOPS = {"drone.hover", "charge.hum", "mine.drill"}
VOLUME = {"drone.hover": 0.25, "charge.hum": 0.3, "mine.drill": 0.35, "chirp.shy": 0.3, "low_battery": 0.8, "danger_alert": 0.9}
ATTENUATION = {"drone.hover": 8}


def sounds_json():
    out = {}
    for key in sorted(SUBTITLES):
        entry = {"name": f"{MOD}:{key.replace('.', '/')}", "stream": False}
        if key in VOLUME:
            entry["volume"] = VOLUME[key]
        if key in ATTENUATION:
            entry["attenuation_distance"] = ATTENUATION[key]
        out[key] = {"subtitle": f"subtitles.{MOD}.{key}", "sounds": [entry]}
    return json.dumps(out, indent=2) + "\n"


def write_wav(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--wav", help="render WAVs into this directory and stop")
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args(argv)
    sdir = os.path.join(RES, "assets", MOD, "sounds")
    sjson = os.path.join(RES, "assets", MOD, "sounds.json")
    if args.check:
        bad = [k for k in SUBTITLES if not os.path.exists(os.path.join(sdir, k.replace(".", "/") + ".ogg"))]
        same = os.path.exists(sjson) and open(sjson, encoding="utf-8").read() == sounds_json()
        for k in bad:
            print("missing ogg:", k)
        print("sounds.json", "matches" if same else "DIFFERS")
        return 1 if bad or not same else 0
    rng = np.random.default_rng(SEED)
    sounds = build(rng)
    wav_dir = args.wav or tempfile.mkdtemp(prefix="nc_sounds_")
    os.makedirs(wav_dir, exist_ok=True)
    for key, x in sounds.items():
        write_wav(os.path.join(wav_dir, key + ".wav"), x)
    print(f"rendered {len(sounds)} WAVs in {wav_dir}")
    if args.wav:
        return 0
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        print("ffmpeg not found; convert with:  for f in *.wav; do ffmpeg -y -i \"$f\" -ac 1 -c:a libvorbis -q:a 4 "
              "\"${f%.wav}.ogg\"; done   (then place each at sounds/<key with . as />.ogg)")
        return 2
    for key in sounds:
        dst = os.path.join(sdir, key.replace(".", "/") + ".ogg")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        tmp = os.path.join(wav_dir, key + ".ogg")
        subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", os.path.join(wav_dir, key + ".wav"), "-ac", "1",
                        "-c:a", "libvorbis", "-q:a", "4", tmp], check=True)
        with open(tmp, "rb") as src, open(dst, "wb") as out:   # truncate-in-place write
            out.write(src.read())
    with open(sjson, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(sounds_json())
    print(f"encoded {len(sounds)} OGGs under {sdir} and wrote sounds.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
