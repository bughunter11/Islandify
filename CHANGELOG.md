# Changelog

## 1.4

- Fixed: an expanded notification card could stay on screen forever after **Reply**. The reply box had no hide timer, so closing the keyboard with Back, locking the phone or walking away left the card open. It now has a 60 s watchdog, is cancelled on screen off, and always hands over to the queue.
- Fixed: the card now closes when its notification is removed from the shade, not only in "Keep notifications" mode.
- Fixed: timers of an old banner can no longer keep a card on screen or bring it back (they are tied to the banner generation).
- Fixed: opening a card by tapping a banner / pill always gives it a fresh hide timer, so it never stays expanded.
- Fixed: a new message from the same chat no longer snaps an open card back to the small banner.
- A notification that arrives while the screen is off now waits (up to 3 minutes) instead of expiring behind the lock screen, so it is still readable when the phone is picked up.
- New: **AMOLED island** — pure black pill, glow off and a visible white outline, so the island is readable on AMOLED wallpapers and on the lock screen.
- New: **Wake screen for notifications** (off by default) — lights up the display when a notification arrives with the screen off, so the island can be read on the lock screen. Android does not let third-party apps draw on the Always-On Display itself; this turns the screen on instead. Silent channels never wake it, and switching it on asks to exempt Islandify from battery optimization.
- Fixed: a notification that arrived while the screen was off could expire unseen when the island window was hidden, because the controller trusted a stale "screen on" flag. It now asks `PowerManager` for the real display state.
- New: **chat threads on the card** — when a chat gets several messages, the expanded card lists the last messages of that conversation and grows so three short messages are all readable in full (MessagingStyle when the app provides it, otherwise the last messages Islandify saw for that chat).
- New: **Always-On display (AOD)** (off by default) — a dim clock, the date, the battery and the notifications that are waiting while the phone is idle. Android does not let apps draw on the system AOD, so Islandify keeps its own black screen on at the lowest brightness (the workaround every third-party AOD app uses); the panel stays awake, so expect extra battery use. It needs the accessibility service, switches the panel off in a pocket (proximity sensor) and on very low battery, and a power press while it is up really switches the phone off. New notifications lift the brightness for a few seconds.
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
