# Config

Every setting lives in `config/nerocompanion.properties` and reloads with `/neroland config reload`. **Server**
settings are decided by the server and apply to everyone; **client** settings are each player's own.

## Limits and throttling (server)

| Key | Default | Range | What it does |
|---|---|---|---|
| `maxCompanionsPerPlayer` | 4 | 1-64 | Most companions one player may have deployed at once (parked and capsuled ones do not count) |
| `maxActiveBotsPerChunkRegion` | 16 | 1-512 | Most deployed companions of all players in one 8x8-chunk region |
| `throttleDistance` | 32 | 8-256 | Beyond this many blocks from its owner a companion thinks on every 4th tick only |
| `sleepDistance` | 64 | 16-512 | Beyond this many blocks from its owner an idle companion falls asleep |

## Energy (server)

| Key | Default | Range | What it does |
|---|---|---|---|
| `energy.lowBatteryPercent` | 10 | 1-50 | At or below this charge a companion heads home, or sleeps if it has no home in range |
| `energy.returnHomeRange` | 96 | 8-512 | A low-battery companion only goes home when its home is within this many blocks |

## Authority (server)

| Key | Default | What it does |
|---|---|---|
| `pvpBots` | false | Let combat companions attack players (still needs server PvP and only against a player who attacked first) |
| `mining.enabled` | true | Let mining companions break blocks at all |
| `mining.maxJobSize` | 9 (1-16) | Edge length of the largest box one mining job may cover |
| `combat.enabled` | true | Let combat companions attack at all |

## Recovery and death salvage (server)

| Key | Default | Range | What it does |
|---|---|---|---|
| `lostAfterMinutes` | 30 | 1-10080 | A companion unseen this long while its owner is online counts as lost |
| `lostAfterOfflineDays` | 30 | 1-3650 | A companion unseen this many days counts as lost even if its owner never returns |
| `reclaimCost` | 0 | 0-1000000 | Credits a reclaim costs; only charged with an economy mod |
| `salvage.enabled` | true | | Companions gather their owner's dropped items after a death |
| `salvage.carryBotsByDefault` | true | | Carry Bots salvage without a Salvage Chip |
| `salvageWindowTicks` | 1200 | 100-12000 | How long after a death companions keep gathering (20 ticks = 1 second) |
| `salvageHoldMinutes` | 60 | 1-1440 | How long a companion holds salvaged items (counted only while the owner is online) |

## Privacy (server)

| Key | Default | Range | What it does |
|---|---|---|---|
| `privacy.ledgerRetentionDays` | 180 | 1-3650 | Days of inactivity after which a player's companion data is deleted from every store |
| `privacy.allowNames` | true | | Show companion names to other players as nameplates |
| `privacy.exportCooldownSeconds` | 300 | 10-86400 | Wait between two `/nscomp export` requests |

## Looks (server)

| Key | Default | What it does |
|---|---|---|
| `season` | `none` | `none`, `spring`, `summer`, `autumn`, `winter` or `festive`, for seasonal looks. Never read from the real clock |

## Debug (server)

| Key | Default | Range | What it does |
|---|---|---|---|
| `debug.maxSpawn` | 64 | 1-256 | Most test companions one `/nscomp debug spawn` may create |

## Client

| Key | Default | What it does |
|---|---|---|
| `telemetryEnabled` | true | Anonymous crash reports (see [Privacy](Privacy.md)); takes effect on restart. A server never sets it for you |
| `hud.enabled` | true | Draw the companion strip |
| `hud.anchor` | `TOP_LEFT` | `TOP_LEFT`, `TOP_CENTRE`, `TOP_RIGHT`, `MIDDLE_LEFT`, `CENTRE`, `MIDDLE_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_CENTRE` or `BOTTOM_RIGHT` |
| `hud.offsetX` | 0 | Extra horizontal offset in GUI pixels (-2000 to 2000) |
| `hud.offsetY` | 0 | Extra vertical offset in GUI pixels (-2000 to 2000) |
| `hud.showNames` | true | Show names on the strip |
| `hud.compact` | false | Always use the compact strip (no names) |
