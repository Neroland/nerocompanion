# Mining Bot

A walking bot that digs out an area you mark. Crafted from glass, a Blank Chip, 2 iron ingots, redstone and an iron
pickaxe.

- **Battery** 48 000 FE. **Health** 20. **Chip slots** 3. **Cargo** 9 slots.
- **Modes**: Follow, Stay, Mine, Sleep.

## Marking an area

Hold the **Remote** and sneak-use two blocks: the two corners of a box. The largest box is
`mining.maxJobSize` blocks along each edge (9 by default); a bigger selection is cut down and the message says so.
Then pick **Mine** from the radial.

## What it does

- Digs the box out block by block, and only where **you** may break blocks: spawn protection,
  claims from supported Neroland mods and the `#nerocompanion:unmineable` block tag are left alone, and so are
  chests, furnaces, beds, signs and other blocks with contents. It does not dig next to lava.
- Drops go to its own cargo, then to a Carry Bot of yours nearby, then to the container you bound with the Remote,
  then to you.
- It pauses when you move more than 64 blocks away or change dimension, and carries on when you come back. It
  gives up on blocks it cannot reach and tells you.
- When the job is done it says how many blocks it dug, climbs out and goes back to **Follow**.
- An **Ore Filter** chip keeps only ore drops. Speed and Range chips help too.

Servers can turn mining off with `mining.enabled=false`.
