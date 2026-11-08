# Server Admin

Notes for people running a server with NeroCompanion. Every setting is listed in [Config](Config.md) and every
command in [Commands](Commands.md). All settings live in `config/nerocompanion.properties`
and can be reloaded with `/neroland config reload`. Every gameplay setting is decided by the server.

## Companion limits

| Setting | Default | What it does |
|---|---|---|
| `maxCompanionsPerPlayer` | 4 | Most companions one player may have deployed at once. Companions stored in a Capsule do not count. |
| `maxActiveBotsPerChunkRegion` | 16 | Most deployed companions of all players in one 8x8-chunk area. Deploying into a full area is refused. |
| `throttleDistance` | 32 | Beyond this many blocks from its owner a companion thinks less often. |
| `sleepDistance` | 64 | Beyond this many blocks from its owner an idle companion falls asleep. |

Both limits are counted from the recovery ledger (below), so they hold across server restarts. The area limit counts
only companions seen in the last `lostAfterMinutes`, so companions left in unloaded areas (for example by players who
left the server) do not fill an area for good.

Companions also fall asleep (no thinking, no battery use) when their owner is offline or in another dimension.

## What bots may do: PvP, claims and protection

A companion acts with its **owner's** permissions, checked by the owner's player id for every block it breaks or
places, every container it opens, every area it enters and every attack. An offline owner's companions sleep and
never act.

| Setting | Default | What it does |
|---|---|---|
| `pvpBots` | false | Let Combat Bots attack players. Even when on, server PvP must be on, and a bot only fights a player who attacked its owner or the bot first; never its owner's team or a creative player. |
| `combat.enabled` | true | Let combat companions attack at all. |
| `mining.enabled` | true | Let Mining Bots break blocks at all. |
| `mining.maxJobSize` | 9 | Edge length of the largest box one mining job may cover (1-16). |

Checked every time: spawn protection, adventure mode, locked containers, the `#nerocompanion:unmineable` block tag,
Neroland Core gates, and, when installed, NeroColonies colony rules, NeroSecurity access rules and NeroFactions
membership (a bot never attacks its owner's faction). Other claim mods are not consulted. Villagers, golems,
tamed animals and other companions are never attacked; add more mobs with the `#nerocompanion:protected_mobs`
entity tag.

## Measuring server cost

`/nscomp budget` reports how much server time companions used over the last 100 ticks, in total and per companion.
To probe a test world: in creative, `/nscomp debug spawn 40` (Helper Drones) or `/nscomp debug spawn 7 mixed` (one
of each type), wait about 30 seconds near them, run `/nscomp budget`, then `/nscomp debug clear`. Companions far
from their owner think less often and sleep (`throttleDistance`, `sleepDistance`), and mining pauses beyond 64
blocks, so a busy server mostly pays for companions near online players.

## Recovery and death salvage

| Setting | Default | What it does |
|---|---|---|
| `lostAfterMinutes` | 30 | A deployed companion unseen this long (its area unloaded) while its owner is online counts as lost. Killed companions are lost at once. |
| `lostAfterOfflineDays` | 30 | A deployed companion unseen this many days counts as lost even if its owner never comes back. |
| `reclaimCost` | 0 | Credits a reclaim costs. Only charged when an economy mod provides Neroland Core currency; otherwise reclaiming is free. |
| `salvage.enabled` | true | Companions gather their owner's dropped items after the owner dies. Never with `keepInventory`. |
| `salvage.carryBotsByDefault` | true | Carry Bots salvage without a Salvage Chip. |
| `salvageWindowTicks` | 1200 | How long after a death companions keep gathering (20 ticks = 1 second). |
| `salvageHoldMinutes` | 60 | How long a companion holds salvaged items before dropping them where it is. |

The server keeps one **recovery ledger** entry per companion: whose it is, whether it is out, in a Capsule, parked
or lost, and its last-known position. Each companion can only be in one place: a copied Capsule, or an old copy of a
companion that loads after it was reclaimed, is refused or removed (anything the old copy carried drops where it
stood). Salvage only gathers items that fell when the owner died, and never more of a stack than fell: items another
player dropped that merge into the pile stay on the ground. If two players die at the same spot at the same moment,
neither's items are gathered (they stay on the ground as usual). Held salvage drops at the companion only while its
owner is online. A companion whose saved data cannot be read stays asleep where it is and may show as lost in the
Bay; reclaiming it is safe (the unreadable copy is removed when a later version can read it).

## Privacy

NeroCompanion keeps only what it needs to run companions, on the server. [PRIVACY.md](https://github.com/Neroland/nerocompanion/blob/main/PRIVACY.md)
lists every piece of player data, how long it is kept and how it is removed.

| Setting | Default | What it does |
|---|---|---|
| `privacy.ledgerRetentionDays` | 180 | A player who has not been on the server for this many days has all their companion data deleted (ledger, Capsule homes, salvage, incubating eggs, privacy settings). Their companions in loaded areas and loaded Bays become ownerless Capsules; companions in unloaded areas register to them again if that area ever loads. The same limit removes a single ledger entry for a companion in a Capsule or lost, a Capsule's stored home, an unclaimed salvage hold and an erasure marker that nobody used for that long. |
| `privacy.allowNames` | true | Show companion names to other players as nameplates. Set `false` and no companion name is ever sent to another player; owners still see their own companions' names on the HUD, screens and Remote. |
| `privacy.exportCooldownSeconds` | 300 | How long a player waits between two `/nscomp export` requests. |

The retention check runs shortly after the server starts and then every 20 minutes. If part of it fails (for
example a full disk), it keeps what it could not delete and tries again a minute later. Each store's backup copy is
refreshed straight after rows are deleted, so deleted data does not survive in a backup.

Players can change their own settings with `/nscomp privacy` (below). Erasure goes through Neroland Core's shared
command, which removes a player's data from every Neroland mod at once.

## Commands

- `/nscomp export` (every player) - writes everything NeroCompanion holds about you to one file,
  `nerocompanion/exports/<your player id>.json` in the world folder. Only your own data is in it. The server
  operator hands the file over; it is deleted after 7 days, and at once if you ask to be erased.
- `/nscomp privacy` (every player) - shows your privacy settings. `/nscomp privacy names off` hides your companions'
  names from other players (no nameplate); `/nscomp privacy chatter off` silences your companions and hides other
  companions' chatter from you; `/nscomp privacy push off` stops push notifications about your companions. Use `on`
  to turn each back on.
- `/nscomp recover [name]` (every player) - reclaim one of your own lost companions as a Capsule, by name or type;
  with no name it reclaims your only lost companion, or lists them. When reclaiming costs credits, the command says
  how much and needs `confirm` at the end.

The rest need operator permission:

- `/nscomp admin release <player> <name|type|id>` - mark one of a player's companions as lost so they can reclaim
  it, for a companion stuck after a Bay was moved or removed by another mod. `player` is an online player's name or
  a UUID.

- `/nscomp budget` - how much server time companions used over the last 100 ticks, in total and per companion.
- `/nscomp types` - the companion types loaded from data packs, and any problems found in their files.
- `/nscomp debug spawn <n> [type]` and `/nscomp debug clear` - for measuring server cost: spawn companions owned
  by you (personal drones unless you name a type; `mixed` cycles all seven), and remove the ones it spawned again
  (until the server restarts). You must be in creative mode. One call spawns at most `debug.maxSpawn` (default
  64); there is no total limit, so clear them when you are done. Debug spawns ignore the companion limits.

## Looks: skins and hats

Skins and hats only change how a companion looks; they never change what it can do. They are data:
`data/<namespace>/skins/<type>/<name>.json` (or `skins/<name>.json` with `"types": ["*"]` for one look on every type)
and `data/<namespace>/hats/<name>.json`, so a data pack can add or replace them and `/reload` sends the new list to
everyone. A player unlocks a look per companion by the rule in its file: always, a bond level, an advancement, an
item used up in the Skin Studio, a quest reward, or a season.

| Setting | Default | What it does |
|---|---|---|
| `season` | `none` | The server's season for seasonal looks: `none`, `spring`, `summer`, `autumn`, `winter` or `festive`. It is never read from the real clock; set it yourself (for example `festive` unlocks the Party Cone, `winter` the Snowdrift skin). |

Looks sold for credits need an economy mod and stay locked without one.

## Showcase gallery

- `/nscomp gallery` (operators, creative mode, nobody else within 96 blocks) builds a stage east and south of you
  showing everything the mod adds. The stage is sized to fit its contents (about 40 x 60 blocks with the shipped
  skins, hats and items; it grows if a data pack adds more), and nothing is left out: if anything cannot be placed
  the reply says how many. It **replaces blocks** (the floor, the air above it and a back wall), so use it in a test
  world. It refuses if a chest or any other block with contents is in the way. On show, one row or more each:
  - every companion type in its standard look;
  - every type skin, including seasonal, shop and other locked ones, whether or not you have unlocked them;
  - the Aurora family skin on every type;
  - every hat on every type that may wear it;
  - poses: asleep (every type), the Scrapling's nap, the work animation of every type that has one, a companion
    charging on a Charging Pad, the energy levels that change the eye colour (100, 50, 20, 10 and 0 %) and bond
    levels 0 to 4 (their nameplates show);
  - a Charging Pad, a Companion Bay and a Hand Crank Charger at the west end;
  - on the back wall, in item frames with a sign under each group: every NeroCompanion item, a filled Capsule and a
    blueprint Capsule for every type, and the three Scrap Egg stages.

  A **perched** companion is not shown: perching pins a companion to a player's shoulder, so it cannot be staged.
  The companions are display companions: yours, frozen in place (no AI, no battery drain), never recorded in the
  Recovery Ledger and never counted toward the companion limits. You cannot open their screen, recall them or hurt
  them.
- `/nscomp gallery clear` removes the gallery's companions and item frames and puts back the blocks it replaced.
  The record of what was replaced lasts until the server stops; a display companion that loads after that (or after
  its gallery was cleared while its chunk was unloaded) removes itself.
- `/nscompgallery capture [time]` (client command, needs cheats) teleports you to 0, 100, 0, rebuilds the gallery
  there and saves screenshots to `screenshots/nerocompanion/`: each companion row in pieces of four, the chargers,
  each item-wall group, the whole wall and an overview; `/nscompgallery capture shot <name>` saves the
  current view. **Side effects it leaves behind:** the daylight and weather cycle game rules are switched off
  (`advance_time` / `advance_weather` on 26.x, `doDaylightCycle` / `doWeatherCycle` on 1.21.x), the time is set
  (noon by default) and the weather cleared. Turn the game rules back on afterwards if the world is not a test
  world. Your HUD, clouds, view bobbing and flight are restored when the run ends.

## Chargers and chips

- The **Charging Pad** and the **Companion Bay** take Neroland Core energy on every side. On Fabric only
  Core-compatible energy sources connect; the **Hand Crank Charger** needs no power at all.
- Which chips cannot be fitted together is data: `data/<namespace>/chip_rules/exclusive.json` with
  `{"pairs": [["nerocompanion:lamp", "nerocompanion:silent_running"]]}`. Every namespace's file is merged, and
  `/reload` picks up a change. Unknown chip ids are ignored with a warning in the log.
- Client options for the HUD companion strip (each player's own `config/nerocompanion.properties`):
  `hud.enabled`, `hud.anchor` (`TOP_LEFT` by default; any of the nine screen anchors), `hud.offsetX`,
  `hud.offsetY`, `hud.showNames` and `hud.compact`.

## Hybrid servers

Bukkit/Paper hybrid servers are not supported. If such a server refuses to open a NeroCompanion screen, the
screen simply does not open (the player is told) instead of the server crashing.
