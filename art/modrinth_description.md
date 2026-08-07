# NeroCompanion

**Bring one along — personal drones, helper bots and tamed alien pets that follow you, work for you, and fight at your side.**

NeroCompanion is the **pets & helper robots** mod of the Neroland ecosystem — the personal, screenshot-able layer where the same energy that powers your factory powers the little bot trailing behind you. Deploy owner-bound companions driven by goal-based AI, customise them through modular upgrade chips, and keep them running on rechargeable charge. Mining, hauling, repairing, guarding — or just a charming pet alien brought home from another world.

Built on **Neroland Core**, so its upgrade-module framework, FE-compatible energy, progression gates and shared `c:` tags are the same ones used across the lineup. Because companions are owner-bound, ownership is stored as a UUID and routed through Core's shared data-erasure hook. *(Planned — in design; not yet released.)*

---

## 1. Personal drones — your first companion

1. **Deploy and follow.** A small flying drone that follows you, holds a few chips, and teaches the whole mod. It stores owner UUID, active mode, energy and installed chips in NBT, and recalls to a **drone capsule** item for transport.
2. **Chip-driven.** Drones carry a small chip inventory backed by Core's upgrade-module framework — the canonical surface for both stat upgrades and cosmetic skins.

- 🛸 **Owner-bound** — bound on first deploy, mirroring the vanilla tamed-entity ownership pattern.
- 🔋 **Charge to run** — drones drain energy while active and idle at low power rather than dying.

## 2. Helper & utility bots

- ⛏️ **Mining bots** — autonomous block-breakers that clear an assigned area or vein, gated by owner permission and Core progression; speed, area and ore filters are set by chips. Deliberately slower than a player with good tools.
- 🔧 **Repair bots** — scan for your damaged gear and nearby machines and restore durability over time, spending energy and optional repair-material chips.
- ⚔️ **Combat bots** — guard-and-fight companions that engage hostiles, never targeting the owner or team. Fragile and energy-limited by design so they augment, not replace, player skill.
- 👾 **Pet aliens** — charming, characterful creatures recovered from space exploration, leaning to presence over raw utility.

## 3. Modes — follow / guard / mine / carry

Every companion carries an active-mode enum in NBT, and a small state machine swaps its AI goal set when the mode changes. *Follow* sticks to the owner; *guard* holds a radius (defensive for most, aggressive for combat bots); *mine* works an area; *carry* shuttles items between you, containers and pickups.

- 🎛️ **Per-type modes** — supported modes are declared per companion and can be unlocked or extended by chips (a basic drone might only follow until a chip grants carry).
- 📦 **Carry integration** — carry-mode companions expose an item-handler capability — the seam where they connect to **NeroLogistics** for routing and deposit targets.

## 4. Upgrade chips & charging

1. **Upgrade chips.** Modular items routed through **Core's upgrade-module framework**, sharing install, validation, tooltip and tier handling with every Nero mod. Chips come in stat, capability and cosmetic-behaviour varieties, with per-type slot counts and tier caps so no single companion stacks every effect.
2. **Drone charging pads.** Powered blocks that pull FE from an attached network, buffer it internally, and top up docked or nearby companions — doubling as a home and recall point. Place them across a base or at remote, off-world outposts.

- 🔌 **Any FE source** — pads accept power from NeroPower, Nerotech, or external FE through Core tags.
- ⚖️ **Energy is the balance dial** — generous enough to feel good, tight enough that companions need infrastructure.

## 5. Marketable & recoverable

- 🎨 **Cosmetic skins** — alternate textures, colours and trim stored as an NBT identifier; purely visual, never touching stats, and optionally gated behind Core's currency/reputation API.
- 🧭 **Lost drone recovery** — persistent saved data tracks each owner's companions so a despawned or abandoned one can be recalled or reclaimed, chips and skin intact.
- 🪪 **Naming & identity** — companions keep a name and identity through recall, reinforcing the attachment the mod is built around.

## Privacy (POPIA / GDPR)

NeroCompanion stores **only what ownership needs** — a companion's owner is kept as a **UUID** (never a username) in entity NBT and in the lost-companion saved data, alongside last-known position and the companion's own chips and skin. It carries **no chat, no analytics profile, and no personal data beyond that owner UUID**, and it registers with **Core's shared data-erasure hook** so a single erase request clears your companion ownership alongside every other Neroland mod. Any crash telemetry follows the ecosystem standard: anonymous, version-strings only, and opt-out.

## Why it fits the ecosystem

- 🧩 **Built on Neroland Core** — one upgrade-module system, one FE-compatible energy surface, one progression arc, shared `c:` tags. NeroCompanion ships in its own creative tab.
- 🤝 **Interoperates, never hard-depends** — soft synergies make it better without being required: **NeroPower / Nerotech** power the pads, **NeroLogistics** takes carry- and mining-bot output, **NeroCreatures** supplies alien types that NeroCompanion adds the binding/recall layer to, and **Nerospace** provides the worlds where pet aliens are found. With them absent, it still plays standalone.
- 🌍 **Off-world ready** — companions are designed to keep working on Nerospace and Ad Astra-style planet dimensions, so a drone bound on Earth still functions at a colony.
- 🧱 **Cross-loader** — NeoForge, Forge and Fabric on Minecraft **26.1.2** and **26.2**.

## Requirements & compatibility

- **Requires [Neroland Core](https://modrinth.com/mod/nerolandcore)** — install it alongside NeroCompanion (it loads first).
- Optional but recommended: **NeroPower / Nerotech** for pad power, **NeroLogistics** for hauling, **NeroCreatures** and **Nerospace** for pet aliens.
- Charging pads accept energy from **any FE source** — Create, AE2, Mekanism, Ad Astra and **Energized Power** interoperate through Core's common tags as the 26.x ecosystem fills in, with no hard dependency on any of them.
- **Modpacks are allowed and encouraged** — any platform, no need to ask. Use the official files and credit *NeroCompanion by Neroland* with links to the [CurseForge page](https://www.curseforge.com/minecraft/mc-mods/nerocompanion) and the [GitHub repository](https://github.com/Neroland/nerocompanion). Full terms: [LICENSE](https://github.com/Neroland/nerocompanion/blob/main/LICENSE).

## Links

- 📖 **[Wiki](https://github.com/Neroland/nerocompanion/wiki)** — every companion, chip, and system documented.
- 💬 **[Discord](https://discord.gg/ArPXvYUzJG)** — chat, help, and sneak peeks.
- 🐞 **[Issues](https://github.com/Neroland/nerocompanion/issues)** — bug reports and feature requests.
- 🗒️ **[Changelog](https://github.com/Neroland/nerocompanion/blob/main/CHANGELOG.md)**
- 🔥 **[Also on CurseForge](https://www.curseforge.com/minecraft/mc-mods/nerocompanion)**

---

*Created by Neroland. The project logo was made with the help of AI image tools; in-game art is generated by the project's own tooling and refined by hand.*
