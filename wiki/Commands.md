# Commands

All server commands start with `/nscomp`.

## For every player

| Command | What it does |
|---|---|
| `/nscomp recover` | Reclaims your only lost companion as a Capsule, or lists your lost companions (type and name) |
| `/nscomp recover <name or type>` | Reclaims that lost companion. When the server charges for reclaiming, it states the price and you repeat the command with `confirm` at the end: `/nscomp recover <name> confirm` (or `/nscomp recover confirm`) |
| `/nscomp export` | Writes everything NeroCompanion holds about you to `nerocompanion/exports/<your player id>.json` in the world folder. Your data only; deleted after 7 days. Once every `privacy.exportCooldownSeconds` (300) |
| `/nscomp privacy` | Shows your privacy settings |
| `/nscomp privacy names on` / `off` | Show or hide your companions' names to other players |
| `/nscomp privacy chatter on` / `off` | Turn your companions' chatter on or off; off also hides other companions' chatter from you |
| `/nscomp privacy push on` / `off` | Turn NeroLink notifications and alerts about your companions on or off |

## For operators

| Command | What it does |
|---|---|
| `/nscomp admin release <player> <name, type or id>` | Marks one of a player's companions as lost so they can reclaim it (for a companion stuck after its Bay was moved or removed). `player` is an online player's name or a UUID |
| `/nscomp budget` | Server time companions used over the last 100 ticks, in total and per companion |
| `/nscomp types` | The companion types loaded from data packs, and any problems found in their files |
| `/nscomp debug spawn <count> [type]` | Creative only. Spawns `count` companions owned by you (Helper Drones unless you name a type such as `mining_bot`; `mixed` cycles all seven). At most `debug.maxSpawn` (64) per call; ignores the companion limits |
| `/nscomp debug clear` | Creative only. Removes the companions `debug spawn` made this server run (they are not lost and cannot be reclaimed) |
| `/nscomp gallery` | Creative only, nobody else within 96 blocks. Builds the showcase stage with everything the mod adds: every skin, hat, pose and item (it replaces blocks; test worlds only). See [Server Admin](Server-Admin.md#showcase-gallery) |
| `/nscomp gallery clear` | Removes the gallery companions and item frames and puts back the blocks it replaced |

## Client command

| Command | What it does |
|---|---|
| `/nscompgallery capture [time]` | Needs cheats. Rebuilds the gallery at 0, 100, 0 and saves screenshots to `screenshots/nerocompanion/` (noon unless you name a time). Changes the world's daylight and weather game rules |
| `/nscompgallery capture shot <name>` | Saves the current view as one screenshot |

## Related Neroland Core commands

| Command | What it does |
|---|---|
| `/neroland data eraseme` | Erases your data from every Neroland mod, NeroCompanion included |
| `/neroland data erase <uuid>` | Operators: erases a player's data from every Neroland mod |
| `/neroland config reload` | Operators: reloads `config/nerocompanion.properties` |

## Keys

| Key | Default | What it does |
|---|---|---|
| Open companion screen | K | Opens the screen of your companion under the crosshair, or of the one highlighted on the HUD strip |
| Show / hide companion strip | not bound | Toggles the HUD companion strip |
