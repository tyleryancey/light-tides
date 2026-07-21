#!/usr/bin/env bash
# Regenerates tool/src/main/resources/stations.json from NOAA's tide-prediction
# station directory. Desktop, one-time-per-release — station search is offline
# in the tool; only predictions need the network at runtime.
#
# Requires: curl, jq. Re-run ahead of each release.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$REPO_ROOT/tool/src/main/resources/stations.json"

curl -sS "https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations.json?type=tidepredictions" \
  | jq -c '[.stations[] | {id, name, state, lat, lon: .lng}]' \
  > "$OUT"

echo "Wrote $(jq 'length' "$OUT") stations to $OUT"
