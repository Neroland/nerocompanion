# Using Neroland Core

NeroCompanion hard-depends on **Neroland Core 1.17.4** or later (below 2.0). The floor is the Core version the mod
is compiled against: `nerolandcore_version` in `gradle.properties` feeds the NeoForge and Forge version range
(`[1.17.4,2.0)`) and the Fabric `depends` entry (`>=1.17.4 <2.0.0`), so a server with an older Core refuses to
load the mod instead of crashing later. 1.17.4 is needed for the erasure report, the retention purge and the
`backupNow` behaviour the privacy code relies on.

Core is resolved per loader and Minecraft version as `za.co.neroland.nerolandcore:nerolandcore-<loader>-<mc>`.

## What NeroCompanion uses

| Core API | Used by | How |
|---|---|---|
| `config.ConfigSchema`, `ConfigManager`, `ConfigValue` | `config/NeroCompanionConfig` | The whole `config/nerocompanion.properties` file; hot-reloads with `/neroland config reload`. Gameplay keys are server-authoritative; `telemetryEnabled` and `hud.*` are client-local. |
| `registry.RegistrationProvider`, `BlockCodecs`, `PropertiesCompat`, `BlockEntityTypeCompat` | `registry/*`, `charge/*` | Blocks, items, block entities, menus, sounds, particles, data components and the mod's own creative tab. |
| `entity.EntityRegistrationSupport` | `registry/ModEntities` | Registers the seven companion entities and the Fetch Ball with their attributes on every loader. |
| `machine.AbstractMachineBlockEntity`, `sideconfig.SideConfig`, `SidePreset`, `Channel` | `charge/ChargingPadBlockEntity`, `CompanionBayBlockEntity` | The Pad and Bay are Core machines with an energy buffer. Both set `.defaultPreset(SidePreset.ALL_INPUT)` so every side accepts energy. |
| `energy.EnergyConversions` and the per-loader energy lookups | `charge/*` | Energy enters the Pad and Bay as Core energy and is converted to FE for the companions; any source Core's lookup sees can charge them. |
| `upgrade.UpgradeContainer`, `UpgradeType` | `chip/ChipSlots`, `chip/ChipItem` | A companion's chips live in a Core upgrade container. Speed, Efficiency, Range and Capacity chips classify as the matching Core `UpgradeType`; the other chip kinds are NeroCompanion's own. Chip numbers come from NeroCompanion, not Core's default modifiers. |
| `progression.ProgressionGates`, `GateScope`, `event.CoreEvents` | `progression/*`, `look/LookService`, `authority/VanillaAuthorityFacts` | Player-scope milestone gates (`nerocompanion:first_deploy`, `first_chip`, `pet_adopted`, `full_squad`, plus the activity gates the quest chapter reads), recorded for the owner even while offline; quest hook gates `nerocompanion:hook/skin/<id>` unlock skins; purchase gates `nerocompanion:purchase/<ns>/<path>` keep bought looks; gate checks feed the owner-authority chain. |
| `data.SavedDataRecovery`, `SavedDataCompat` | `data/RecoveryLedger`, `data/CapsuleVault`, `salvage/SalvageHold`, `incubation/IncubatorStore`, `privacy/PrivacyPrefs` | Every saved-data store loads through Core's recovery guard, so a corrupt file falls back to the last good backup instead of crashing the server; backups are refreshed after deletions. |
| `data.PlayerDataErasure` | `privacy/CompanionErasure`, `RetentionSweep` | One erasure handler covers every NeroCompanion store, so `/neroland data erase <uuid>` and `/neroland data eraseme` remove a player's companion data along with every other Neroland mod's. The retention sweep uses the same path. The tests run Core's `ErasureConformance` fixture over all stores. |
| `link.NeroLinkRegistry`, `LinkSnapshotProvider`, `LinkActionHandler`, `LinkEvent`, `LinkModuleInfo`, `LinkAlerts`, `LinkAlert` | `link/*`, `alert/CompanionAlerts` | The `nerocompanion` link module and the per-player alerts (see [USING-NEROLINK.md](USING-NEROLINK.md)). Core ships no server; the NeroLink bridge serves the module. |
| `economy.CurrencyApi`, `CoreCurrencies` | `recovery/Recovery`, `compat/neroeconomy` | The optional `reclaimCost`. Without a currency provider (normally NeroEconomy) reclaiming is free. |
| `worldgen.SpaceTags` | `compat/nerospace` | Off-world rules run only in dimensions Core tags as space; on a Core-only server the tags are empty and nothing changes. |

## What NeroCompanion does not take from Core

- Core's `UpgradeType` is a closed list, so the chip catalogue, tiers and exclusions are NeroCompanion's own.
- NeroCompanion never adds its items to Core's creative tab; it has its own `nerocompanion` tab.
- No third-party mod is a dependency. Other Neroland mods are reached only through `compat/<sibling>` façades that
  do nothing when the sibling is absent.
