# KC Diamonds Stats

An Android app and a web dashboard for the KC Diamonds, a professional
softball team, backed by a pipeline that scrapes their published results and
folds them into the app's data on a schedule.

The 2026 season is complete: 41 games played, a 27-14 record, with a full box
score for every one of them.

## Getting it

**On an Android phone** — open the latest release and download
`kc-diamonds.apk`:

<https://github.com/inspectorgad/markdown/releases/latest>

Install it over any earlier copy; it keeps the data already on the phone. The
app then updates its own season data over the network, so a new APK is only
needed when the app itself changes.

**In a browser** — the dashboard is published from `docs/` to this
repository's GitHub Pages site (the address is on the repository's
**Settings → Pages**, and is usually `https://<owner>.github.io/markdown/`).
It installs to a phone's home screen like an app, and reads the same season
data the Android app does.

## What the app shows

- **Roster** — every player with her career batting line. Tap one for
  per-season totals, career totals and a game-by-game log.
- **Games** — every game with its score, opponent, venue and event. Tap one
  for the full box score. Games can also be added and edited by hand.
- **Leaders** — the team's record, runs for and against, team AVG/OBP/SLG,
  and leaderboards for average, hits, home runs, RBIs, runs and stolen bases,
  filtered by season or across all of them.
- **Standings** — the league table where one is published, and KC's own
  record against each opponent, which is derived from the box scores this
  repository holds rather than from anyone's ranking.

Derived stats (AVG, OBP, SLG, OPS, total bases) are computed from the raw
counting stats, so they stay in step with whatever is entered or synced.

## Where the data comes from

Nothing is typed in by hand unless you want it to be. A scheduled workflow
drives a headless browser over the team's published pages, captures the raw
box scores into `scraped/`, parses them into
`app/src/main/assets/seed.json`, and checks that result against the
officially published aggregates before it is allowed to ship.

```
BallClubz + the team site          scripts/scrape-kc-diamonds.mjs
            |                                     |
            v                                     v
   scraped/box-*.json  ----------->  scripts/update-seed.py
   (raw capture, committed)                       |
                                                  v
                                   app/src/main/assets/seed.json
                                                  |
                       +--------------------------+--------------------+
                       v                                               v
          bundled into the APK                       published as season-data.json
          (first launch, offline)                    on the latest-apk release
                                                                       |
                                                     the installed app syncs from here
```

`scripts/verify-seed.py` sits between the parse and the release: it compares
the seed against the official season totals and refuses to ship a seed whose
numbers it cannot corroborate. The raw capture is committed either way, so a
bad parse can always be investigated after the fact.

The app merges new data rather than replacing it, so anything entered by hand
survives a sync.

## Layout

| Path | What it is |
| --- | --- |
| `app/` | The Android app: Kotlin, Jetpack Compose, Room |
| `scripts/` | The scraper, the seed builder, the verifier, icon tools |
| `scraped/` | The raw capture, committed as evidence |
| `docs/` | The web dashboard, published to GitHub Pages |
| `.github/workflows/` | The scrape, the APK build, the Pages deploy |

## Building it

Open the project in [Android Studio](https://developer.android.com/studio) and
run it on an emulator or a device. The unit tests cover the stats engine, the
data merge and the downloaded-data validator:

```sh
./gradlew :app:testDebugUnitTest
```

CI builds the APK on every push to `main` and publishes it to the rolling
`latest-apk` release, alongside the season data the installed app syncs from.

## For maintainers

**[CLAUDE.md](CLAUDE.md)** is the full briefing: architecture, the pipeline in
detail, how releases work, the decisions behind the design and why, the house
conventions, known bugs, and what is still outstanding.
