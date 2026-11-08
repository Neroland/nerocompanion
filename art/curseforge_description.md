[![NeroLink App — Beta](https://img.shields.io/badge/NeroLink_App-Now_in_Beta-60d4e8?style=for-the-badge)](https://nerolandmc.net/nerolink/#beta) [![Explore the Neroland ecosystem](https://img.shields.io/badge/Explore-The_Neroland_Ecosystem-1a5a6c?style=for-the-badge)](https://nerolandmc.net/ecosystem/)

> 📱 **NeroLink App Beta — your Neroland world on your phone.** Check energy, alerts and machines live, claim quest rewards and search your storage without logging in. **[Join the beta at nerolandmc.net →](https://nerolandmc.net/nerolink/#beta)**
>
> 🌌 **Explore the Neroland ecosystem.** See how NeroCompanion fits together with the rest of the Nero mods — every mod, wiki and changelog in one place. **[View the ecosystem at nerolandmc.net →](https://nerolandmc.net/ecosystem/)** · [NeroCompanion on the website](https://nerolandmc.net/mods/nerocompanion/)

---

# NeroCompanion

**Bring one along: personal drones, helper bots and a little scrap pet that follow you, work for you and grow attached to you.**

NeroCompanion is the **pets & helper robots** mod of the Neroland ecosystem. Every companion is owner-bound, lives in a **Capsule** you can carry around, runs on Forge Energy and is customised with **chips**. Mining, hauling, repairing, guarding and scouting, or just a Scrapling napping on its pad.

Built on **Neroland Core**, so its upgrade modules, FE energy, progression gates and shared tags are the same ones the rest of the lineup uses. NeroCompanion has its own creative tab.

---

## Seven companions

- 🛸 **Helper Drone**: follows and perches on your shoulder, picks up what you have no room for, scouts, and lights the way with a Lamp chip.
- 🔭 **Scout Drone**: flies ahead and marks ores and hostile mobs for you only, and warns you when something is behind you.
- ⛏️ **Mining Bot**: mark an area with the Remote and it digs it out block by block, only where you may build, and delivers the drops.
- 📦 **Carry Bot**: picks up dropped items and delivers them to a container you bind with the Remote.
- 🔧 **Repair Bot**: mends your worn armour and hotbar items for energy.
- ⚔️ **Combat Bot**: guards you, or its post, against hostile mobs. Fragile by design; it never attacks players when the server's bot PvP is off.
- 🐾 **Scrapling**: a pet that hatches from a Scrap Egg incubated on a Charging Pad. It follows, perches, fetches your Fetch Ball, plays and naps. It never fights.

Craft a companion as a Capsule and use it on a block to activate it. Sneak-use the Remote to recall it: name, chips, bond, look, energy and home all come back with it.

## Modes, chips and charging

- 🎛️ **Modes**: Follow, Stay, Guard, Mine, Carry, Repair, Scout and Sleep, each offered only by the companions that can do it. Companions sleep (no thinking, no battery use) when you are offline, in another dimension or far away.
- 🧩 **18 chip kinds in 30 items**: stat chips in tiers I-III (Speed, Efficiency, Range, Capacity, Armour, Vitality), capability chips (Guard Protocol, Ore Filter, Auto-Deposit, Cargo Rack, Lamp, Salvage, Silent Running, Radiation Shielding) and cosmetic chips.
- 🔌 **Charging Pad** (takes FE from any side), **Companion Bay** (park up to four companions; they charge and stop counting toward your limit) and the **Hand Crank Charger**, which needs no power mod.

## Recovery and death salvage

- 🧭 **Nothing is lost for good.** The server keeps a recovery ledger of every companion. A companion that is killed, or stays unseen in an unloaded area, can be reclaimed from any Companion Bay or with `/nscomp recover` as a Capsule with its chips, bond, name and looks. Per-player and per-area limits hold across restarts, and a copied Capsule can never bring out a second companion.
- 💀 **Death salvage.** Carry Bots, and any companion with a Salvage Chip, gather the items you dropped when you died and hold them until you talk to them. They only ever take your own drops, and other players' companions leave your drops alone while the salvage window is open.

## Personality, bond and looks

Every companion has one of six traits that shape its idle behaviour and chatter. Time together, pats and finished jobs raise its bond (levels 1-4, cosmetic only). Unlock 10 skins and 10 hats and try them on in the Skin Studio with a live preview. Looks never change what a companion can do.

## Plays fair on servers

- 🛡️ Every block a bot breaks, container it opens, light it places and mob it attacks is checked **as its owner**: vanilla rules, spawn protection, Neroland Core gates, the server's PvP setting and, when installed, NeroColonies, NeroSecurity and NeroFactions.
- 📊 Operators get `/nscomp budget`, `/nscomp types`, `/nscomp admin release` and caps per player and per area.

## Privacy (POPIA / GDPR)

NeroCompanion keeps only what ownership needs: the owner as a UUID (never a username), the companion's own chips, looks and bond, and the recovery ledger. `/nscomp export` writes everything the mod holds about you to one file. `/nscomp privacy names|chatter|push on|off` lets you hide your companions' names, turn chatter off and turn notifications off. Inactive players' data is deleted after a retention period, and Neroland Core's erasure command removes a player's companion data along with everything else.

> **Telemetry notice:** NeroCompanion sends anonymous error reports (stack trace + mod/game
> versions only — never IPs, usernames, UUIDs, or world data) to the developers via Sentry
> (EU servers) so crashes can be fixed. On by default — opt out any time by setting `telemetryEnabled = false`
> in `config/nerocompanion.properties`. Full details:
> [PRIVACY.md](https://github.com/Neroland/nerocompanion/blob/main/PRIVACY.md).

## Works with the Neroland mods, all optional

- 🌌 **Nerospace**: flying companions fly faster on light worlds and slower on heavy ones; radiation powers a companion down unless it carries the Radiation Shielding chip.
- 📦 **NeroLogistics**: carry and mining bots can deliver into a network drive bay.
- 💰 **NeroEconomy**: buy shop skins in the Skin Studio; servers can charge for reclaiming a lost companion.
- 🏘️ **NeroColonies / NeroSecurity / NeroFactions**: companions respect colony claims and secured devices, and never attack their owner's faction.
- 📜 **NeroQuests**: the 14-quest *Companion Log* chapter.
- 📱 **NeroLink**: see your companions in the NeroLink app, change their mode, recall, reclaim and rename them, with optional push notifications. The app never shows coordinates.

Everything works on Neroland Core alone.

## Requirements & compatibility

- **Requires [Neroland Core](https://www.curseforge.com/minecraft/mc-mods/neroland-core) 1.17.4 or later.** Install it alongside NeroCompanion.
- **Minecraft** 1.21.1, 1.21.4, 1.21.5, 1.21.8 (also 1.21.7), 1.21.10 (also 1.21.9), 1.21.11, 26.1.2, 26.2 and 26.3, on **NeoForge, Forge and Fabric** (Fabric also needs Fabric API).
- Charging Pads accept energy from any FE source.
- **Modpacks are allowed and encouraged**, on any platform, no need to ask. Use the official files and credit *NeroCompanion by Neroland* with links to this page and the [GitHub repository](https://github.com/Neroland/nerocompanion). Full terms: [LICENSE](https://github.com/Neroland/nerocompanion/blob/main/LICENSE).

## Links

- 📖 **[Wiki](https://github.com/Neroland/nerocompanion/wiki)**: every companion, chip and system documented.
- 💬 **[Discord](https://discord.gg/ArPXvYUzJG)**: chat, help and sneak peeks.
- 🐞 **[Issues](https://github.com/Neroland/nerocompanion/issues)**: bug reports and feature requests.
- 🗒️ **[Changelog](https://github.com/Neroland/nerocompanion/blob/main/CHANGELOG.md)**

---

*Created by Neroland. The project logo was made with the help of AI image tools; in-game art is generated by the project's own tooling and refined by hand.*
