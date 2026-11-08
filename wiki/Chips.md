# Chips

Chips upgrade a companion. Every chip starts from a **Blank Chip** (copper ingot + redstone + iron nugget, makes 2).

## Fitting and removing

- Hold a chip and click an empty slot on the **Companion screen**, or right-click your companion with it.
- Click a fitted chip to take it out; it goes back to your inventory (or your feet if it is full).
- Helper and Scout Drones have **2** slots, the bots **3**, the Scrapling **1** for cosmetic chips only.
- One chip of each kind per companion (any tier), and some kinds cannot be fitted together:
  **Lamp + Silent Running**, **Chatterbox + Hush**, **Speed + Armour**. The screen tells you why a chip does not fit.
- Chips travel with the companion into its Capsule, a Bay and back. Looks never change stats; chips do.

## Stat chips (tiers I, II, III)

Tier I is a Blank Chip plus the reagent; tier II is tier I + the reagent + a gold ingot; tier III is tier II + the
reagent + a diamond.

| Chip | Reagent | I / II / III |
|---|---|---|
| Speed | sugar | moves and works 10 / 20 / 30 % faster |
| Efficiency | lapis lazuli | uses 15 / 25 / 35 % less energy |
| Range | amethyst shard | looks for work 2 / 4 / 6 blocks further |
| Capacity | redstone | holds 25 / 50 / 75 % more charge |
| Armour | iron ingot | +2 / 4 / 6 armour |
| Vitality | glistering melon slice | +4 / 8 / 12 health |

Speed, Efficiency, Range and Capacity chips also count as Neroland Core upgrade modules.

## Capability chips

Blank Chip plus the reagent.

| Chip | Reagent | Effect |
|---|---|---|
| Guard Protocol | iron sword | Lets any companion take the Guard job (one with no attack only keeps watch) |
| Ore Filter | raw iron | Mining keeps only ore drops; scouting marks only ores |
| Auto-Deposit | hopper | A carrier empties into its bound container without being told |
| Cargo Rack | chest | Nine more carry slots |
| Lamp | glowstone dust | Lights the space around a drone as it flies |
| Salvage | compass | Gathers your dropped items when you die, on any companion |
| Silent Running | white wool | No chatter, no work sounds, no sparkles |
| Radiation Shielding | obsidian | Ignores radiation hazards (Nerospace worlds) |

## Cosmetic chips

| Chip | Reagent | Effect |
|---|---|---|
| Chatterbox | note block | Chats twice as often |
| Hush | feather | Chats a quarter as often |
| Emote Pack | book | A second set of emotes |
| Sparkle Trail | glow ink sac | A little sparkle trail while it moves |

## For pack makers

Which chips exclude each other is data: `data/<namespace>/chip_rules/exclusive.json`, for example
`{"pairs": [["nerocompanion:lamp", "nerocompanion:silent_running"]]}`. Every namespace's file is merged and
`/reload` picks up a change. The chip kinds themselves are items and cannot be added by data.
