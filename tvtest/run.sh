#!/bin/bash
# The Bent Steering Samsung app on Chromium 56 (the 2018 Samsung TV engine), with a stand-in for the
# TV's own functions, against the live website. Writes out/log.txt and out/*.png.
set -uo pipefail
cd "$(dirname "$0")"
mkdir -p out
if [ ! -x e16/electron ]; then
  curl -fsSL -o e.zip https://github.com/electron/electron/releases/download/v1.6.18/electron-v1.6.18-linux-x64.zip
  unzip -q -o e.zip -d e16 && python3 patch_hb.py e16/electron
fi
export NO_PROXY_SWITCH=1 MOCK_TIZEN=1 MOCK_PKG="$PWD/../tizen/app" SHOTDIR="$PWD/out" STEPS="$PWD/steps-live.json"
URL="file://$PWD/../tizen/app/index.html"
timeout 240 xvfb-run -a -s "-screen 0 1920x1080x24" ./e16/electron --no-sandbox . "$URL" 165 2>&1 \
  | grep -vE "Gtk-|GLib|dbus|libva|Fontconfig|^$|gles2|Xlib|ALSA|gpu_|GPU|end of its support|encouraged|Read about" | tee out/log.txt
echo "exit $?"

# YouTube by itself on the old engine: one video, then a channel's uploads list (Bleepin' Jeep)
YT="https://admanroddy.github.io/BSO/tvtest/yt.html"
for case in "video|$YT#jNQXAC9IVRw" "channel|$YT?list=UULFvlG_ZnHoZLPv0CVWOC0a1Q"; do
  name="${case%%|*}"; url="${case#*|}"
  mkdir -p "out/yt-$name"
  MOCK_TIZEN= SHOTDIR="$PWD/out/yt-$name" STEPS="$PWD/steps-yt.json" timeout 90 xvfb-run -a -s "-screen 0 1920x1080x24" ./e16/electron --no-sandbox . "$url" 45 2>&1 \
    | grep -E "YT |ERROR|error" | tee "out/yt-$name/log.txt"
done
