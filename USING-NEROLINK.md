# Using NeroLink

NeroCompanion registers a link module with Neroland Core's `NeroLinkRegistry`. The **NeroLink** bridge mod serves
it to the NeroLink app; without the bridge the module is registered but nobody reads it. NeroCompanion needs no
NeroLink classes: it depends only on Core's `link` API. The module is registered last in init, inside a
try/catch, so a link problem never stops the mod loading.

- **Module id:** `nerocompanion`
- **Scope:** every section, action and event is about the **requesting player's own companions**, found by the
  owner UUID stored on each companion. Nothing about any other player, never a server-wide list, never coordinates.
- **Requires:** Core 1.17.4; works with the NeroLink bridge 1.2.2 or later as it ships (no bridge change needed).

## Snapshot sections

| Section | Contents |
|---|---|
| `roster` | `{asOf, companions:[...]}`. Per companion: `id` (the companion id, never the entity UUID), `type` (`drone`, `bot` or `pet`), `chassis` (e.g. `nerocompanion:mining_bot`), `name`, `skin`, `hat`, `mode`, `supportedModes`, `charge` (percent) and `chargeMax` (100), `bond` (0-4), `home` (`bay`, `pad` or `none`), `parked`, `lost`, `dimension` (display name) and `areaHint` (`near_you`, `at_home`, `away` or `unknown`). Includes parked and lost companions. |
| `salvage` | `{held, count, expiresAt}`: whether a companion holds your death drops, how many items and when the hold ends. No item list. |
| `bay` | `{bays:[{index, slots:[{companionId, type, chassis, name, charge}]}]}`: the Companion Bays your companions call home, numbered 1..n. No positions. |

Names are the stored, already filtered names. A player who hid their companions' names from others still sees
them here, because it is their own data. The roster is not pushed as live updates; the app polls it.

## Action `companion`

Body: `{requestId, companionId, op, mode?, name?}`, at most 2 KiB.

| `op` | Does | Needs |
|---|---|---|
| `set_mode` | Changes the companion's mode (`mode`) | Owner online, companion out and loaded, mode supported (chassis modes plus modes a fitted chip unlocks) |
| `recall` | Parks the companion in its home Companion Bay | Companion out and loaded; a free slot in the Bay at its home, which must be loaded. Works with the owner offline. Never puts an item into an inventory |
| `reclaim` | Brings a lost companion back, parked in its home Bay | Companion lost; a free slot in its loaded home Bay. Works offline. Charges `reclaimCost` if set (refunded if the park fails) |
| `rename` | Renames it (`name`) | Owner online; same text filter and 24-character limit as in game. When the filter answers later the result is `pending`, and a refusal is told to the player in game |

Every action checks that the companion belongs to the requester. At most **10 actions a minute** per player,
shared with the in-game action limit. Refusals carry a short reason token, for example `not_owner`, `not_live`,
`not_lost`, `not_loaded`, `no_bay`, `owner_offline`, `rate_limited`, `unknown_mode`, `filtered`, `same_name` or
`reclaim_cost`.

## Events and push

Topic `events`. Five kinds, each published once with a `_notify` block (`title`, `body`, `deepLink` to
`nerocompanion/<companionId>`) and then raised as a Neroland Core alert:

| `type` | When |
|---|---|
| `LOW_BATTERY` | Once per discharge, at the low-battery threshold |
| `LOST` | A companion becomes lost |
| `RECOVERED` | A lost companion is back |
| `SALVAGE_HELD` | A companion picked up your things after you died (`minutesLeft` included) |
| `BOND_LEVEL` | Bond reaches level 2, 3 or 4 (`level` included) |

Payload: `type`, `at`, `companionId`, `chassis` and the extra field above. Push text names only the companion's
type and the name its owner gave it (plus the minutes left for salvage): never a position, dimension, id or another
player's name. At most one event per kind per companion each minute; inside that window nothing is published.

**Push is off by default.** The app's *Companions* notification category starts off and is the master switch. In
game, `/nscomp privacy push off` stops NeroCompanion publishing these events **and** raising the matching Core
alerts for that player, so nothing reaches any of their devices; the roster still works. `/nscomp privacy push on`
turns them back on.

## Privacy

The module reads what the mod already stores; it adds no new stored data. `/nscomp export` includes what the app
can see. See [PRIVACY.md](PRIVACY.md) and the wiki's NeroLink page.

## Not in this release

- NeroCreatures pets are not listed in the roster.
- App deep links currently open the app's companion list rather than the single companion.
