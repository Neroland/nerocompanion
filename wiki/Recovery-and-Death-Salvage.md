# Recovery & Death Salvage

## A lost companion always comes back

The server keeps a record of every companion: out in the world, in a Capsule, parked in a Bay, or **lost**.

A companion becomes lost when:

- it is **killed**, or
- it stays unseen in an unloaded area for `lostAfterMinutes` (30) while you are online, or
- it is unseen for `lostAfterOfflineDays` (30) even if you never come back.

You are told when one is lost (and on the NeroLink app if you turned notifications on).

### Getting it back

- Open any **Companion Bay**: the **Lost** list shows your lost companions. **Reclaim** gives one back as a
  Capsule with its chips, bond, name and looks.
- Or run `/nscomp recover` (your only lost companion, or a list) or `/nscomp recover <name or type>`.
- From the NeroLink app, **Reclaim** brings it back parked in its home Bay.

What it was carrying stays where it was lost. Reclaiming is free unless the server sets `reclaimCost` and runs an
economy mod; then the price is stated first and `/nscomp recover <name> confirm` pays it.

A companion can only exist once. If the old copy turns up later (its area loads again), it disappears and anything
it carried drops where it stood. A copied Capsule cannot bring out a second companion.

### Stuck companions

If a Bay holding a companion was moved or removed by another mod, an operator can run
`/nscomp admin release <player> <name|type|id>` to mark it lost, so its owner can reclaim it.

## Death salvage

When you die, **Carry Bots** (and any companion with a **Salvage Chip**) of yours near where you died gather your
dropped items for a minute (`salvageWindowTicks`) and hold them for you.

- **Talk to the companion** (right-click it) to get your things back.
- It holds them for `salvageHoldMinutes` (60). After that they drop where the companion stands, but only while you
  are online; an offline owner's items wait.
- Only items that fell when **you** died are gathered, and never more than fell. Another player's drops stay on
  the ground. If two players die at the same spot at the same moment, neither's items are gathered.
- Other players' companions leave your dropped items alone while your salvage window is open: their Carry Bots and
  drones will not pick them up as ordinary cargo. After the window closes, normal pickup rules apply again. This
  protection stays on even when a server turns salvage off.
- Nothing happens with `keepInventory`. Servers can turn salvage off with `salvage.enabled=false`, or limit it to
  Salvage-chip companions with `salvage.carryBotsByDefault=false`.
