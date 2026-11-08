# Modes

A companion always has one mode (job). Pick it from the **Remote's radial** (hold use; keys 1-8 also work), from the
Companion screen, or from the NeroLink app. A companion only offers the modes it can do: see
[Companions](Companions.md).

| Mode | What the companion does | Who has it |
|---|---|---|
| **Follow** | Stays close and comes along wherever you go; small ones perch on your shoulder | everyone |
| **Stay** | Waits where it is until told otherwise (a Combat Bot guards that spot) | everyone |
| **Guard** | Watches over you and fights hostile mobs that come near | Combat Bot, or any companion with Guard Protocol |
| **Mine** | Digs out the box you marked with the Remote | Mining Bot |
| **Carry** | Picks up dropped items and delivers them | Helper Drone, Carry Bot |
| **Repair** | Mends your worn armour and hotbar items | Repair Bot |
| **Scout** | Flies ahead and marks ores and enemies for you | Helper Drone, Scout Drone |
| **Sleep** | Powers down and uses no energy; pick any job to wake it | everyone |

## Things that change a companion's behaviour on their own

- **Low battery**: at `energy.lowBatteryPercent` (10 %) it heads to its home Pad or Bay if that is within
  `energy.returnHomeRange` (96 blocks), otherwise it sleeps where it is. Charge it and pick a job again.
- **Owner away**: beyond `throttleDistance` (32 blocks) it thinks less often; beyond `sleepDistance` (64 blocks), or
  while you are offline or in another dimension, an idle companion sleeps. Work pauses and resumes when you return.
- **Hazards**: a sleeping companion is moved out of water, lava or a wall instead of being left to drown or burn.
- **Off-world** (with Nerospace): radiation puts it to sleep unless it carries a Radiation Shielding chip.
