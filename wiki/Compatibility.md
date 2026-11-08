# Compatibility

NeroCompanion needs only **Neroland Core** (1.17.4 or later). Every other Neroland mod is optional; each adds
something when it is installed and nothing breaks when it is not.

| Mod | What it adds |
|---|---|
| **Nerospace** | Off-world, flying companions fly faster on light worlds and slower on heavy ones; radiation powers a companion down unless it carries a Radiation Shielding chip |
| **Nerotech**, **NeroPower** | Their generators and cables charge Pads and Bays like any energy source |
| **NeroLogistics** | A Carry or Mining Bot can be bound to a network drive bay and delivers into it |
| **NeroEconomy** | Shop looks can be bought in the Skin Studio and stay unlocked; servers can charge credits for reclaiming. See [NeroEconomy integration](NeroEconomy-Integration.md) |
| **NeroColonies** | Companions never break, place, open, enter or attack inside a colony where their owner may not build |
| **NeroSecurity** | Companions never open, break or enter a secured device their owner may not use |
| **NeroFactions** | A Combat Bot never attacks a member of its owner's faction |
| **NeroCreatures** | Taming a creature counts for the *Pet Adopted* quest |
| **NeroQuests** | The Companion Log quest chapter. See [NeroQuests integration](NeroQuests-Integration.md) |
| **NeroLink** | The phone app shows and controls your companions. See [NeroLink](NeroLink.md) |

Companions respect vanilla protection (spawn protection, adventure mode, locked containers), Neroland Core gates
and the mods above. Other claim mods are not consulted. If NeroColonies, NeroSecurity or NeroFactions is installed
but cannot be reached (for example after an incompatible update), companions play safe and do not break, place,
open or attack where that mod would have decided; the server log says so once.

Recipe viewers: JEI and EMI show NeroCompanion's recipes like any vanilla crafting recipe.

## Adding a companion type with a data pack

Companion types are data: `data/<namespace>/companion_types/<name>.json`. A pack can retune a shipped type (same
path in the `nerocompanion` namespace) or add a new one built on an existing chassis:

```json
{
  "entity": "nerocompanion:carry_bot",
  "locomotion": "ground",
  "small": false,
  "max_health": 24.0,
  "armor": 2.0,
  "speed": 0.25,
  "attack_damage": 0.0,
  "energy": { "capacity": 60000, "drain_idle_per_second": 8, "drain_active_per_second": 30 },
  "modes": ["follow", "stay", "carry", "sleep"],
  "default_mode": "follow",
  "chip_slots": 3,
  "chip_categories": ["stat", "capability", "cosmetic"],
  "work_goals": ["nerocompanion:carry_haul"],
  "cargo_slots": 54
}
```

- `entity` must be one of the seven companion entities; it decides the model and the locomotion.
- `modes`: `follow`, `stay`, `guard`, `mine`, `carry`, `repair`, `scout`, `sleep`. `chip_slots` at most 6,
  `cargo_slots` at most 54.
- `work_goals`: `nerocompanion:drone_carry_lite`, `scout_ahead`, `scout_danger_alert`, `drone_lamp`, `mine_area`,
  `carry_haul`, `repair_gear`, `guard_target`, `guard_melee`, `pet_fetch`, `pet_nap`, `pet_play`.
- A bad value is reported and that field keeps the chassis default; an unknown `entity` drops the file.
  `/nscomp types` lists what loaded and every problem.
- To craft one, give a recipe a `nerocompanion:capsule` result with the component
  `"nerocompanion:blueprint": "<namespace>:<name>"`.
- Name it with `companion_type.<namespace>.<name>` in your language file.

## Adding skins and hats

See [Skins & Hats](Skins-and-Hats.md#for-pack-makers).

## Changing chip rules and tags

- `data/<namespace>/chip_rules/exclusive.json`: chip pairs that cannot be fitted together. See [Chips](Chips.md).
- `#nerocompanion:unmineable` (block tag): blocks a Mining Bot never breaks.
- `#nerocompanion:ore_filter` (block tag): extra blocks the Ore Filter chip keeps, on top of `#c:ores`. Scouts mark
  blocks in `#c:ores`, `#neroland:ores` and the vanilla ore tags.
- `#nerocompanion:protected_mobs` (entity type tag): mobs a Combat Bot never attacks.
