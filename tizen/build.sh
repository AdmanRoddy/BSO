#!/bin/bash
# Builds the Bent Steering Samsung TV app (BentSteering.wgt) and the Windows installer zip.
#
#   tizen/build.sh <app-folder> <out-folder>
#
# <app-folder> is a copy of tizen/app. A private copy also holds pictures/blueprints and
# pictures/badges; those builds run only in the private bso-blueprints repository.
# Runs on a GitHub Actions Ubuntu runner (needs the internet for download.tizen.org).
set -euo pipefail
APP="$(cd "$1" && pwd)"
OUT="$(mkdir -p "$2" && cd "$2" && pwd)"
HERE="$(cd "$(dirname "$0")" && pwd)"
TS_VER="${TIZEN_STUDIO_VER:-4.5.1}"
TS="$HOME/tizen-studio"
TIZEN="$TS/tools/ide/bin/tizen"
PKGS="http://download.tizen.org/sdk/tizenstudio/official"

echo "== Tizen Studio command line tools $TS_VER"
if [ ! -x "$TIZEN" ]; then
  curl -fsSL -o /tmp/tizen-cli.bin "https://download.tizen.org/sdk/Installer/tizen-studio_${TS_VER}/web-cli_Tizen_Studio_${TS_VER}_ubuntu-64.bin"
  chmod +x /tmp/tizen-cli.bin
  /tmp/tizen-cli.bin --accept-license "$TS" > /tmp/tizen-cli-install.log 2>&1 || { tail -40 /tmp/tizen-cli-install.log; exit 1; }
fi
"$TIZEN" version || true

echo "== signing certificate (a fresh author certificate each build; the installer handles the swap)"
PW="$(openssl rand -hex 12)"
echo "::add-mask::$PW"
rm -f "$HOME/tizen-studio-data/keystore/author/bso-author.p12"
"$TIZEN" certificate -a BentSteering -p "$PW" -c US -s TN -ct Chattanooga -o "Bent Steering Offroad" -n "Bent Steering" -f bso-author
"$TIZEN" security-profiles remove -n BSO > /dev/null 2>&1 || true
"$TIZEN" security-profiles add -n BSO -a "$HOME/tizen-studio-data/keystore/author/bso-author.p12" -p "$PW"
PROFILES="$HOME/tizen-studio-data/profile/profiles.xml"
# the command line tool keeps passwords in side files it can't read back on a runner without a keyring
sed -i "s|password=\"[^\"]*bso-author.pwd\"|password=\"$PW\"|g" "$PROFILES"
sed -i "s|password=\"[^\"]*tizen-distributor-signer.pwd\"|password=\"tizenpkcs12passfordsigner\"|g" "$PROFILES"
sed -i 's|password=""|password="tizenpkcs12passfordsigner"|g' "$PROFILES"
"$TIZEN" cli-config "profiles.path=$PROFILES" > /dev/null

echo "== package"
WORK="$(mktemp -d)"
mkdir -p "$WORK/app" && cp -r "$APP/." "$WORK/app/"
if [ -d "$WORK/app/pictures" ]; then
  echo "built-in pictures: $(find "$WORK/app/pictures/blueprints" -type f 2>/dev/null | wc -l) blueprints, $(find "$WORK/app/pictures/badges" -type f 2>/dev/null | wc -l) badges"
fi
# build-web wants a Tizen Studio project; if it won't take this folder, package the files as they are
if ! ( cd "$WORK/app" && "$TIZEN" build-web -e ".*" -e "*.md" -e "build.sh" ) || [ ! -f "$WORK/app/.buildResult/config.xml" ]; then
  echo "(build-web skipped: packaging the folder directly)"
  rm -rf "$WORK/app/.buildResult" && mkdir -p "$WORK/app/.buildResult"
  ( cd "$WORK/app" && tar --exclude=.buildResult -cf - . ) | ( cd "$WORK/app/.buildResult" && tar -xf - )
fi
command -v expect > /dev/null || sudo apt-get install -y -q expect > /dev/null
cat > "$WORK/package.exp" <<EOF
set timeout 600
cd "$WORK/app"
spawn "$TIZEN" package -t wgt -o "$WORK/out" -s BSO -- .buildResult
expect {
  "Author password:" { send -- "$PW\r"; exp_continue }
  "Yes: (Y), No: (N) ?" { send -- "N\r"; exp_continue }
  eof
}
EOF
expect "$WORK/package.exp"
WGT="$(ls "$WORK"/out/*.wgt | head -1)"
[ -f "$WGT" ] || { echo "no .wgt was made"; cat "$HOME/tizen-studio-data/cli/logs/cli.log" 2>/dev/null | tail -60; exit 1; }
cp "$WGT" "$OUT/BentSteering.wgt"
unzip -l "$OUT/BentSteering.wgt" | grep -E "signature|config.xml|shell.js|index.html" || true
unzip -l "$OUT/BentSteering.wgt" | tail -1

echo "== sdb for Windows (Samsung's TV connection tool)"
curl -fsSL "$PKGS/pkg_list_windows-64" -o "$WORK/pkg_list"
SDB_PATH="$(awk '/^Package *: *sdb *$/{f=1;next} f&&/^Package *:/{exit} f&&/^Path *:/{sub(/^Path *: */,"");print;exit}' "$WORK/pkg_list" | tr -d '\r')"
echo "sdb package: $SDB_PATH"
curl -fsSL "$PKGS$SDB_PATH" -o "$WORK/sdb.zip"
mkdir -p "$WORK/sdb" && unzip -q -o "$WORK/sdb.zip" -d "$WORK/sdb"
find "$WORK/sdb" -maxdepth 4 | head -30

echo "== installer zip"
KIT="$WORK/Bent Steering Samsung Installer"
mkdir -p "$KIT/tools"
cp "$OUT/BentSteering.wgt" "$KIT/"
cp "$HERE/installer/Install Bent Steering.bat" "$HERE/installer/README.txt" "$KIT/"
cp "$WORK"/sdb/data/tools/sdb.exe "$KIT/tools/"
cp "$WORK"/sdb/data/tools/*.dll "$KIT/tools/" 2>/dev/null || true
# keep sdb's licence notes with it
find "$WORK/sdb" -iname '*licen*' -exec cp {} "$KIT/tools/" \; 2>/dev/null || true
# Windows wants CRLF line endings in .bat and .txt files
sed -i 's/\r*$/\r/' "$KIT/Install Bent Steering.bat" "$KIT/README.txt"
VERSION="$(sed -n 's/.*<widget[^>]*version="\([0-9.]*\)".*/\1/p' "$APP/config.xml" | head -1)"
printf '{"version":"%s"}\n' "$VERSION" > "$OUT/version.json"
( cd "$WORK" && zip -q -r "$OUT/Bent-Steering-Samsung-Installer.zip" "Bent Steering Samsung Installer" )
ls -la "$OUT"
echo "built Bent Steering $VERSION"
