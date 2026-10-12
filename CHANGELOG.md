# Changelog

All notable changes to **NeroCompanion** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.0-alpha.1] - 2026-10-11

Quest pack and milestone seams. Requires Neroland Core 1.15.0. Still no blocks, items or recipes: the quests
become completable once companions exist.

### Added

- **NeroQuests quest pack.** Ships chapter `nerocompanion:companion_log` "Companion Log", 6 quests
  (`blueprint`, `first_deploy`, `first_chip`, `pet_adopted`, `full_squad`, `companion_keeper`), that appears in
  the quest book when NeroQuests is installed; inert otherwise, no dependency in either direction. Until the
  companion items and entities land, the quests are visible but cannot be completed.
- **Milestone gates** (player-scope Core record gates, no `requires`, never checked for access):
  `nerocompanion:first_deploy`, `first_chip`, `pet_adopted` and `full_squad`.
  `progression/CompanionMilestones` has one `record...` call per gate for the companion code to make when the
  matching thing happens (online owner, or offline owner by UUID through Core 1.15.0).
- **Skin hook gates** `nerocompanion:hook/skin/<id>` (shipped: `aurora`, rewarded by *Companion Keeper*).
  `progression/QuestHooks` listens on Core's progression events; the open gate is the permanent unlock
  record, so the future skin picker reads `QuestHooks.isSkinUnlocked` as a display check. For now the only
  visible effect is a chat line when the gate opens for an online player; the skins themselves land with the
  companion content.
- `QuestPackTest` (JUnit 5, plain JVM) checks the pack against the quest-mod format and that every gate the
  code names is shipped as a player-scope record. Run with `gradlew :neoforge:26.2:test`.

### Changed

- Neroland Core floor raised to **1.15.0** (offline-capable `ProgressionGates.open(server, uuid, gate)`).
- No threshold channels are published: a companion count is about one owner, so there is no world milestone
  that would not hint at a specific player (POPIA/GDPR). Milestones live only in Core's player-scope gate
  store, which Core's privacy document covers.

## [0.3.0-alpha.1] - 2026-10-11

Optional NeroEconomy seams. No blocks, items or recipes yet.

### Added

- **NeroEconomy seams** (NeroEconomy stays optional; NeroCompanion builds, loads and runs without it):
  `economy/CompanionEconomy.chargeCosmetic` charges reason `cosmetic_purchase` with the purchase id as the
  replay-safe key, and `askPrice`, `bidPrice` and `remainingToday` give companions Merchant House prices
  and the player's own daily allowance.
- The carry-bot rules (no feeding from creative or spectator players, no chunk loading), documented in the
  README and the new wiki page *NeroEconomy integration*.

### Changed

- NeroEconomy is declared as an optional dependency on every loader (Fabric `suggests`, NeoForge
  `optional`, Forge `mandatory = false`, floor `0.3.0-beta.1`) and compiled `compileOnly`.
- Neroland Core (floor 1.13.0) is now a required dependency, and NeroCompanion ships anonymous, opt-out
  crash reporting (see PRIVACY.md).

## [0.2.0-alpha.1] - 2026-09-24

EMI compatibility. No gameplay, id, tag or config change.

### Added

- **EMI support.** NeroCompanion adds no recipes of its own, so nothing needed a plugin. The build now
  compiles against the community EMI Unofficial Port (Unstable), the only EMI build for Minecraft 26.x,
  and dev clients load it with `-PwithEmi` (default runs stay JEI-only). EMI stays optional.

## [0.1.0-alpha.1] - 2026-09-20

Minecraft **26.3** support. No gameplay, id, tag or config change.

### Added

- **Minecraft 26.3** as a new Stonecutter node on every loader — NeoForge `26.3.0.7-beta`,
  Forge `26.3-66.0.2` and Fabric (fabric-api `0.161.0+26.3`, NeoForm `26.3-1`) — built alongside
  26.1.2 and 26.2, so every release now ships **nine** loader × version jars.

### Changed

- VS Code run/debug configurations (`.vscode/launch.json`, `.vscode/tasks.json`) gain the three
  26.3 cells; the "Build all" task now builds all nine.
- CI (`multiloader.yml`, `publish.yml`) builds, attaches and publishes the 26.3 jars.
- JEI pins moved to the newest published builds on each Minecraft version: `29.40.0.101` (26.1.2), `30.35.0.223` (26.2) and `31.3.0.18` (26.3). Compile-time API only — JEI remains a soft dependency and the shipped jar gains no hard requirement.

### 26.3 port notes

- Build: the shared `common/` Java source is now preprocessed by Stonecutter for every non-active node (`stonecutterProcessCommon`), so common code can carry `//? if >=26.3 {` blocks, and `common/src/main/resources-<mc>` overlay folders are merged over the shared resources for matching nodes (`mergeCommonResources`). The active node still compiles the raw `common/` folder.
- Build plugins aligned with Neroland Core: ModDevGradle `2.0.147` (the older 2.0.141 cannot set up NeoForge 26.3), ForgeGradle `7.0.40`, Stonecutter `0.9.8`.

