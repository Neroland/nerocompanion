# NeroCompanion

> Part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

**Status:** barebones multiloader skeleton — version `0.3.0-alpha.1`. No gameplay content yet; optional NeroEconomy seams are in place.

## Build targets

- **Minecraft:** 26.1.2, 26.2 and 26.3
- **Loaders:** NeoForge, MinecraftForge/Forge, Fabric (the "9 cells")
- **Java:** 25
- Mod id: `nerocompanion` · package `za.co.neroland.nerocompanion`

## Layout

The build is the repo root, with a flattened cross-loader structure driven by Stonecutter:

- `common/` — shared, loader-agnostic source spliced into every loader node
- `fabric/` — Fabric Loom
- `forge/` — ForgeGradle
- `neoforge/` — ModDevGradle
- `stonecutter.gradle` — the real root build script; `build.gradle` is intentionally inert

## Building

```sh
./gradlew :fabric:26.2:build          # one cell
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build \
          :forge:26.1.2:build :forge:26.2:build :forge:26.3:build \
          :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build   # all nine
```

## Developer notes: NeroEconomy seams and carry-bot rules

NeroCompanion has no content yet. `common/src/main/java/za/co/neroland/nerocompanion/economy/CompanionEconomy`
is the ready-to-use seam:

- `chargeCosmetic(player, price, purchaseId)` charges reason `cosmetic_purchase` into NeroEconomy's Reserve.
  Unlock only when `outcome.ok()`. The purchase id is the request key, so a double click never charges
  twice; the confirm step restates the price and what the player gets.
- `askPrice(server, itemId)` / `bidPrice(server, itemId)` wrap `CommodityApi.ask` / `bid`: what a Merchant
  House sells / pays for one unmodified unit now (empty when it does not, or NeroEconomy is absent).
- `remainingToday(player, itemId)` wraps `CommodityApi.remainingToday`: how much more of that item the
  player may trade with the Houses today, about that player only.

- NeroEconomy is **optional**. It is compiled `compileOnly` (never bundled) and declared optional in
  every loader manifest. One bridge class, `economy/NeroEconomyBridge`, is the only code that imports
  `za.co.neroland.neroeconomy`, and it is loaded only after `isModLoaded("neroeconomy")` and an API
  version check (`SiblingEconomy.API_VERSION >= 1`, read reflectively). The handle is claimed once from
  common init.
- Every call returns an `EconomyOutcome`: `APPLIED` (grant), `REFUSED` (nothing moved; show
  `outcome.message()`), or `ABSENT` (NeroEconomy is not installed; use the fallback).
- Request keys name the thing being paid for, never a player. A key holding a UUID or 32 hex digits is
  refused before it is sent. Nothing logs who paid.
- Dev clients load NeroEconomy only with `-PwithNeroEconomy` (NeoForge and Fabric), so the default run
  proves the mod works without it.

### Carry bots that haul to market

No API is needed: the Auto-Trader is a vanilla `WorldlyContainer` (slots 0-8 accept tradeable stacks from
any face, slots 9-17 give up what a buy rule bought), so a bot that inserts into a vanilla container already
works. Two rules:

1. **No creative or spectator feeding.** Goods from a creative-mode or spectator player are never a source
   of value; a bot owned by such a player must not feed a trader.
2. **No chunk loading.** The Auto-Trader never loads chunks, and a bot must not either just to keep one
   running.

## Privacy & telemetry

> **Telemetry notice:** NeroCompanion sends anonymous error reports (stack trace + mod/game
> versions only — never IPs, usernames, UUIDs, or world data) to the developers via Sentry
> (EU servers) so crashes can be fixed. On by default — opt out any time by setting `telemetryEnabled = false`
> in `config/nerocompanion.properties`. Full details:
> [PRIVACY.md](https://github.com/Neroland/nerocompanion/blob/main/PRIVACY.md).

See [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) for agent and contributor context.
