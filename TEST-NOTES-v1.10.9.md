# DEEP-ECHO Mobile v1.10.9 Test Notes

- Smooth Ambient video by avoiding frequent decoder-flushing seeks.
- Hard resync only for large drift; smaller drift uses 0.985x–1.03x visual-only speed correction.
- Live-only theme system; Golden default.
- Session snapshot stores one song, timestamp and theme only.
- Restored song is prepared PAUSED.
- Navigation state is intentionally not restored.
