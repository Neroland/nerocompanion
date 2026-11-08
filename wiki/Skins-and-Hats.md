# Skins & Hats

Looks only change how a companion looks; they never change what it can do. Choose them in the **Skin Studio**
(the Skin button on the Companion screen), which has a live preview and says how to unlock each locked look.
Unlocks are per companion.

## Skins

| Skin | Companion | How to unlock |
|---|---|---|
| Standard | every type | always |
| Aurora | every type | reward for the *Companion Keeper* quest (NeroQuests) |
| Graphite | Helper Drone | bond 2 |
| Snowdrift | Helper Drone | while the server's season is winter |
| Nightwatch | Scout Drone | bond 3 |
| Hazard | Mining Bot | use a Hazard Decal (Blank Chip + yellow dye + black dye) |
| Courier | Carry Bot | buy in the Skin Studio (needs NeroEconomy) |
| Workshop | Repair Bot | bond 2 |
| Sentinel | Combat Bot | bond 4 |
| Tinplate | Scrapling | earn the *Pet Adopted* advancement |
| Mossback | Scrapling | bond 3 |

## Hats

| Hat | How to unlock |
|---|---|
| Bobble Beanie | bond 1 |
| Antenna Bow | bond 2 |
| Halo Ring | bond 3 |
| Tiny Crown | bond 4 |
| Sprout | earn the *First Deploy* advancement |
| Hard Hat | use a Hard Hat (yellow dye + iron ingot + leather) |
| Sun Visor | use a Sun Visor (black dye + light blue stained glass pane + leather) |
| Headlamp | use a Headlamp (iron ingot + glowstone dust + leather) |
| Scrap Bucket | use a Scrap Shell (left behind when a Scrap Egg hatches) |
| Party Cone | while the server's season is festive |

"Use" means pressing **Apply** in the Skin Studio while you carry the item; the item is used up and the look stays
unlocked for that companion. Some hats do not fit every companion; the Studio lists only those that do.

## For pack makers

Looks are data:

- `data/<namespace>/skins/<type>/<name>.json`, or `skins/<name>.json` with `"types": ["*"]` for every type,
- `data/<namespace>/hats/<name>.json`.

A file names the companion types it fits (`"types"`), its `texture` (and optional `glow` and `eye_color`) and an
`unlock` rule: `default`, `bond` (`level` 1-4), `advancement` (`id`), `item` (`item`), `shop` (`price`), `season`
(`season`) or `hook` (`hook`, a Neroland Core gate a quest opens). Name it in your language file: the key is `skin.` or `hat.`, the namespace and the file path with `/` written as
`.` (for example `skin.mypack.mining_bot.camo`), plus the same key with `.unlock` for the unlock sentence. A
`{type}` in a texture path is filled with the companion type, for one file that fits every type. `/reload` sends the new list to everyone.
See [Compatibility](Compatibility.md).
