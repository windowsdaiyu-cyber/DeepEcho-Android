# v1.12.14 test notes

## Queue contract
1. Start a song from Home/Search with Smart Autoplay ON.
2. Open Queue: random autoplay candidates must NOT appear.
3. Use Play Next on one song and Add to Queue on another. Queue should show only those manual upcoming songs (plus current song as context).
4. Manual Play Next must play first. Manual Add to Queue must remain in intentional order.
5. After all intentional upcoming items are exhausted, Smart Autoplay may continue internally.
6. Smart Autoplay should never build a 50/100+ visible upcoming list.

## Regression
Retest Home/Search tap-to-play, Next/Previous, fast seek, lyrics, vocal-entry timing, Floating Lyrics, Downloads, themes, playlists and long-press actions.
