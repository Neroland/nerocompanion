# Charging & Bays

Companions run on Forge Energy (FE). Three blocks charge them.

## Hand Crank Charger

Stick on top; copper ingot, piston, copper ingot; three iron ingots. Stand a companion on it and right-click: each
turn puts **60 FE** into it (one turn every quarter second, and cranking makes you hungry). Needs no power at all,
so it works on any server.

## Charging Pad

Redstone, copper ingot, redstone over three iron ingots. Takes energy from **any side** and charges:

- the companion standing on it at **400 FE/t**, and
- up to four more companions within **3 blocks** at **100 FE/t** each.

A ring shows while it is charging. A Pad becomes the home of a companion charged on it: a companion with a low
battery flies or walks back to it. A Combat Bot on a powered Pad heals 1 health a second. Scrap Eggs incubate on
it (sneak-use the egg).

## Companion Bay

Iron ingot, glass, iron ingot; Charging Pad, iron block, Charging Pad; iron ingot, block of redstone, iron ingot.

- **Park** up to four of your companions within 8 blocks from the Bay screen. A parked companion sleeps inside,
  charges at **200 FE/t**, and does not count toward your companion limit. Its Capsule shows in a port on the front.
- **Unpark** sends it out again (if you are under your limit).
- The Bay becomes the parked companion's **home**; the NeroLink app can recall a companion to it or reclaim a lost
  one into it.
- The **Lost** list shows your lost companions; **Reclaim** gives one back as a Capsule. See
  [Recovery & Death Salvage](Recovery-and-Death-Salvage.md).
- Breaking a Bay hands every parked companion back as a Capsule.
- Other players' parked companions show as a grey card; only their owner can take them out.

## Power sources

The Pad and Bay accept Neroland Core energy, so anything Core can connect to charges them (Nerotech and NeroPower
generators, and other mods' FE generators on NeoForge and Forge). On Fabric only Core-compatible sources connect.
Without power, parked companions rest but do not charge.
