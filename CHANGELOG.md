# Changelog

## 1.4

- Fixed: an expanded notification card could stay on screen forever after **Reply**. The reply box had no hide timer, so closing the keyboard with Back, locking the phone or walking away left the card open. It now has a 60 s watchdog, is cancelled on screen off, and always hands over to the queue.
- Fixed: the card now closes when its notification is removed from the shade, not only in "Keep notifications" mode.
- Fixed: timers of an old banner can no longer keep a card on screen or bring it back (they are tied to the banner generation).
- Fixed: opening a card by tapping a banner / pill always gives it a fresh hide timer, so it never stays expanded.
- Fixed: a new message from the same chat no longer snaps an open card back to the small banner.
- A notification that arrives while the screen is off now waits (up to 3 minutes) instead of expiring behind the lock screen, so it is still readable when the phone is picked up.
- New: **AMOLED island** — pure black pill, glow off and a visible white outline, so the island is readable on AMOLED wallpapers and on the lock screen.
- New: **Read on the locked screen** (off by default) shows the real sender and message text while the phone is locked. Secret notifications stay hidden.
- Added the new options to Customize > Island behavior, in English and Hindi.

## 1.3

- Added baseline profile for smoother scrolling and a faster first launch.
- Settings are now saved in the background (debounced) and flushed when the app goes to the background.
- App icons for a first notification load off the main thread (no stutter).
- Larger app icon cache (24 -> 64).
- Island position watchdog polls less often when idle (battery).
- Backup export always includes the latest settings; import is no longer overwritten by a pending save.
- README: corrected target SDK (34), added what's new and a screenshot gallery.

## 1.2

- Previous release.
