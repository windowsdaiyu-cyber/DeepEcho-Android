#!/usr/bin/env bash
set -euo pipefail

# Safety guard: this workflow is intentionally Android-only.
# It fails closed if the repository does not look like the isolated DeepEcho Android project.

test -f settings.gradle.kts
test -f app/build.gradle.kts
test -f app/src/main/AndroidManifest.xml

grep -Fq 'rootProject.name = "DeepEchoAndroid"' settings.gradle.kts
grep -Fq 'namespace = "com.deepecho.android"' app/build.gradle.kts
grep -Fq 'applicationId = "com.deepecho.android"' app/build.gradle.kts

# Known Windows/Electron entry files must not exist at the Android repo root.
for f in main.js preload.js renderer.js electron-builder.yml electron-builder.yaml; do
  if [[ -e "$f" ]]; then
    echo "ERROR: $f found at repository root. Refusing to build because this does not look like the isolated Android repository." >&2
    exit 2
  fi
done

echo "Android repository safety guard passed."
