# Combat Bot

A small walking turret that guards you. Crafted from glass, a Blank Chip, 2 iron ingots, redstone, an iron sword
and a shield.

- **Battery** 40 000 FE. **Health** 12, no natural healing. **Attack** 4. **Chip slots** 3.
- **Modes**: Follow, Stay, Guard, Sleep.

## What it does

- **Guard**: watches about 12 blocks around you (or around the spot where it stands in Stay) and fights hostile
  mobs that come near. Each hit costs 150 FE. Neutral mobs are left alone until they turn hostile.
- It never attacks you, your team, tamed animals, villagers, golems, other companions or other players. A server
  can allow it to fight players with `pvpBots=true`, and then only when server PvP is on and only a player who
  attacked you or the bot first. With NeroFactions it never attacks a member of your faction.
- It is **fragile**: it does not heal on its own. Standing on a powered Charging Pad heals it 1 health a second.
  Recalling it does not heal it.
- It gives up a fight that drags it too far from you, and drops it when you change dimension.
- Kills earn no experience and no rare drops, so it is no use for mob farms.

The **Guard Protocol** chip lets any other companion take the Guard job; one without an attack only keeps watch.
Servers can turn combat off with `combat.enabled=false`.
