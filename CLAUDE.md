# CLAUDE.md — maintainer's briefing

Everything needed to pick this project up cold. README.md is the front door
for someone who wants the app; this is for whoever has to change it.

---

## 1. What this is, and where it stands

An Android app that tracks the **KC Diamonds**, a professional softball team,
plus a data pipeline that scrapes their published results so nobody has to
type a box score in, plus a web dashboard over the same data.

**Current state (November 2026):**

| | |
| --- | --- |
| Season | 2026 complete. Last game 8 Aug. Nothing until next summer. |
| Record | 27-14 across 41 scored games (48 rows in the seed; 7 were never played or never published) |
| Batting | 1130 AB, 390 H, a full box score for all 41 games |
| Pitching | 41 games, 263.0 IP, 3.99 ERA, plus the opponent's half of every box |
| Roster | 24 players |
| Scrape | Weekly, Mondays 14:07 UTC (was four times daily in season) |

The app is live and installed; the pipeline runs unattended. The 2026 data is
final and will not change unless the upstream source corrects something.

**There is no `main`-side work in flight.** The significant unmerged work is a
Kotlin Multiplatform conversion with an iPhone app — see §9.

---

## 2. Architecture and layout

```
app/                        Android app (Kotlin, Jetpack Compose, Room)
  src/main/java/com/example/
    MainActivity.kt         the only activity
    DiamondsViewModel.kt    everything the screens read and change
    data/                   Room entities, DAO, database, seeder, sync, standings
    stats/BattingStats.kt   the aggregation engine
    ui/                     DiamondsApp (tab host) + one file per screen
  src/main/assets/seed.json THE data artifact (see §3)
  src/test/                 unit tests, run in CI on every push
scripts/
  scrape-kc-diamonds.mjs    Playwright scraper -> scraped/
  update-seed.py            scraped/ -> seed.json
  verify-seed.py            gate on seed.json; exit code is the signal
  parse-pitching.py         standalone pitching parser (now also in update-seed)
  make-launcher-icons.py    Android launcher icons from the team logo
  make-web-icons.py         dashboard icons from the same source
scraped/                    raw capture, committed deliberately (see §5)
docs/                       the web dashboard, one HTML file + PWA bits
.github/workflows/          scrape, APK build, Pages deploy, v1 freeze
```

**The single most important fact about the architecture:** everything flows
through one file, `app/src/main/assets/seed.json`. The app bundles it, the
app syncs a copy of it, and the dashboard fetches it. Nothing else is a
source of truth. Understand that file and you understand the system.

### seed.json

```jsonc
{
  "formatVersion": 1,            // the app refuses anything higher
  "generatedAt": "2026-10-13T16:12:26Z",
  "players":  [ { "name", "jerseyNumber", "position" } ],
  "standings": [ { "team", "w", "l", "t", "rf", "ra", "kc" } ],
  "games": [ {
    "date": "2026-08-08",        // ISO, so text order == date order
    "opponent", "season", "event", "location",
    "teamScore": 8, "opponentScore": 2,
    "srcId": "2b7aa5e",          // upstream id — the real key, see §5
    "lines":            [ { "player", "ab","r","h","2b","3b","hr","rbi","bb","so","hbp","sf","sb" } ],
    "pitching":         [ { "player","outs","h","r","er","bb","so","pitches","strikes","dec" } ],
    "opponentLines":    [ ... ],  // same shape as lines
    "opponentPitching": [ ... ]   // same shape as pitching
  } ]
}
```

Three things in there are easy to get wrong:

- **`outs`, not innings.** `5.1` in a box score means five and one third. It
  is stored as `16`. Storing `5.1` as a decimal would make every ERA quietly
  wrong by a little, which is worse than loudly wrong.
- **`srcId` is the key, not the date.** 8 Aug 2026 was a doubleheader. Keying
  on date collapsed the two games into one; that bug is why this field exists.
- **`dec`** is `W`, `L` or `S` and comes from the wrap text, not the table.

### The app

Four tabs — Roster, Games, Leaders, Standings — plus a detail screen for a
player and for a game, shown as full-screen overlays. One Room database,
`kc_diamonds.db`, schema version 3, three entities (`players`, `games`,
`stat_lines`). It has never carried a hand-written migration: it is built with
`fallbackToDestructiveMigration`, so **raising the schema version wipes every
phone.** If you change the schema, write a real migration first.

Data reaches the app two ways, both through the same merge:

1. **Bundled** — `Seeder.sync()` runs on every launch from `assets/seed.json`.
2. **Synced** — `SeasonSync.sync()` fetches `season-data.json` from the
   `latest-apk` release, at most every 6 hours or on pull-to-refresh.

Both call `Seeder.merge()`, which **gap-fills only**: it adds what is missing
and never overwrites what is there. That is what lets someone type a score in
by hand and keep it through every subsequent sync.

---

## 3. Data sources and the scrape

**Sources:** BallClubz (the league's platform — schedule, line scores, box
scores) and the team's own site (schedule, logo). Both are public; the scrape
reads what a browser would see, with a real headless Chromium, because the
pages are JavaScript-rendered.

**The run, end to end:**

1. `scrape-kc-diamonds.mjs` discovers every game id, visits each game page,
   clicks through to the box score, captures KC's half and the opponent's
   half, and writes `scraped/box-NN-<id>.json` plus a screenshot.
2. `update-seed.py` parses that capture into `seed.json`.
3. `verify-seed.py` compares the result against the officially published
   season aggregates. Its **exit code** is the gate.
4. The workflow commits the raw capture **either way**, and ships the seed
   only if verification passed.

**Reading verify-seed output.** It currently reports **0 failures and ~36
warnings**, and that is the healthy steady state. The warnings are permanent,
not a backlog:

> The official aggregate page appears to count the regular season only. It
> reads 26-13 where the seed correctly reads 27-14, and the same exclusion
> makes about twenty per-player totals read as over-counts. Every game in the
> seed is corroborated by the published schedule, so the seed is right and the
> aggregate page is narrower. **Only a verify-seed *failure* is an alert.**

---

## 4. Builds and releases

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `scrape-data.yml` | Weekly cron + `scrape-trigger.txt` + manual | The whole pipeline above; commits, ships the seed, refreshes the feed |
| `build-apk.yml` | Push to `main` + manual | Tests, builds the debug APK, publishes it |
| `pages.yml` | Push to `docs/` | Deploys the dashboard |
| `freeze-v1.yml` | One-off, spent | Snapshotted a known-good build |

**Three releases, three jobs:**

- **`latest-apk`** — rolling. Carries `kc-diamonds.apk` *and*
  `season-data.json`. This is both the download link and the app's sync feed.
- **`v1-stable`** — frozen July build, the rollback if a newer one misbehaves.
- **`shared-code-test`** — pre-release, test builds from a branch.

**The APK is a debug build signed with a committed debug keystore**
(`debug.keystore.base64`). That is deliberate: it is not a Play release, and a
stable signature is what lets every build install over the last one and keep
its data. A release signing config exists and reads its passwords from the
environment; no password is in the repository.

**Two non-obvious things about the feed:**

- Pushes made with `GITHUB_TOKEN` do not trigger other workflows. That is why
  the scrape uploads `season-data.json` itself instead of leaving it to the
  APK build.
- The upload reconciles on **digest**, not on "did this run change
  something". A seed corrected out of band once left the feed serving the bad
  copy because that run reported no change and skipped the upload.

---

## 5. Decisions, and why

Each of these looks arbitrary until it isn't. Most were paid for.

**The raw capture is committed.** `scraped/` is ~44 games of JSON and
screenshots in the repository. A failed verification used to discard the whole
run, taking with it the evidence needed to diagnose it — so the same bad parse
could not be investigated without waiting for another 15-minute scrape. The
capture now commits even when the seed is rejected.

**Games are keyed on `srcId`.** See §2. A date is not unique.

**Innings are outs.** See §2.

**A gated table must never parse as zeros.** The source briefly put box scores
behind a login, serving dashes where numbers go. A dash is an absence; a zero
is a claim. The parser returns nothing for a gated table rather than a row of
zeros, and a re-derive that would more than halve a game's at-bats is refused
outright.

**The seed is gap-filled, never replaced.** The whole point is that a correction
typed in by hand survives.

**An older bundled seed must not roll back newer synced data.** An APK carries
whatever data was current when it was built. Installed over a phone that has
since synced something newer, it would otherwise roll the standings back on
every launch. `Seeder.olderThan()` compares the two `generatedAt` stamps and
holds the bundled copy back — but only when both stamps are readable, because
holding back on a guess would strand a phone with no data at all.

**The scrape dropped to weekly for the off-season.** Six-hourly collection
exists to put a weekend result in the app the same evening. There are no
results. It was kept weekly rather than switched off for two reasons: the
official aggregates may yet reconcile, and a selector that rots in January is
much better found in January than on opening weekend.

**Capture guards, each from an incident:**

- *Tab switch* — the box opens on the away team, so for a road game clicking
  "KC Diamonds" changed nothing and both halves were saved as KC's. The
  opponent was missing from exactly the 16 away games.
- *Id floor* — the listing pages briefly served a short schedule and one run
  captured 33 games instead of 44, silently, because nothing compared a run's
  harvest against the last. Discovery now unions in every `srcId` the seed
  already holds.
- *Preserve on failure* — a page that failed to load used to have an error
  stub written over its good capture. The seed survived on gap-filling, which
  is exactly what made the loss invisible. The previous capture is now kept.
- *The stats snapshot is deliberately **not** preserved* — it is what
  verify-seed checks against, and checking today's seed against a stale copy
  of the official aggregates would be worse than not checking.

**Things that look like oversights but are not.** Each of these has been
tried the other way and reverted:

- *The opponent label clicked on the box tab is the truncated line-score
  spelling* ("Atlanta Smok", "New York Ris"), because that is what the
  clickable toggle says. Normalising it to the seed's spelling breaks the
  click.
- *`if (away === kc || !away.includes('Batters\t')) away = ''`* must stay.
  Without it KC's own lines are stored as the opponent's, and the result looks
  entirely plausible — which is what happened for all 16 away games.
- *A box's date may be inferred from a score match only when that match is
  unique.* Scores repeat; the permissive version attached an 8-2 box to a date
  whose real result was 7-8.
- *A TBD schedule slot is not a wildcard*, for absorbing a result or for
  creating a row. Both permissive versions corrupted the schedule.
- *Absence of a key means "not yet known".* `'teamScore' not in game` is the
  gap-filling test throughout, in Python and in Kotlin. Writing explicit nulls
  breaks all of them at once.
- *Opposing players' names are stored exactly as the box prints them*,
  upstream errors included, because folding them onto anything would invent
  players. The seed therefore contains some names that are visibly wrong.
- *Never route the opponent's half through the KC parser.* The KC path appends
  unrecognised names to the roster, so one run would file ~40 opposing players
  as Diamonds.
- *Do not add a "batters' runs equal the team score" check.* It was measured
  and rejected: it fails on correct data, because temporary runners are not in
  the batting table.

**Stats are implemented twice.** `stats/BattingStats.kt` in Kotlin and again
in JavaScript inside `docs/index.html`. They must agree. If you change an
aggregation rule, change both. (Consolidating them is on the TODO list.)

---

## 6. Conventions

The house style is unusual and worth matching.

**Comments explain *why*, at length, and name the incident.** Not what the
code does — the code says that. A representative one:

```kotlin
// Kept in step with the database as we go. A stale snapshot let the
// second game of a doubleheader re-match the row the first had just
// claimed, so the two games collapsed into one.
```

```js
// The name ends at the first tab. A looser match runs past the "X" that
// marks a side not batting in the last inning and returns the whole row,
// tabs and all, which then matches no element on the page - losing the
// opponent for precisely the games a team did not bat last.
```

Workflow files carry the same register: `scrape-data.yml` explains in prose
why the cron sits at `:07` and why there are four of them. Follow it. A future
maintainer with no memory of the incident is the audience.

**Other conventions:**

- Kotlin: 4-space indent in `app/src/main/java`, 2-space in Gradle files.
  Compose functions are `PascalCase`; everything else is standard Kotlin style.
- Python: standard library only, no dependencies, run straight by the workflow.
- Fail closed. When a parse is ambiguous, refuse rather than guess, and say so
  loudly enough to show up in a log.
- Commit messages are prose, not bullet lists: what changed, and why it was
  worth changing. Several paragraphs is normal here.
- Unused dependencies are commented out in `app/build.gradle.kts` rather than
  deleted, so they are easy to restore.

---

## 7. Known bugs and rough edges

### Fixed — the three dated 2027 breakages (November 2026)

Items 1–3 were guaranteed failures on a known date. They are fixed, and are
kept here under their original numbers because the rest of this file and §8
refer to items by number. What they were, and what replaced them:

1. **`verify-seed.py` hard-coded `2026` in its published-schedule key.** From
   the first 2027 game every scored game would have failed as uncorroborated
   and the pipeline would have stopped shipping any seed at all. Replaced by
   `season_year()`, which reads the year off the team-site captures
   (`scraped/schedule.txt`, `scraped/past-results.txt` — they print `Oct 17,
   2026`; BallClubz's own rows carry no year at all, only `Sat 8/8`), and
   takes the latest year present. Two consequences worth knowing: the
   corroboration check and the seed-side aggregates are both now **scoped to
   that one season**, because the pages upstream publish one season at a time
   and an all-time seed compared against a one-season page reads as every
   batter over-counting; and when **no** captured page carries a year the
   check is skipped with a warning rather than run on a guess, since a guessed
   year fails every game, which is the breakage being replaced.
2. **`update-seed.py`'s `season_for()` hard-coded 2026 dates.** Worse than
   stale: the comparisons are on strings, and `'2027-06-15' >= '2026-10-01'`
   is true, so every 2027 game would have been labelled `2026 Fall`. The year
   now comes from the date itself. **The month-and-day boundaries — 3 Aug for
   the postseason, 1 Oct for the fall exhibitions — are still the 2026
   calendar and are a guess about any other year. Check them against the
   published schedule each season.** A wrong boundary mislabels the games
   around it; the wrong year mislabelled all of them.
   `EMBEDDED_MAP`'s 29 ids were left alone: they are a verified historical
   floor for 2026 and are harmless in any later year, since every id the seed
   already holds is folded in from its own `srcId` records.
3. **The scraper's `games.json` context climb hard-coded the year and the
   months** — `/\b(Jun|Jul|Aug|Sep|Oct)\s+\d{1,2},\s*2026/`. The silent one:
   in 2027 it would have matched nothing, every `context` would have degraded
   to a page-nav blob, and `update-seed.py` would have lost its game-id ->
   date mapping while `games.json` still looked valid. Widened to all twelve
   months and any four-digit year, which is what `update-seed.py` already
   parsed at the other end. The one remaining mismatch between the two — a
   literal `, ` where the scraper allowed `,\s*` — was closed at the same time
   so the finder cannot accept a context the parser then cannot read.

All four behaviours were checked against the committed capture and against a
simulated 2027 run: today's output is byte-identical (0 failures, 36
warnings, 27-14, 41 games, `SEED_CHANGED=no`); a 2027 seed that would have
produced 75 failures and exit 1 now passes; a genuine 2027 misdating is still
caught; and a capture with no dated page warns instead of failing.

### Real bugs, live now

4. **`fallbackToDestructiveMigration` is armed.** Raising the schema version
   wipes every installed phone. Nothing warns you.
5. **`verify-seed.py`'s `systemic` flag has an operator-precedence bug.**
   `len(over) >= 3 and bool(uncorroborated) or bool(uncorroborated)` reduces to
   `bool(uncorroborated)`, so the documented "3 or more drifted players"
   threshold is dead. One misdated game produces ~33 failure lines instead of
   2, burying the real finding.
6. **`canonical()` calls `.title()`**, which mangles intercapital surnames
   (`McMillan` -> `Mcmillan`). The mismatch is silent — the player is just
   skipped — so "19 official batters checked" overstates real coverage, which
   is 17 of 24. The highest-AB batter on the roster is currently never
   cross-checked.
7. **The scraper's shortfall guard double-counts gated games.**
   `reached = boxOk + gated`, but `gated` is already inside `boxOk`. The guard
   over-reports what was reached in exactly the situation the gating counter
   was added for. That path has never fired, so it is wholly untested.
8. **Pitching decisions are matched by surname**, so a pitcher listed twice in
   one table gets the decision twice. This is live in the seed: 2026-07-04 and
   2026-07-31 both have an opposing starter appearing twice.
9. **Only the first 20 warnings print**, while the summary counts all of them.
   A new warning class landing after position 20 is invisible in CI logs.

### Traps in the build and release machinery

10. **`build-apk.yml` still fires on pushes to
    `claude/kc-diamonds-stats-app-r7de5h`** — the dead branch from §9. A push
    there would clobber both `kc-diamonds.apk` and `season-data.json` on the
    public `latest-apk` release with a July build, pushing stale data to every
    installed phone. This is the concrete reason that branch must not be
    pushed to, and removing the trigger would be a kindness.
11. **The app's feed resolves `releases/latest/download/season-data.json`** —
    GitHub's *floating* latest-release pointer, not a fixed tag. Any new
    non-prerelease release silently redirects every installed app's feed.
    Mark anything else as a pre-release.
12. **`docs/sw.js` caches the dashboard under a hand-maintained
    `VERSION = "v1"`.** Change the page without bumping it and home-screen
    installs keep the old shell. The Pages workflow will not catch this.
13. **`.build-outputs/app-debug.apk`** is an 18 MB APK committed at the root
    commit, not gitignored, refreshed by nothing. It is neither the current
    build nor the v1-stable one. Do not ship it.
14. **`v1-stable`'s rollback asset is named `app-debug.apk`**, not
    `kc-diamonds.apk` — it predates the rename and cannot be regenerated under
    the new name.

### Structural

15. **The batting aggregation exists twice** — `stats/BattingStats.kt` and
    again in JavaScript inside `docs/index.html` — with nothing checking that
    the two agree. The dashboard has no tests of any kind.
16. **The pitching data has no consumer.** 81 KC pitching rows, 110 opposing
    pitching rows and 429 opposing batting lines are collected, checksummed
    and verified on every run, and neither the app nor the dashboard reads any
    of it.
17. **`actions/checkout@v4` and `actions/setup-node@v4`** warn about Node 20
    deprecation on every run.
18. **The scrape cannot be tested locally.** The sites are unreachable from a
    development sandbox, so DOM and selector changes have historically shipped
    unverified. Reason about failure modes; you cannot observe them.
19. **Every script uses relative paths and must run from the repo root.** Run
    `update-seed.py` elsewhere and it finds no capture, swallows the
    `FileNotFoundError`s and exits 0 with `SEED_CHANGED=no` — indistinguishable
    from a successful no-op.

## 8. TODO, in priority order

1. **Finish the seasonal work before next season.** The three dated breakages
   (§7 items 1–3) are fixed. What is left of that same piece of work is to
   restore the four daily crons in `scrape-data.yml`, and to check the two
   season boundaries in `season_for()` — 3 Aug and 1 Oct — against whatever
   schedule the league actually publishes for the new year. The code no
   longer assumes the year; it still assumes the shape of the 2026 calendar.
2. **Decide the Kotlin Multiplatform branch** (§9). Built, tested, on
   TestFlight; the longer it sits the more it drifts from `main`.
3. **Write a real Room migration before any schema change** (§7 item 4). This
   is the one that loses a user's data if forgotten.
4. **Fix the `systemic` precedence bug and `canonical()`'s `.title()`**
   (§7 items 5 and 6). Both make the verifier quietly weaker than it reads.
5. **Remove `build-apk.yml`'s trigger on the dead branch** (§7 item 10). One
   line, removes a foot-gun that publishes stale data to every phone.
6. **Give the pitching data a consumer, or stop collecting it** (§7 item 16).
   It is fully parsed and verified and entirely unread.
7. **Unify the stats engine** so Kotlin and the dashboard cannot disagree, or
   add a test that runs one fixture through both.
8. **Fix the shortfall guard's gated double-count** (§7 item 7).
9. **Raise the `warnings[:20]` print cap** so new warning classes are visible.
10. **Replace the Node 20 actions** when GitHub stops forcing them onto 24.
11. **Decide whether the 2026 data is final**, and whether the weekly
    off-season scrape should stop rather than run all winter. It is currently
    watching for the official aggregates to reconcile; nobody has decided how
    long to wait.
12. **Add any smoke test at all for the dashboard.**

## 9. Branches

| Branch | Merged into `main`? | Status |
| --- | --- | --- |
| `claude/pitching-parser` | **Yes** | Done. Safe to delete. |
| `claude/kmp-ios-app` | **No** — 7 commits ahead | Live work. See below. |
| `claude/kc-diamonds-stats-app-r7de5h` | **No — and it never can be** | Dead history. See below. |

One further `claude/` branch in this repository belongs to a different app
entirely and is not part of this project.

### `claude/kmp-ios-app` — unmerged, and the thing to decide

Converts the app to **Kotlin Multiplatform with Compose Multiplatform**, so an
iPhone app runs the same screens, database and sync. `app/` becomes a thin
Android shell; everything real moves to a new `shared/` module; `ios/` holds an
XcodeGen project and a SwiftUI entry point.

**State: built, tested and verified on both platforms.** Android unit tests
pass and a test APK is published to the `shared-code-test` release. The shared
code compiles for a real iPhone, links, and passes its tests on a simulator. A
build has reached **TestFlight** successfully.

Android is intended to be indistinguishable. The application id, the signing
key, `kc_diamonds.db`, its schema version and the sync's stored settings are
all unchanged, so it installs as an update and keeps its data.

**Two things a reviewer should know:**

- **Landscape on a phone is genuinely different.** From 600dp the app moves
  the tabs to a left rail and opens a player or game beside the list. An
  ordinary phone in landscape exceeds that. This is the layout the iPhone
  needs, but it is a visible change to Android and was flagged as a decision,
  not an oversight.
- **Android-only APIs had to be replaced** — `org.json`, `String.format`,
  `MessageDigest`, OkHttp. Each replacement ships with a test comparing it
  against the Android API it stands in for, because a wrong rounding or a
  wrong hash is a silent wrong number rather than a crash.

`ios/APPLE-SETUP.md` on that branch documents the Apple and GitHub setup. The
TestFlight pipeline uses **cloud signing**: no certificate or provisioning
profile is stored in this repository. It reads four repository secrets, named
in that file; none of their values appear anywhere in the repo.

### `claude/kc-diamonds-stats-app-r7de5h` — dead history, do not push

`git merge-base` against `main` returns **nothing**: the two share no ancestry
at all. `main` is a 70-commit history beginning 17 Sep 2026; this branch is
the original 29-commit history from "Initial commit" on 23 May, ending 18 Jul.
Diffed against `main` it is roughly 17,600 lines *behind*, not ahead.

It is a frozen archive of the pre-September app, superseded wholesale. It
cannot be merged and must not be pushed to. Keep it or delete it, but do not
try to reconcile it.

---

## 10. Working on this

- **`main` is authoritative.** Develop on a branch and open a pull request.
- **Never commit a key, token or password.** The debug keystore in the
  repository is deliberate and is not a secret in any meaningful sense; the
  release signing config and the Apple pipeline both read from the
  environment.
- **Do not hand-edit `seed.json` or `scraped/`.** They are generated. Fix the
  parser and re-run.
- **Kick a scrape** by appending a line to `scrape-trigger.txt` and pushing.
- **When two sources disagree, find out why** rather than picking one. That
  rule has paid for itself more than once here — the 27-14 versus 26-13
  discrepancy in §3 turned out to be real and explicable, and "fixing" it
  would have corrupted a correct seed.
