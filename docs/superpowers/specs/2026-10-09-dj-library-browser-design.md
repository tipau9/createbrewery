# DJ Booth Library Browser — Design

Date: 2026-10-09. Status: approved. The implementation plan
(`docs/superpowers/plans/2026-10-09-dj-library-browser.md`) simplifies five points — own screen
instead of overlay, client pulls the library once a second, load swaps into the vacated slot,
BPM only (no Etched length), no on-deck marking; see its "Deviations" section.

## Goal

Turn the DJ booth's BROWSE tab into a rekordbox-style library browser while keeping physical
discs (vanilla records and Etched discs) the **only** way music gets onto a deck. No URLs, no
virtual tracks: the library *is* the discs stored in containers next to the booth.

Success: a DJ opens the booth, sees every disc around it in a sortable, searchable table grouped
into playlists, loads one onto any of the 4 decks with one click, and sees BPM for every disc that
has been played before.

## Findings that shape the design

- The current BROWSE tab reads `dj.crate()` on the **client**. Chest contents are not synced to
  clients unless the chest is open, so the tab shows nothing in practice. The library must be
  sent by the server.
- `crate()` only finds the first adjacent container; the browser needs all of them.
- Etched track data has title and artist (`PlayableRecord.getTracks`, read via reflection like
  `EtchedCompat.getTrackDisplayName`). Vanilla discs have a title and `JukeboxSong.lengthInTicks`.
- BPM: `MusicPulse.song` (a `DropDetector`) measures the tempo of everything heard, not per
  track. A BPM is only trustworthy while exactly one deck of the booth is playing.
- No musical-key detection exists. **Key is out of scope**; the Key column shows `–`.

## Units

### 1. Library model (server) — `DjLibrary`
Pure function of the booth's surroundings; no state of its own.

- **Playlists**: every item-handler container on the 5 sides `crate()` already scans (N, S, E, W,
  down), in that order, skipping other booths. Name = container's custom name (anvil) if set,
  else its block name. Each shulker box item inside a container is a nested playlist named after
  the shulker's custom name, else "Shulker Box".
- **Entry**: `(playlist index, slot, inner slot or -1, title, artist, bpm or 0, length ticks or 0,
  album flag)`. `inner slot` is the slot inside a shulker box.
- "ALL TRACKS" is a client-side view over every playlist, not a server concept.

### 2. Library sync — `DjLibraryPayload` (server → client)
- Sent to a player when they open the booth screen, and re-sent to players with the screen open
  when the snapshot changes (server compares a hash of the entries once per second for open
  viewers only — no work when nobody is browsing).
- Client keeps the last snapshot per booth position; the screen renders only from it.
- Viewers are tracked by a client "browse open/closed" `DjControl` action, dropped on logout,
  dimension change, or distance > 8 blocks.

### 3. Loading (`DjControl.LOAD_TRACK`, extended)
- Payload addresses `(playlist, slot, inner slot)` instead of a bare slot. The server re-reads the
  location and checks it still holds a music disc before moving anything (the snapshot can be stale).
- Disc moves container → deck. The previous deck disc goes back to the **same** place it came
  from (shulker contents rewritten via `DataComponents.CONTAINER`), else into the container, else
  dropped at the booth. A disc is never deleted or duplicated.
- The booth remembers the playlist last loaded from; **automix restock** cycles through that
  playlist instead of the first container only. Default (nothing loaded yet): first container,
  as today.

### 4. Track memory — `TrackInfo` data component
- `createbrewery:track_info` `{bpm: float, length_ticks: int}`, persistent + network-synced,
  registered like `Purity`.
- Client reports via a new `DjControl.REPORT_TRACK_INFO` once a deck has played alone for
  ≥ 30 s with a stable tempo (`DropDetector` period unchanged within 1 % for 8 beats).
  Length is reported when known (vanilla: from the song; Etched: when the stream ends).
- Server accepts only if: reporter can interact with the booth, that deck is playing, it is the
  only playing deck, BPM is finite and within 60–200. The disc on the deck gets the component.
- The library shows the component's values; vanilla discs without it show length from
  `JukeboxSong`.

### 5. Browser UI — in `DjMixerScreen`, drawn by a new `DjBrowserPanel` class
- BROWSE toggles a panel covering the screen from the top bar down to the transport row,
  full width (the 204 px touchscreen is too narrow for the columns). Widgets underneath are
  hidden and inactive while it is open. Escape or BROWSE closes it.
- Left sidebar (~110 px): ALL TRACKS, then playlists, shulkers indented; click to select.
- Top: search `EditBox`, filters case-insensitively on title + artist.
- Table, ~12 rows of 14 px: `#`, Title, Artist, BPM, Key (`–`), Length (`m:ss`), then four load
  buttons coloured like `DECK_COLORS`. Click a header to sort; click again to reverse.
  Mouse wheel scrolls; Up/Down select a row, keys 1–4 load the selected row onto that deck.
- Discs currently on a deck are marked with that deck's colour.
- Empty states: "NO RECORD CRATE AT BOOTH", "PLAYLIST EMPTY", "NO MATCHES".

## Error handling

- Stale snapshot / container changed: server re-validates on load; on mismatch it does nothing
  and the next snapshot corrects the view.
- Container removed mid-load: same re-validation.
- Malformed client values: existing `Float.isFinite` guard plus the range checks above.
- Etched not installed: Etched paths return false, as in `EtchedCompat` today.

## Testing (GameTests in `ClubGameTests`)

1. Library lists discs from two adjacent containers and a shulker inside one, with names.
2. Loading from a chest slot moves the disc and returns the previous one to that slot.
3. Loading from inside a shulker box rewrites its contents; previous disc returns into it;
   full shulker → previous disc goes to the container; full everything → dropped, never lost.
4. Stale address (slot emptied after snapshot) loads nothing and loses nothing.
5. Track info: accepted for the lone playing deck; rejected when two decks play, out of range,
   or from a player out of reach.
6. Automix restock follows the playlist last loaded from.

UI layout is verified by hand in game.

## Out of scope

Musical key detection; adding music other than by discs; cue points stored on discs;
cross-booth shared libraries.
