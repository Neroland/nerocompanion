# Companions

Seven companion types ship with NeroCompanion. Each one lives in a **Companion Capsule** when it is not out: use the
Capsule on a block to deploy it, and use an empty Capsule (or sneak-use the Remote) on it to recall it. A recalled
companion keeps everything: name, chips, bond, look, energy, health and home.

| Companion | Moves | Battery | Chip slots | Modes | Page |
|---|---|---|---|---|---|
| Helper Drone | flies | 24 000 FE | 2 | Follow, Stay, Carry, Scout, Sleep | [Helper Drone](Helper-Drone.md) |
| Scout Drone | flies | 20 000 FE | 2 | Follow, Stay, Scout, Sleep | [Scout Drone](Scout-Drone.md) |
| Mining Bot | walks | 48 000 FE | 3 | Follow, Stay, Mine, Sleep | [Mining Bot](Mining-Bot.md) |
| Carry Bot | walks | 36 000 FE | 3 | Follow, Stay, Carry, Sleep | [Carry Bot](Carry-Bot.md) |
| Repair Bot | walks | 36 000 FE | 3 | Follow, Stay, Repair, Sleep | [Repair Bot](Repair-Bot.md) |
| Combat Bot | walks | 40 000 FE | 3 | Follow, Stay, Guard, Sleep | [Combat Bot](Combat-Bot.md) |
| Scrapling | walks | 6 000 FE | 1 (cosmetic) | Follow, Stay, Sleep | [Scrapling](Scrapling.md) |

The Guard Protocol chip adds Guard to any companion (one with no attack only keeps watch).

## Crafting

Every bot and drone Capsule is shapeless: **glass + Blank Chip + iron + redstone + the type's reagent**.

| Companion | Iron ingots | Reagent |
|---|---|---|
| Helper Drone | 2 | feather |
| Scout Drone | 1 | feather and spyglass |
| Mining Bot | 2 | iron pickaxe |
| Carry Bot | 2 | chest |
| Repair Bot | 2 | grindstone |
| Combat Bot | 2 | iron sword and shield |

The Scrapling is hatched, not crafted: see [Scrapling](Scrapling.md). An empty Capsule (glass over iron, redstone,
iron, with iron below) only recalls companions.

## Rules every companion follows

- It belongs to the player who deployed it. Only the owner can open its screen, change its job, rename it, fit
  chips or recall it.
- It acts with its owner's permissions and never more: it does not break, open, place or attack anything its
  owner could not. See [Server Admin](Server-Admin.md).
- It sleeps (no thinking, no battery use) while its owner is offline, in another dimension or far away.
- A server limits how many companions each player has out at once (`maxCompanionsPerPlayer`, 4 by default).
  Capsuled and parked companions do not count.
- Killed or left behind? It is **lost**, not gone: see [Recovery & Death Salvage](Recovery-and-Death-Salvage.md).
- Creative-mode spawn eggs exist for every type; they are not craftable.
