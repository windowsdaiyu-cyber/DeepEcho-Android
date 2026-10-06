# DEEP-ECHO Mobile v1.10.5 test notes

## User-requested changes
- Removed the experimental Recognize Music feature and its bottom-navigation entry.
- Bottom navigation is back to Home / Search / Library; Settings remains a Home-only top shortcut.
- Rebuilt the Now Playing action row so Ambient Mode no longer collapses into vertical letters on narrow phones.
- Ambient Mode now uses a dedicated compact Ambient switch beside About Artist without stealing width from Like/Download.
- Ambient Mode always renders a lyrics pane: synced lyrics when available, otherwise a readable plain-lyrics fallback, with loading/not-found states instead of a blank pane.
- Ambient lyrics preserve theme-aware colors, glow, Romanized display support, and subtle audio-reactive bounce.
- Ambient media stage keeps cover and lyrics side-by-side with phone-width-aware sizing.
- Added/retained a clear Play Video button under the cover. The video replaces the cover in-place, starts at the current DeepEcho playback position, stays muted, and continuously re-syncs to DeepEcho while play/pause follows the song.
- If the current song URL does not expose a usable YouTube video ID, DeepEcho searches the existing YouTube video bucket for a matching video.

## Protected behavior
- PlaybackService was not changed.
- Downloads pipeline was not changed.
- AppUpdater was not changed.
- FloatingLyricsService was not changed.
- Existing lyrics provider/matching engine was not changed.
