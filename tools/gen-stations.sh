#!/usr/bin/env bash
# Regenerates tool/src/main/resources/stations.json from NOAA's tide-prediction
# station directory. Desktop, one-time-per-release — station search is offline
# in the tool; only predictions need the network at runtime.
#
# Requires: curl, jq. Re-run ahead of each release.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$REPO_ROOT/tool/src/main/resources/stations.json"

# Blank-state entries are mostly NOAA's foreign holdovers (Papeete, B.C.,
# Galapagos…) — v1 is US-coastal only, so they stay out of the asset. But NOAA
# also leaves state blank on genuine US-territory stations (Guam, American
# Samoa, CNMI, one WA river gauge) and on the Compact of Free Association
# ports the README promises — STATE_PATCH restores those by id. Apia (1778000)
# is the reverse: a foreign station wearing "AS", so it is excluded by id.
curl -sS "https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations.json?type=tidepredictions" \
  | jq -c '
      def state_patch: {
        "1630000": "GU",  # APRA HARBOR, GUAM
        "1631428": "GU",  # Pago Bay, Guam
        "1770000": "AS",  # PAGO PAGO Harbor, Tutuila Island
        "TPT2623": "MP",  # Saipan Harbor, Saipan Island
        "1633227": "MP",  # Tanapag Harbor, Saipan (upstream mislabels as GU)
        "TWC1131": "WA",  # Stanwood, Stillaguamish River (upstream blank)
        "1820000": "MH",  # KWAJALEIN ATOLL (Kwajalein I.)
        "TPT2697": "MH",  # Kwajalein Atoll (Namur Island)
        "TPT2721": "MH",  # Majuro Atoll
        "1841275": "PW",  # MALAKAL HARBOR
        "1841367": "PW"   # Malakal Harbor, Palau Island (upstream mislabels as FM)
      };
      [.stations[]
        | {id, name, state, lat, lon: .lng}
        | .state = (state_patch[.id] // .state)
        | select(.id != "1778000")
        | select(((.state // "") | gsub("\\s+"; "")) != "")]' \
  > "$OUT"

echo "Wrote $(jq 'length' "$OUT") stations to $OUT"
