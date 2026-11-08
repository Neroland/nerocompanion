# NeroLink (companion app)

With the **NeroLink** bridge mod installed on the server, the NeroLink phone app shows your companions next to
your drones. NeroCompanion needs nothing else: without NeroLink none of this is active.

## What the app shows

Only **your own** companions, found by the owner stored on each companion. Nobody else's, ever.

- **Roster** - each companion's type, name, mode, battery, bond level, skin and hat, and a rough idea of where it
  is: *near you*, *at home*, *away* or *unknown*, plus the name of the dimension. Never coordinates.
- **Lost** companions you can reclaim, and companions parked in a Bay.
- **Salvage** - whether a companion is holding your things after you died: how many items and when the hold ends.
- **Bays** - the Companion Bays your companions call home, numbered 1, 2, 3 ... and what is parked in each. Never
  where they are.

## What you can do from the app

| Action | When it works |
|---|---|
| Change mode | You are online and the companion is out and loaded |
| Recall | The companion is out and loaded; it parks in its home Bay if a slot is free |
| Reclaim | The companion is lost; it comes back parked in its home Bay if a slot is free (the server's reclaim cost, if any, applies) |
| Rename | You are online; the same filter and 24-character limit as in game |

Recall and reclaim also work while you are offline. Nothing is ever put into your inventory from the app. The app
can send at most 10 companion actions a minute.

## Notifications

Push notifications are **off by default**. Turn the *Companions* category on in the app to get one when a
companion's battery runs low, it gets lost, it comes back, it picks up your things after you die, or your bond with
it reaches level 2, 3 or 4. At most one notification per kind per companion each minute.

A notification says the companion's type and the name you gave it (and, for salvage, the minutes left). It never
contains a position, a dimension, an id or another player's name. Notifications pass through the NeroLink relay
and your phone's push service.

To stop them from the game, run `/nscomp privacy push off`: NeroCompanion then sends no notification and no alert
about your companions at all, so the app's alert list shows none from it (the companion list still works). With
push on (the default), the *Companions* category in the app is the master switch: nothing reaches your phone until
you turn it on.

## Privacy

The app reads what the mod already keeps about your companions; nothing new is stored for it. See the
[Privacy section](Server-Admin.md#privacy) and
[PRIVACY.md](https://github.com/Neroland/nerocompanion/blob/main/PRIVACY.md).
