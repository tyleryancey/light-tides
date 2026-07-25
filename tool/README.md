# Tides

Tide predictions for one coastal station, refreshed once a day.

## US-coastal only

Tides is built on NOAA's CO-OPS tide-prediction network — American coastal
water levels, the US's own domestic public-domain data. **v1's design,
testing, and defense are all US-coastal; that's the intended use case.** The
bundled ~3,000-station directory is NOAA's own listing, filtered at
generation time to stations carrying a US state or territory code — every
coastal state plus DC, Puerto Rico, the US Virgin Islands, Guam, the
Northern Marianas, American Samoa, and the Compact of Free Association
states (NOAA leaves a handful of genuine territory stations blank or
miscoded; the generator restores those by id). NOAA's raw directory also
carries a long tail of blank-state foreign reference ports (Papeete, British
Columbia, the Galapagos…), a holdover from decades of published US
predictions abroad; those are deliberately left out of the bundle rather
than shipped half-supported. If your coastline isn't a US NOAA station, this tool isn't
built with you in mind yet — international sources are a later, per-country
decision, not a v1 gap to be patched around.

## What it does

- Pick a station by typing a name or state — the ~3,000-station directory is
  bundled with the tool, so search is instant and works offline.
- See the next high or low tide, today's remaining tides, and a 7-day table —
  and nothing past 7 days. The screen ends where the week ends.
- One setting: feet or meters.
- Refreshes automatically once a day in the background; opening the tool
  always shows the most recent cached prediction first, with a network
  fetch only when the cache has gone stale.

## Data

Predictions come from [NOAA's CO-OPS Tides & Currents API](https://api.tidesandcurrents.noaa.gov/api/prod/),
a free, keyless, public-domain data source. Tides fetches a seven-day table
for one user-chosen station, at most once a day, and sends an `application`
identifier with every request per NOAA's usage courtesy guidelines. No
account, no API key, no cost.

## Why this is a clean tool to vet

Tides fetches a seven-day table of public-domain NOAA predictions for one
user-chosen station, once a day, and renders it as a finite list. No account,
no key, no feed, no infinite anything — the screen ends where the week ends.
It's the same shape as the SDK's own weather example, pointed at the coast,
for people whose "go outside" depends on the water.

## License

MIT — see the root [LICENSE](../LICENSE).
