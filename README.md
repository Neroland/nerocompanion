# NeroCompanion

> Part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

**Version `0.6.0-beta.1`.** Personal drones, helper bots and a scrap pet that follow you, work for you and grow
attached to you. Player documentation lives in the [wiki](wiki/Home.md); this file describes what the source does.

## What the mod does

- **Seven companion types**: Helper Drone, Scout Drone, Mining Bot, Carry Bot, Repair Bot, Combat Bot and the
  Scrapling pet. Each is an owner-bound entity carried in a **Capsule** item, deployed onto a block and recalled
  back into the Capsule with every field intact (name, chips, bond, look, energy, health, home, job).
- **Modes** (Follow, Stay, Guard, Mine, Carry, Repair, Scout, Sleep) drive a per-companion goal table. The
  companion type's data file says which modes, chip slots and work goals a chassis has.
- **Energy** is Forge Energy (FE) stored on the companion. The **Charging Pad** and **Companion Bay** take energy
  through Neroland Core's energy lookup and side configuration; the **Hand Crank Charger** needs no power mod.
- **Chips** are installed into a Neroland Core `UpgradeContainer`; Speed, Efficiency, Range and Capacity chips
  count as Core upgrade modules. Slots, categories and exclusive pairs are validated server-side.
- **Authority**: every block break, container open, placement and attack a bot makes is checked as its owner, by
  owner UUID, against vanilla rules, Neroland Core gates, the server's PvP setting and (when installed) NeroColonies,
  NeroSecurity and NeroFactions. Offline owners' companions sleep and never act.
- **Recovery ledger**: one server-side record per companion (out, in a Capsule, parked or lost) drives the
  per-player and per-region caps, duplicate protection and reclaiming lost companions.
- **Death salvage**: carry bots and Salvage-chip companions gather only their owner's death drops and hold them.
- **Personality, bond, chatter, perching, fetch** and **skins and hats** are cosmetic and never touch stats.
- **Privacy**: export, per-player opt-outs, retention and erasure through Core's shared erasure hook. See
  [PRIVACY.md](PRIVACY.md).
- **NeroLink**: a link module for the NeroLink bridge and app. See [USING-NEROLINK.md](USING-NEROLINK.md).

## Source layout

`common/src/main/java/za/co/neroland/nerocompanion/`:

| Package | What it holds |
|---|---|
| `entity`, `entity/type`, `entity/types` | The companion entity, its persisted record, the data-driven type registry and the seven chassis |
| `ai` | Mode controller, goal table and the shared and per-type goals |
| `energy`, `charge` | Battery maths; Charging Pad, Companion Bay and Hand Crank blocks |
| `chip` | Chip kinds, items, slots, validation and effects |
| `authority` | Owner-authority checks and the provider chain siblings plug into |
| `scheduler` | Caps, distance throttling and sleep rules |
| `recovery`, `data` | Recovery ledger, capsule vault and their saved data |
| `salvage`, `incubation`, `fetch`, `mining`, `carry`, `scout`, `job` | Work and pet systems |
| `personality`, `bond`, `look` | Traits and chatter, bond levels, skins and hats |
| `privacy`, `alert`, `link`, `telemetry` | Export, opt-outs, retention and erasure; Core alerts; the NeroLink module; crash reporting |
| `compat/<sibling>` | Optional sibling façades; only a bridge class inside each one names the sibling |
| `progression` | Milestone gates and quest hooks |
| `command` | `/nscomp` |
| `client`, `hud`, `menu`, `network`, `registry`, `platform` | Rendering, screens, HUD strip, payloads, registration and loader seams |

Data lives in `common/src/main/resources/data/nerocompanion/`: `companion_types/`, `skins/`, `hats/`,
`chip_rules/`, recipes, tags and the NeroQuests chapter.

## Build targets

- **Minecraft:** 1.21.1, 1.21.4, 1.21.5, 1.21.8 (also 1.21.7), 1.21.10 (also 1.21.9), 1.21.11, 26.1.2, 26.2 and 26.3
- **Loaders:** NeoForge, Forge and Fabric (27 builds). **Java:** 21 for 1.21.x, 25 for 26.x.
- **Requires** Neroland Core 1.17.4 or later. Every other Neroland mod is optional.
- Mod id `nerocompanion`, package `za.co.neroland.nerocompanion`. Build one cell with
  `./gradlew :neoforge:26.3:build`; `tools/check_companion_assets.py` checks that every asset the code names exists.

## See also

- [CHANGELOG.md](CHANGELOG.md), [USING-CORE.md](USING-CORE.md), [USING-NEROLINK.md](USING-NEROLINK.md),
  [PRIVACY.md](PRIVACY.md)
- [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) for contributor context

## Privacy & telemetry

> **Telemetry notice:** NeroCompanion sends anonymous error reports (stack trace + mod/game
> versions only — never IPs, usernames, UUIDs, or world data) to the developers via Sentry
> (EU servers) so crashes can be fixed. On by default — opt out any time by setting `telemetryEnabled = false`
> in `config/nerocompanion.properties`. Full details:
> [PRIVACY.md](https://github.com/Neroland/nerocompanion/blob/main/PRIVACY.md).
