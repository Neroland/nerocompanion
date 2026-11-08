# Changelog

All notable changes to **NeroCompanion** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.6.0-beta.1] - 2026-11-08

### Fixed

- **Left-clicks did nothing in any NeroCompanion screen on Minecraft 26.3.** Minecraft 26.3 numbers mouse buttons
  differently, so the Companion screen, the Remote's radial menu, the Skin Studio and the Companion Bay ignored
  left-clicks (the Skin Studio button looked missing because it never responded). Every button works again on
  26.3, and older Minecraft versions are unaffected.
- The Skin Studio keeps the look you just applied selected instead of jumping to a different card.
- Text no longer overlaps or gets cut mid-word: the type line and Energy label on the Companion screen, the Bay's
  column headers and empty-list hint, Skin Studio card names, descriptions and scroll arrows, the radial's job
  description and Recall button, and companion names on the HUD.
- An unnamed companion's title shows its type once ("Helper Drone", not "Helper Drone — Helper Drone").
- Chip hints read "Needs the Guard Protocol Chip." instead of "…Chip chip."
- The Companion Bay only says a parked companion is charging when the Bay has power; otherwise it says it is
  parked without power.
- The Remote's radial menu no longer closes when you let go of the Remote before choosing. Hold the Remote, let
  go, then left-click a job (or the centre for "Come here", or Recall); holding, aiming and letting go still works,
  and so do the number keys. Clicking the job the companion already has now closes the menu instead of doing
  nothing, and a click away from the ring closes it.

### Changed

- The Companion screen has one **Skin Studio** button in place of the separate Skin and Hat buttons. Skins and
  hats are the two tabs of the Studio.
- The showcase gallery moved from `/nscomp gallery [clear]` to `/nerocompanion gallery [clear]` (same rules:
  operators in creative, nobody else within 96 blocks). The client screenshot pass moved from
  `/nscompgallery capture` to `/nerocompanion_capture capture`.
- The gallery now shows everything the mod adds on a stage sized to fit: every skin and every hat on every
  type that may wear it, the poses (asleep, napping, working, charging, energy levels, bond levels) and a back wall
  of every item, Capsule variant and Scrap Egg stage. Display companions stay frozen and no longer count toward
  the companion limits. The capture harness frames the new layout.

## [0.6.0-beta.1] - 2026-11-08

The first content release: seven companions, chips, chargers, recovery, death salvage, personality and bond, skins
and hats, quests, privacy controls and the NeroLink app module. Requires **Neroland Core 1.17.4** or later.

### Requirements

- **Neroland Core 1.17.4** or later (below 2.0). The loader refuses an older Core.
- **Minecraft** 1.21.1, 1.21.4, 1.21.5, 1.21.8 (also runs on 1.21.7), 1.21.10 (also runs on 1.21.9), 1.21.11, 26.1.2,
  26.2 and 26.3, on NeoForge, Forge and Fabric (Fabric also needs Fabric API). Java 21 for 1.21.x, Java 25 for 26.x.
- **Optional**, never required, each one adds something when it is installed (see the Compatibility wiki page):
  NeroEconomy (0.3.0-beta.1 or later; built against 0.5.0-beta.1), NeroColonies (built against 0.6.1-beta.1),
  NeroFactions (0.6.1-beta.1), NeroSecurity (0.5.0-beta.1), NeroLogistics (0.7.0-beta.1), Nerospace (2.0.0-beta.1),
  NeroCreatures, NeroQuests and the NeroLink bridge. Everything works on Neroland Core alone.

### Added

- **Seven companions.** Craft a Helper Drone, Scout Drone, Mining Bot, Carry Bot, Repair Bot or Combat Bot as a
  new Capsule (glass, a Blank Chip, iron, redstone and one reagent per type) and use it on a block to activate it.
  The Scrapling hatches from a **Scrap Egg** incubated for 5 minutes on a Charging Pad or in a Companion Bay.
  - Helper Drone: follows and perches on your right shoulder, picks up what you can't carry (8 slots), scouts,
    lights the way with a Lamp chip.
  - Scout Drone: flies ahead and marks ores and hostile mobs for you only; warns you when a hostile is behind you.
  - Mining Bot: mark an area with the Remote (sneak-use two corners, up to 9 x 9 x 9 by default) and it digs it
    out, block by block, only where you may build, and delivers the drops.
  - Carry Bot: picks up dropped items and delivers them to a container you bind with the Remote (sneak-use it).
  - Repair Bot: mends your worn armour and hotbar items for energy.
  - Combat Bot: guards you (or its post in Stay) against hostile mobs; fragile, and heals only on a Charging Pad.
  - Scrapling: a pet that follows, perches, fetches your Fetch Ball, plays and naps on its pad. It never fights.
- **Modes**: Follow, Stay, Guard, Mine, Carry, Repair, Scout and Sleep, each offered only by the companions that
  can do it. Companions sleep (no thinking, no battery use) when their owner is offline, in another dimension or
  far away, and head home to charge at a low battery.
- **Chips**: 18 kinds in 30 items, crafted from a cheap Blank Chip plus one reagent each. Six stat families in
  tiers I-III (Speed, Efficiency, Range, Capacity, Armour, Vitality), eight capability chips (Guard Protocol,
  Ore Filter, Auto-Deposit, Cargo Rack, Lamp, Salvage, Silent Running, Radiation Shielding) and four cosmetic
  chips (Chatterbox, Hush, Emote Pack, Sparkle Trail). Hold a chip and click a slot on the Companion screen, or
  right-click your companion with it. Speed, Efficiency, Range and Capacity chips count as Neroland Core upgrade
  modules. Some chips cannot be fitted together (Lamp and Silent Running, Chatterbox and Hush, Speed and Armour);
  packs can change that list with `data/<namespace>/chip_rules/exclusive.json`.
- **Charging Pad**: takes energy from any side and charges the companion standing on it (400 FE/t) and up to four
  more within 3 blocks (100 FE/t each).
- **Companion Bay**: park up to four companions (they sleep inside, charge at 200 FE/t each and stop counting
  toward your limit); the Bay becomes their home, and its ports show each parked companion's Capsule. Breaking a
  Bay hands every parked companion back as a Capsule.
- **Hand Crank Charger**: right-click to crank 60 FE into the companion standing on it. Needs no power mod.
- **Companion Remote**: tap your companion for its screen, hold for the job radial, sneak-use to recall it.
  **Fetch Ball**: a throwable toy for pet companions.
- **Screens**: the Companion screen (name, job, energy, bond, chips, look, recall), the Remote's job radial, the
  Bay screen, the Skin Studio, and the HUD companion strip (`hud.*` options; keys "Open companion screen" (K) and
  "Show / hide companion strip").
- **Personality and bond.** Every companion has one of six traits (Curious, Grumpy, Loyal, Show-off, Shy, Sleepy)
  that shape its idle behaviour and chatter (speech bubbles). Time together, pats and finished jobs raise its bond
  (levels 1-4, cosmetic only); level 3 adds a trick and a heart by its name, level 4 a coloured nameplate.
- **Recovery.** The server keeps a recovery ledger of every companion: out in the world, in a Capsule, parked or
  lost. A companion that is killed, or stays unseen in an unloaded area for `lostAfterMinutes` (30) while you are
  online, is **lost**: reclaim it from any Companion Bay's Lost list or with `/nscomp recover [name]` as a Capsule
  with its chips, bond, name and looks (its cargo stays where it was). Reclaiming is free unless the server sets
  `reclaimCost` and runs an economy mod (`/nscomp recover` then asks you to confirm the price). A companion unseen
  for `lostAfterOfflineDays` (30) is lost even if its owner never returns. Operators can release a stuck companion
  with `/nscomp admin release`. The companion limits are counted from the ledger, so they hold across restarts, and
  a copied Capsule can never bring out a second copy of a companion.
- **Death salvage.** Carry Bots, and any companion with a Salvage Chip, near where you die gather your dropped
  items for a minute (`salvageWindowTicks`) and hold them for you; talk to the companion within
  `salvageHoldMinutes` (60) to get them back, or they drop where it is. Never with `keepInventory`, never another
  player's drops; `salvage.enabled` turns it off. While your salvage window is open, other players' Carry Bots and
  drones leave your dropped items alone, even on servers that turn salvage off.
- **Art, animation and sound.** Every companion has its own model, textures and animations (hover, walking, head
  look, work loops, sleep, perch and charging poses, pet and emote bounces, trait tricks) and eyes that change
  colour with the battery. 30 original sounds with subtitles and five particles. The Capsule shows which companion
  is inside; the Scrap Egg cracks as it incubates.
- **Skins and hats.** 10 unlockable skins (including the Aurora skin for every type, a quest reward) and 10 hats,
  chosen in the Skin Studio with a live preview. Each locked look says how to unlock it: a bond level, an
  advancement, an item used on it (Hazard Decal, Scrap Shell, Hard Hat, Sun Visor, Headlamp), a quest, a shop
  price (with NeroEconomy) or the server's `season` setting. Looks never change what a companion can do. Skins and
  hats are data (`data/<namespace>/skins/`, `hats/`), so packs can add their own.
- **Companion types are data.** `data/<namespace>/companion_types/*.json` sets each chassis' stats, battery,
  modes, chip slots and work goals; `/nscomp types` lists what loaded and any problems in the files.
- **Companion Log quests.** The NeroQuests chapter now has 14 quests that follow what you actually do: deploy,
  charge, fit a chip, mine, deliver, repair, guard, hatch a Scrapling, reach bond level 3, reclaim a lost companion
  and fill out a squad. Each is credited to the companion's owner when it happens, even if they are offline.
- **Works alongside the other Neroland mods, all optional.** With Nerospace: flying companions fly a little faster
  on light worlds and slower on heavy ones, and radiation powers a companion down unless it carries the Radiation
  Shielding chip. With NeroLogistics: carry and mining bots can deliver into a network drive bay. With
  NeroEconomy: shop skins can be bought in the Skin Studio and stay unlocked, and `reclaimCost` can be charged.
  With NeroColonies or NeroSecurity: companions never break, open or enter where their owner could not. With
  NeroFactions: a combat bot never attacks a member of its owner's faction. With NeroCreatures: taming a creature
  counts for the *Pet Adopted* quest.
- **NeroLink companion app.** With the NeroLink bridge installed, the app shows your own companions (type, name,
  mode, battery, bond, look, a rough "near you / at home / away" hint and the dimension name, never coordinates),
  your lost and parked ones, your salvage hold and the Bays they call home. From the app you can change a
  companion's mode, recall it to its home Bay, reclaim a lost one into its home Bay and rename it (same filter and
  limit as in game; at most 10 actions a minute). Push notifications for low battery, lost, back, salvage held and
  bond levels 2-4 are off by default and at most one per kind per companion a minute.
- **Alerts** for a low battery, a lost companion, a companion that is back, held salvage and bond levels go to
  Neroland Core's alert list. They name the companion's type and name only, never where it is.
- **Privacy controls.** `/nscomp export` writes everything the mod holds about you to one file in the world folder
  (your data only, deleted after 7 days). `/nscomp privacy names|chatter|push on|off` lets each player hide their
  companions' names from others, turn chatter off and turn push notifications and alerts off; `privacy.allowNames`
  hides every name server-wide. A player who has not played for `privacy.ledgerRetentionDays` (default 180) has all
  their companion data deleted, in every store and its backup; a partly failed clean-up is retried a minute later.
  Neroland Core's erasure command removes a player's companion data along with everything else. Error reports
  also mask player ids, names, positions and world names. See PRIVACY.md.
- **Showcase gallery** for test worlds: `/nscomp gallery [clear]` (operators in creative) and the client
  `/nscompgallery capture` screenshot pass.
- Creative-only spawn eggs for every companion type; operator commands `/nscomp budget`, `/nscomp types` and
  `/nscomp debug spawn <n> [type|mixed]` / `/nscomp debug clear` for measuring server cost.

### Changed

- Now requires Neroland Core **1.17.4** (was 1.17.0).
- A Capsule item carries only the shareable part of its companion. The owner, home position and job data wait on
  the server until the Capsule is deployed, so a Capsule seen in someone's hand reveals nothing personal.
- The Companion Log chapter grew from 6 to 14 quests; the *Blueprint* quest asks for a crafted Capsule.
- On Forge, the mod now asks for at least the Forge build it was built against. An older Forge build of the same
  Minecraft version shows an "update Forge" message instead of crashing while the game starts.
- The Helper Drone's item pickup now checks claims like the Carry Bot does: it never collects where you could not go.
- A Scrap Egg can only be set on a Charging Pad or Companion Bay you are allowed to use. Someone else's egg on a Pad
  or Bay you may use can be popped out (sneak-use twice with an empty hand); it keeps its progress.
- If NeroColonies, NeroSecurity or NeroFactions is installed but cannot be reached (for example after an
  incompatible update), companions play safe: they do not break, place, open or attack where that mod would have
  decided, and the log says so once.

### Not in this release

These were planned and are left for a later version:

- A through-wall outline for scout marks (marks are particles on the marked blocks).
- An unbind button for a carry bot's container on the Companion screen (bind another container to replace it).
- A button to make a companion show its trick (it plays on its own while idling at bond 3+).
- Double chests as carry targets (one half is used).
- A glowing bond-4 nameplate (level 4 colours the name instead).
- NeroCreatures pets in the NeroLink app, and companion features for them (perching, fetch, Bay parking,
  recovery, hats).
- Carry and mining bots as NeroLogistics network endpoints (they deliver into a drive bay only).
- Wireless charging from Nerotech (Pads take Nerotech energy by cable like any other source).
- Drone fleets, programmable behaviour and a drone camera.
- Scrapling breeding or evolution.
- Repair-material, ranged-attack and melee-tier chips (combat bots fight at one tier).
- Remote recall and reclaim from the app need the companion's home Bay; they never put an item into your inventory.

### Known limitations

- On Fabric the Pad and Bay connect only to Neroland Core-compatible energy sources (Fabric has no common FE
  standard yet); the Hand Crank works everywhere.
- Companions respect vanilla protection, Neroland Core gates and the optional Neroland mods above. Third-party
  claim mods are not consulted.
- A companion that cannot find a path to you inside its leash may stay behind until you come back or it
  teleports when you move further away.
- Companions that travel through a portal keep working; a mod that re-creates entities with new ids on teleport
  can make the arrived copy count as lost.
- Bukkit/Paper hybrid servers are not supported.

## [0.5.1-alpha.2] - 2026-10-31

### Changed

- Release uploads now target CurseForge and GitHub Releases only.
- Removed automatic project description and gallery uploads.
- Renamed the website overview source to `art/description.md` and refreshed public documentation links.

## [0.5.0-alpha.1] - 2026-10-30

Builds for Minecraft 1.21.x. Requires Neroland Core 1.17.0.

### Added

- **Minecraft 1.21.x builds** on NeoForge, Fabric and Forge, next to the 26.1.2, 26.2 and 26.3 builds: one
  build each for 1.21.1, 1.21.4, 1.21.5, 1.21.8 (also runs on 1.21.7), 1.21.10 (also runs on 1.21.9) and
  1.21.11. The 1.21.x builds need Java 21. Everything works the same on every version; the optional NeroEconomy link needs
  NeroEconomy 0.5.0-beta.1 or later.

### Changed

- Now requires Neroland Core 1.17.0 or later.
- The optional NeroEconomy link is compiled against NeroEconomy 0.5.0-beta.1.

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

