# v1.12.12 test notes

Primary checks on device/emulator:

- Artwork mode cover should be close to the available phone width and never overlap metadata/actions.
- Short screens may scroll in Artwork mode; this is intentional to preserve large artwork.
- Drag/tap seek from ~0:20 to the middle/end of a remote song and confirm playback resumes quickly.
- Repeat backward/forward seeks, including within the previous ~20 seconds.
- Confirm ordinary Home/Search play, Next, Previous, pause/resume and continuous playback remain at least as stable as v1.12.11.
- Verify Lyrics / Minimal / Ambient modes and Ambient fullscreen switch.
- Regression-check Lyrics, Floating Lyrics, Exact Lyrics, Downloads, Queue, Smart Autoplay and themes.
