# KC Diamonds Stats

An Android app for the KC Diamonds softball team that captures every player's
historical batting stats and keeps season aggregates up to date as new games
are played.

## Features

- **Roster** — add players (name, jersey number, position) and see each
  player's career batting line at a glance. Tap a player for per-season
  aggregates, career totals, and a full game log.
- **Games** — record games (date, opponent, season, score). Back-fill past
  games to capture historical stats, then keep adding games for the rest of
  the season. Inside a game, tap any player to enter their batting line
  (AB, R, H, 2B, 3B, HR, RBI, BB, SO, HBP, SF, SB).
- **Leaders** — filter by season (or all-time) to see the team's win–loss
  record, runs for/against, team AVG/OBP/SLG, and leaderboards for batting
  average, hits, home runs, RBIs, runs, and stolen bases.

Derived stats (AVG, OBP, SLG, OPS, total bases) are computed automatically
from the raw counting stats, so aggregates always stay in sync as games are
added or edited.

All data is stored locally on the device in a Room (SQLite) database.

On first launch the app seeds itself from `app/src/main/assets/seed.json`
(currently the confirmed 2026 KC Diamonds pro roster). Games and per-player
box-score lines can be added to that file to ship pre-populated historical
stats; seeding only runs when the database is empty, so it never overwrites
user-entered data.

## Tech

- Kotlin + Jetpack Compose (Material 3)
- Room for persistence
- Pure-Kotlin stats aggregation engine in `app/src/main/java/com/example/stats/`,
  covered by unit tests in `app/src/test/`

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project
4. Run the app on an emulator or physical device
