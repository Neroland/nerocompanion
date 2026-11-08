# Carry Bot

A walking bot with a big tub. Crafted from glass, a Blank Chip, 2 iron ingots, redstone and a chest.

- **Battery** 36 000 FE. **Health** 20. **Chip slots** 3. **Cargo** 27 slots (36 with a Cargo Rack chip).
- **Modes**: Follow, Stay, Carry, Sleep.

## What it does

- **Bind a container**: sneak-use the Remote on a chest, barrel or other container you may open. Binding another
  container replaces it. With NeroLogistics installed, a network drive bay works as well.
- **Carry**: picks up dropped items nearby (yours, including ones you toss, and items from your Mining Bot) and
  delivers them to the bound container. It never takes items another player threw, and it leaves another
  player's death drops alone for the salvage window (see
  [Recovery & Death Salvage](Recovery-and-Death-Salvage.md)).
- With an **Auto-Deposit** chip it empties itself into the container when it fills up, without being told.
- If the container is full or locked it tells you once and holds on to the items.
- Recall it, and what it carries goes into your inventory (or lands at your feet); the Capsule holds no items.
- **Death salvage**: a Carry Bot near where you die gathers your dropped items and holds them for you. See
  [Recovery & Death Salvage](Recovery-and-Death-Salvage.md).
