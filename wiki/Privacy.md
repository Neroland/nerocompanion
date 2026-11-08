# Privacy

NeroCompanion keeps only what it needs to run your companions, on the server, and lets you see it, export it and
have it deleted. The full policy is in
[PRIVACY.md](https://github.com/Neroland/nerocompanion/blob/main/PRIVACY.md).

## What the server stores about you

- Which companions are yours (by your player id), their state (out, in a Capsule, parked, lost) and their last-known
  position, home Pad or Bay and bound container.
- Death salvage a companion holds for you, Scrap Eggs you are incubating, and your three privacy settings.
- The names you give your companions. Bond, traits and chatter contain no player data.

Nothing is written to logs about you, and notifications and alerts never contain a position.

## Your controls

| Command | What it does |
|---|---|
| `/nscomp privacy names off` | Other players never see your companions' names (you still do) |
| `/nscomp privacy chatter off` | Your companions stop chattering, and you stop seeing other companions' chatter |
| `/nscomp privacy push off` | No NeroLink notifications or alerts about your companions |
| `/nscomp export` | One file with everything NeroCompanion holds about you |
| `/neroland data eraseme` | Erases your data from every Neroland mod |

`on` turns each setting back on; `/nscomp privacy` shows them.

## Erasure and retention

- After an erasure, your companions in loaded areas and Bays become ownerless Capsules and their names are cleared.
  Companions in unloaded areas are handled when the area loads.
- If you have not played for `privacy.ledgerRetentionDays` (180 by default), all your companion data is deleted.
- Backups of the data files are refreshed straight after a deletion, so deleted data does not survive in a backup.
- Export files are deleted after 7 days, or at once if you are erased.

## Crash reports

NeroCompanion sends anonymous crash reports (stack trace, mod and game versions; never your IP, username, player id,
world data or chat) unless you set `telemetryEnabled=false` in your own `config/nerocompanion.properties`.
