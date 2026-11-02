# KC Diamonds — Claude Code session archive

**Repository:** `inspectorgad/markdown`
**Span:** 16 July 2026 → 2 November 2026
**Commits attributable to this session:** 37, across five working clusters
**Archived:** 2 November 2026

---

## How to read this, and what it is worth

This is a record of one long Claude Code session: what was asked for, what was
built, what broke, and why the surviving code looks the way it does.

**A note on provenance, because it affects how much to trust each section.**
The commit record below is authoritative for the whole span — every commit
listed carries this session's trailer and was checked against `git log`, not
recalled. The *narrative* detail is not uniform:

- **18 October → 2 November** is recounted in full, from the working
  conversation.
- **16–17 July** and **10 October** are reconstructed from their commits,
  their diffs and the documentation they produced. The commit messages in this
  repository are unusually full — prose, several paragraphs, naming the
  incident — so this reconstruction is solid on *what* and *why*, and thin on
  the back-and-forth that produced it.

Where a section is reconstructed rather than remembered, it says so.

For the maintainer's view of the system as it stands — architecture, the
pipeline, the decisions, the known bugs — read **`CLAUDE.md`**. This archive is
the story; that file is the manual, and it was written during this session
precisely so the story would stop being load-bearing.

---

## The project in one paragraph

An Android app tracking the **KC Diamonds**, a professional softball team, with
a data pipeline that scrapes their published results so nobody has to type in a
box score, and a web dashboard over the same data. Everything flows through one
file, `app/src/main/assets/seed.json`: the app bundles it, the app syncs a copy
of it, the dashboard fetches it. The 2026 season finished 8 August at 27-14
across 41 scored games, with a full box score for every one.

---

## Timeline

| Dates | Cluster | Commits | Landed on |
| --- | --- | --- | --- |
| 16–17 Jul | The app, the pipeline, the releases | 18 | `claude/kc-diamonds-stats-app-r7de5h` |
| 10 Oct | Pitching, and the opponent's half of the box | 4 | `claude/pitching-parser` → merged to `main` |
| 19–20 Oct | Capture guards, and the off-season cadence | 4 | `main` |
| 28–29 Oct | Kotlin Multiplatform and the iPhone app | 7 | `claude/kmp-ios-app` (unmerged) |
| 1 Nov | Handoff documentation, and the 2027 fixes | 4 | `claude/handoff-claude-md` → merged as PR #1 |

### A structural fact worth noticing

**The first eighteen commits are on a branch that can never be merged.**
`claude/kc-diamonds-stats-app-r7de5h` holds the July work; `main`'s own history
does not begin until 17 September and shares *no common ancestor with it* —
`git merge-base` between the two returns nothing at all. Diffed against `main`,
that branch is roughly 17,600 lines *behind*, not ahead.

So the work of 16–17 July exists twice: once as that branch's literal commits,
and once as the system it became, re-landed on a separate lineage. The branch
is a frozen archive of the pre-September app, superseded wholesale. Keep it or
delete it; do not try to reconcile it. This was established during the 1 Nov
documentation work and is written up in `CLAUDE.md` §9.

---

## Cluster 1 — 16–17 July: the app, the pipeline, the releases

*Reconstructed from commits and their diffs.*

The repository began life as a markdown-editor demo. `ce73a9b` replaced it
wholesale with the KC Diamonds app, and the next seventeen commits built out
everything the project still rests on, in a single day and a morning:

- **The app** — Kotlin, Jetpack Compose, Room. A seeded database built from a
  bundled JSON artifact with the confirmed official roster (`d0734a6`).
- **CI and the APK** (`8d4a2de`, `10037a1`) — a debug build, published
  automatically. Pinned to Gradle 9.3.1, the minimum AGP 9.1.1 would accept.
- **The scraper**, iterated hard across six commits (`be684ae` → `70871a7`)
  from a first browser-based workflow to "scraper v5": discover every game on
  the league platform, visit each, capture KC's half of the box score. One
  commit message records that `workflow_dispatch` was not permitted for the
  integration, so the scrape had to be kicked by pushing (`0719ec8`) — the
  `scrape-trigger.txt` convention still in use today.
- **The first full season** (`a0699c2`) — 25 games, 21 complete box scores.
- **The nightly pipeline** (`8b30312`) — scrape → parse → reseed → rebuild.
- **The releases** — `latest-apk` as a rolling one-tap download (`ed755d7`),
  then `v1-stable` frozen as an immutable rollback (`eb43518`).
- **In-app sync** (`2d2b1ef`) — the app fetching `season-data.json` over the
  network rather than waiting for a new APK, with the scrape workflow uploading
  that feed itself (`86ac3b3`), because pushes made with the CI token do not
  trigger other workflows. That constraint is still true and still load-bearing.

Almost every architectural decision the project still lives with was made in
these eighteen commits.

---

## Cluster 2 — 10 October: pitching, and the opponent's half

*Reconstructed from commits.*

Four commits on `claude/pitching-parser`, since merged:

1. `6c1c2f4` — **capture the opponent's box by clicking its own toggle.** The
   box score opens on the away team. For a road game, clicking "KC Diamonds"
   changed nothing, so both halves were saved as KC's and the opponent was
   missing from exactly the 16 away games. The fix clicks the opponent's own
   label — which is why the code uses the *truncated line-score spelling*
   ("Atlanta Smok", "New York Ris"): that is what the clickable toggle says.
   Normalising it to the seed's spelling breaks the click.
2. `cffb489` — a prototype parser for the pitching table, which until then had
   been scraped and discarded.
3. `1bca7bb` — pitching folded into the seed.
4. `c2b8ea2` — the opponent's half stored too.

This is where **innings became outs**. A box score's `5.1` means five and one
third; storing it as the decimal `5.1` makes every ERA quietly wrong by a
little, which is worse than loudly wrong. It is stored as `16`.

It is also where the project acquired its largest piece of dead weight: 81 KC
pitching rows, 110 opposing pitching rows and 429 opposing batting lines are
now collected, checksummed and verified on every run, and **neither the app nor
the dashboard reads any of it.** That is `CLAUDE.md` §7 item 16 and TODO item 6
— give it a consumer or stop collecting it.

---

## Cluster 3 — 19–20 October: two capture incidents, and the off-season

This is where the conversation record begins in full.

### The 18 October incident — a thin capture that passed quietly

A run captured **33 games instead of 44**. Nothing noticed, because nothing
compared a run's harvest against the last one, and the seed's gap-filling merge
meant the missing games simply stayed as they were. A collection failure that
leaves correct data behind is invisible by construction.

**Fix (`10d5b42`): floor game-id discovery on the seed.** Discovery now unions
in every `srcId` the seed already holds, so a short listing page cannot shrink
a run. A shortfall marker is written and the workflow fails on it:

```js
const reached = boxOk + gated;
if (priorBox && reached < priorBox) {
  fs.writeFileSync('scraped/CAPTURE-SHORTFALL.txt', `${note}\n`);
  console.log(`::error::capture shortfall: ${note}`);
}
```

That `reached = boxOk + gated` line carries a known bug — `gated` is already
inside `boxOk`, so the guard over-reports exactly in the situation the gating
counter was added for. It is recorded as `CLAUDE.md` §7 item 7. The path has
never fired and is wholly untested.

### The 20 October incident — good captures overwritten by error stubs

Five pages failed with `ERR_TOO_MANY_REDIRECTS`. The error path wrote a stub
(`{id, url, error}`) over each one's good capture and deleted its screenshot.
**The seed survived on gap-filling — which is precisely what made the loss
invisible.**

**Fix (`667a57e`): preserve on failure.** The previous capture is moved aside
before a run and restored for any page that fails:

```js
const PREV = '.scrape-prev';
fs.rmSync(PREV, { recursive: true, force: true });
if (fs.existsSync('scraped')) fs.renameSync('scraped', PREV);
fs.mkdirSync('scraped', { recursive: true });
```

Plus a navigation retry (two attempts, five seconds apart). The five lost
captures were restored from `6e00764` and proved byte-identical to what the
next live run produced — an independent confirmation that the restore was
correct, not merely plausible.

**One deliberate exception:** the stats snapshot is *not* preserved. It is what
`verify-seed.py` checks against, and checking today's seed against a stale copy
of yesterday's official aggregates would be worse than not checking at all.

`0c7db5e` kicked a scrape to confirm the capture recovered. It did.

### The off-season cadence (`dbdd3a9`)

The user noted there would be no more games until next summer. The scrape
dropped from four runs a day to one a week:

```yaml
- cron: '7 14 * * 1'    # replacing '7 2 * * *', '7 8 * * *', '7 14 * * *', '7 20 * * *'
```

Weekly rather than *off*, for two reasons that outlast the season: the official
aggregate page may still reconcile with the box scores it was built from, and
**a selector that rots in January is much better found in January than on
opening weekend.**

---

## Cluster 4 — 28–29 October: Kotlin Multiplatform and the iPhone app

The largest piece of work in the session. The request: convert the Android app
into one app for Android and iPhone, the same way a sibling project had been
done, with a pinned toolchain (Kotlin 2.4.21, KSP 2.3.12, AGP 9.2.1, Gradle
9.4.1, compileSdk 37, Compose Multiplatform 1.12.2, material3 1.12.0-alpha03,
Room 2.8.5, sqlite-bundled 2.6.2), TestFlight builds via GitHub Actions with
cloud signing, internal testing only, and the data pipeline left working
unchanged.

The binding constraint: **on Android it must look and behave exactly as it does
today**, keeping the application id, the signing key, the database file and
every migration, so it installs as an update and keeps its data.

### What was built (`9eb571e`, 7 commits total)

`app/` becomes a thin Android shell. Everything real moves to a new `shared/`
module — the view model, the Room database, the sync, the stats engine, every
screen, the theme. `ios/` holds an XcodeGen project and a SwiftUI entry point.

**Android-only APIs had to be replaced** for code that compiles on both
platforms: `org.json` → a small JSON shim; `String.format` → a fixed-decimal
helper; `MessageDigest` → a pure-Kotlin SHA-256; OkHttp → a platform `Http`
abstraction (OkHttp on Android, `NSURLSession` on iOS); `SimpleDateFormat` →
`kotlin.time.Clock`; `SharedPreferences` → a platform settings abstraction.
**Each replacement ships with a test comparing it against the Android API it
stands in for**, because a wrong rounding or a wrong hash is a silent wrong
number rather than a crash.

The rollback guard moved across intact:

```kotlin
internal fun olderThan(seed: String?, feed: String?): Boolean =
    seed != null && feed != null && STAMP.matches(seed) && STAMP.matches(feed) && seed < feed
```

Both stamps must be readable. Holding the bundled seed back on a *guess* would
strand a phone with no data at all.

### What went wrong, and was caught

- **`7ce3752` — the first shared build failed** on two unresolved imports.
  `TextAutoSize` lives in `androidx.compose.foundation.text`, not
  `androidx.compose.ui.text`; `Clock` had moved to `kotlin.time.Clock`.
- **`90f63ef` — three defects a review of the conversion turned up.** The most
  serious: the Material 3 1.4 parity compensations (selected-pill sizing, label
  colour) had been applied to the new landscape rail but **not to the bottom
  navigation bar** — the component every upright Android phone actually uses.
  A second: `SeasonSync` had lost its `withContext(Dispatchers.IO)`, so JSON
  parsing and SHA-256 would have run on the main thread, and both had become
  *slower* in the port (plain Kotlin versus the platform implementations).
- **A test copied from the sibling project referenced a fixture that does not
  exist here** — the single failure out of 45. Repointed at a real captured box
  score.

### A correction made to the user

I initially described the landscape behaviour as "a new thing to try." That was
wrong, and five independent reviewers flagged it: **from 600dp the app moves to
a left rail with list-beside-detail, and an ordinary phone in landscape exceeds
that threshold.** Before the conversion, landscape was portrait with a bottom
bar. It is a visible change to Android, not an incidental one. I corrected this
and put three options to the user — keep it, raise the threshold, or lock to
portrait — recommending keep.

**That question was never answered, and it is why the KMP branch's pull request
was never opened.**

### State at the end of the cluster

Built, tested and verified on both platforms. Android unit tests pass; a test
APK is published to the `shared-code-test` pre-release
(`kc-diamonds-shared-test.apk`, from commit `90f63ef`, 28 Oct). The shared code
compiles for a real iPhone, links, passes its tests on a simulator, and **a
build reached TestFlight successfully.**

---

## Cluster 5a — 29 October: the Apple and GitHub setup

Three commits of documentation, written because the user had not yet done the
App Store Connect and GitHub steps and asked for them in detail.

- `eb956f7` — **`ios/APPLE-SETUP.md`, written out click by click.**
- `24d45f1` — a correction. The user sent a screenshot of the Actions tab
  asking what they were supposed to do; the "Run workflow" button was not
  there. Verified against GitHub's own documentation: **the button only appears
  once the workflow file is on the repository's default branch.** Added to two
  parts of the guide plus two troubleshooting entries.
- `efa435a` — **check the key may sign, not merely that Apple accepts it.** A
  credential that authenticates but lacks signing authority fails later, in a
  45-minute Mac build, rather than immediately.

The user completed the Apple steps and the GitHub secrets, and the key check
passed 13 of 13.

**Nothing in this archive, in `CLAUDE.md`, or in the repository contains a key,
token, certificate or password.** The TestFlight pipeline uses cloud signing:
no certificate or provisioning profile is stored here. It reads repository
secrets whose names are given in `ios/APPLE-SETUP.md` and whose values appear
nowhere in the repo. The committed debug keystore is deliberate and is not a
secret in any meaningful sense — a stable signature is what lets every build
install over the last one and keep its data.

---

## Cluster 5b — 1 November: the handoff, and three dated bugs

The project was moving to a different account. The request: write `CLAUDE.md`
as a complete handoff, update the README, and open a pull request.

### The documentation (`b581b39`, `927526f`)

`CLAUDE.md` in ten sections — state, architecture, the scrape, builds and
releases, the decisions and their reasoning, conventions, known bugs, the TODO
list in priority order, the branches, and how to work on it.

The parts worth writing down were never the file listing. They are the things
that look arbitrary until you know the incident behind them, and each got a
paragraph: `srcId` as the key because a doubleheader collapsed two games into
one row; innings as outs; a gated table parsing to *nothing* rather than zeros,
because a dash is an absence and a zero is a claim; the capture committed even
when verification fails, because discarding it took the evidence with it.

A section titled **"Things that look like oversights but are not"** records
eight behaviours that have each been tried the other way and reverted.

The README was rewritten too. It had drifted into describing the app's first
week — it still claimed seeding "only runs when the database is empty," which
stopped being true when the merge became gap-filling, and it predated the
Standings tab, the network sync, the whole pipeline and the dashboard.

### How to read a healthy verify-seed run

Written down explicitly because the numbers look alarming and are not:

> It reports **0 failures and ~36 warnings**, and that is the steady state, not
> a backlog. The official aggregate page counts the regular season only, so it
> reads 26-13 where the seed correctly reads 27-14, and the same exclusion makes
> about twenty per-player totals read as over-counts. Every game in the seed is
> corroborated by the published schedule. **Only a verify-seed *failure* is an
> alert.**

This is the clearest instance of a rule that has paid for itself repeatedly
here: *when two sources disagree, find out why rather than picking one.*
"Fixing" the 27-14 to match the aggregate page would have corrupted a correct
seed.

### The three dated breakages (`cd66174`)

Reading the repository systematically turned up three bugs that were fine that
day and **certain to fire on a known date** — the worst shape a bug can have.

**1. `verify-seed.py` hard-coded the year `2026`.** The league platform's
schedule rows carry no year at all (`Sat 8/8`), so the year had to come from
somewhere, and it had been written in. From the first 2027 game, every scored
game in the seed would have failed as uncorroborated and the pipeline would
have refused to ship any seed at all.

Replaced by `season_year()`, which takes the latest four-digit year from the
team-site captures (they do print `Oct 17, 2026`). Two consequences:

- The corroboration check **and** the seed-side aggregates are now scoped to
  that one season, because the pages upstream publish one season at a time. An
  all-time seed compared against a one-season page reads as every batter
  over-counting — the same dated breakage by a different route.
- When no captured page carries a year, the check is **skipped with a warning**
  rather than run on a guess. A guessed year fails every game, which is exactly
  the failure being replaced.

**2. `season_for()` hard-coded `2026-10-01` and `2026-08-03`.** Worse than
stale — it *inverts*. The comparisons are on strings, and `'2027-06-15' >=
'2026-10-01'` is **true**, so every 2027 game would have been labelled
`2026 Fall` and filed under a season that had already ended. The year now comes
from the date itself.

⚠️ **The month-and-day boundaries are still the 2026 calendar and are a guess
about any other year.** That is now stated in the docstring and in `CLAUDE.md`.
A wrong boundary mislabels the games around it; the wrong year mislabelled all
of them.

**3. The scraper's context climb** — `/\b(Jun|Jul|Aug|Sep|Oct)\s+\d{1,2},\s*2026/`.
The silent one, and so the worst. In 2027 it matches nothing: the climb runs to
its limit, every captured `context` quietly degrades to a page-navigation blob,
and the parser loses its game-id → date mapping **while the file still looks
entirely valid**. Widened to all twelve months and any four-digit year, which
is what the parser already accepted at the other end.

### How the fixes were checked

The standing rule is that a fix must never write zeros or drop rows, so none of
this was assumed:

| Check | Result |
| --- | --- |
| Against the committed capture | **Byte-identical**: 27-14, 41 scored games, 41 box scores, `SEED_CHANGED=no`, 0 failures and the same 36 warnings line for line |
| Simulated 2027 run | Old script: **75 failures, exit 1.** New script: **passes** |
| A genuine 2027 misdating | Still caught and still named — scoped, not defanged |
| Capture with no dated page | Warns and continues rather than failing |

The scrape cannot be exercised from a development sandbox — the sites are
unreachable — so the scraper change was verified the only way available: the
widened pattern matches **exactly the same 34 captured contexts** as the old
one, and no captured page contains a date in a month the widening newly admits.
It is inert on 2026 data by construction.

### The cron work, stopped deliberately (`1d9f0be`)

The last substantive request was to restore the four daily crons. An audit was
started first, because these would fire roughly 850 times before opening day
and because the season-scoping landed an hour earlier changes what a run does.

**The user stopped it mid-flight** — the project was moving to their personal
account — and asked for it to be written down instead. It is now `CLAUDE.md`
TODO item 1, carrying the exact crons, a warning that the comment block above
them argues for the off-season cadence and must be rewritten in the same edit,
and the check that has to come first:

> Between roughly March and May 2027 the team site will list the 2027 schedule
> while no 2027 game has been played, so `season_year()` returns `2027` and
> `in_season()` excludes **every game in the seed**. Two questions, in order:
> does it exit 1 — and does it **pass vacuously**? The second is worse. A
> verifier that checks nothing while reporting success is how a bad seed ships
> unnoticed.

**That question is open and is the first thing the next maintainer should
settle.**

### PR #1

Opened as a documentation pull request, then rewritten when the fixes landed on
the same branch — it had opened with the words "two files, no code," and that
stopped being true. **Merged into `main` on 1 November** (`c895a67`): 4 commits,
5 files, +755 / −54.

---

## Where things stand

### Branches

| Branch | Merged into `main`? | Status |
| --- | --- | --- |
| `claude/handoff-claude-md` | **Yes** — PR #1, 1 Nov | Done. Safe to delete. |
| `claude/pitching-parser` | **Yes** | Done. Safe to delete. |
| `claude/kmp-ios-app` | **No** — 7 commits ahead | Built, tested, on TestFlight. Awaiting a decision. |
| `claude/kc-diamonds-stats-app-r7de5h` | **No — and it never can be** | Dead history. Do not push. |

One further `claude/` branch in this repository belongs to a different app
entirely and is not part of this project.

### Releases

| Tag | What it is |
| --- | --- |
| `latest-apk` | Rolling. Carries `kc-diamonds.apk` **and** `season-data.json` — both the download link and the app's sync feed. |
| `v1-stable` | Frozen July build, the rollback if a newer one misbehaves. |
| `shared-code-test` | Pre-release. The Kotlin Multiplatform test APK from `90f63ef`. |

### Open questions carried out of this session

1. **The landscape layout on the KMP branch.** Keep the two-pane layout on a
   phone in landscape, raise the threshold, or lock to portrait? Unanswered,
   and the reason no pull request was opened for that branch.
2. **The March–May 2027 season-rollover case** described above. Settle it
   before restoring the daily crons.
3. **The Android test APK verdict** — the user was asked to try it, in
   particular the Standings tab in airplane mode, and had not reported back.
4. Everything else is in `CLAUDE.md` §8, in priority order.

---

## What this session kept getting right, and what it got wrong

Worth recording, because the pattern repeats.

**The failures that mattered were all silent ones.** A thin capture that
gap-filling made invisible. Error stubs written over good data, hidden by the
same mechanism. A regex that would stop matching and degrade to a plausible
wrong answer. A season label that inverts rather than going stale. In every
case the system kept reporting success. The guards added in response are
mostly not *checks that something is right* — they are checks that something
was actually **reached**, actually **compared**, actually **published**.

**Fail closed, and never let a fix write zeros.** A gated table parses to
nothing rather than a row of zeros. A re-derive that would more than halve a
game's at-bats is refused outright. A missing year skips a check rather than
guessing one. Each of these was a decision to produce *less* data rather than
confident wrong data.

**Verify before pointing a parser somewhere new.** Before fixing the hard-coded
year, the captures were actually checked for whether they contained a year at
all — they do not, which ruled out the obvious fix. Before blaming the upstream
site for a redirect loop, the live site was probed and the sandbox proxy turned
out to be the cause.

**Where I was wrong:** I described the landscape change as something to try,
when it was a visible behaviour change on ordinary hardware; reviewers caught
it and I corrected it to the user. I also applied a set of visual parity fixes
to the new navigation rail and missed the bottom bar that every upright phone
actually uses — caught in review, before it shipped.

---

*Archived from a Claude Code session. The commit record is authoritative; the
narrative for 16 July–17 October is reconstructed from commits and their
diffs, and says so where it is.*
