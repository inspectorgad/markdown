# KC Diamonds — Claude Code session archive

**Repository:** `inspectorgad/markdown`
**Span:** 16 July 2026 → 2 November 2026
**Commits attributable to this session:** 38 — 37 of project work in five
clusters, plus the commit that added this file
**Archived:** 2 November 2026

---

## How to read this, and what it is worth

This is a record of one long Claude Code session: what was asked for, what was
built, what broke, and why the surviving code looks the way it does.

**A note on provenance, because it affects how much to trust each section.**
The commit record below is authoritative for the whole span — every commit
listed carries this session's trailer and was checked against `git log`. The
*narrative* detail is not uniform:

- **18 October → 2 November** is recounted in full, from the working
  conversation.
- **16–17 July** and **10 October** are reconstructed from their commits,
  their diffs and the documentation they produced. The commit messages in this
  repository are unusually full — prose, several paragraphs, naming the
  incident — so this reconstruction is solid on *what* and *why*, and thin on
  the back-and-forth that produced it.
- **18 July → 9 October** is a twelve-week hole with **no session commits at
  all**, and this archive cannot account for it. See the note after the
  timeline; it is the largest gap in the record and it is not a small one.

Where this document quotes another file, it quotes it verbatim or says it is
paraphrasing. Where a figure came from a command, the command was re-run before
publication. Where something is a recollection that nothing in the repository
can corroborate, it says so in the text.

For the maintainer's view of the system as it stands, read **`CLAUDE.md`**.
This archive is the story; that file is the manual.

> **`CLAUDE.md` carries four errors this archive originally repeated.** They
> were found by fact-checking this document against the code, and `CLAUDE.md`
> is already merged into `main`. They are listed at the end, under
> *[Corrections owed to `CLAUDE.md`](#corrections-owed-to-claudemd)*. If you
> are picking this project up, read that section before trusting §3, §5, §7
> item 16 or §9 of the briefing.

---

## The project in one paragraph

An Android app tracking the **KC Diamonds**, a professional softball team, with
a data pipeline that scrapes their published results so nobody has to type in a
box score, and a web dashboard over the same data. Everything flows through one
file, `app/src/main/assets/seed.json`: the app bundles it, the app syncs a copy
of it, the dashboard fetches it. The league season finished **8 August** at
**27-14** across 41 scored games, with a full box score for every one. Four
further fixtures sit in the seed dated 10 and 17 October, labelled `2026 Fall`
and **unscored** — exhibitions against college sides for which no box score was
ever published. Whether they were played and not published, or never played, is
not resolvable from the sources; the seed holds them as rows with no result.

---

## Timeline

| Dates | Cluster | Commits | Landed on |
| --- | --- | --- | --- |
| 16–17 Jul | The app, the pipeline, the releases | 18 | `claude/kc-diamonds-stats-app-r7de5h` |
| *18 Jul – 9 Oct* | *no session commits — see below* | *0* | — |
| 10 Oct | Pitching, and the opponent's half of the box | 4 | 1 direct to `main`; 3 via `claude/pitching-parser` |
| 19–20 Oct | Capture guards, and the off-season cadence | 4 | `main` |
| 28–29 Oct | Kotlin Multiplatform and the iPhone app | 7 | `claude/kmp-ios-app` (unmerged) |
| 1 Nov | Handoff documentation, and the 2027 fixes | 4 | `claude/handoff-claude-md` → merged as PR #1 |
| 2 Nov | This archive | 1 | `claude/session-archive` |

### The twelve-week hole, stated plainly

Between **18 July and 9 October** this session produced nothing. `main`'s own
history does not begin until **17 September**, and by its first commit a great
deal is already in place that the July branch never had.

That is not a gap in activity — it is a gap in *this record*. Work happened;
this session did not do it, and this archive cannot describe it. Anyone relying
on this document should treat the period as undocumented rather than as quiet.

### A structural fact worth noticing

**The first eighteen commits are on a branch that can never be merged.**
`claude/kc-diamonds-stats-app-r7de5h` holds the July work; `main` shares **no
common ancestor with it** — `git merge-base` between the two returns nothing at
all.

Measured on 2 November 2026, that branch is **about 18,400 lines of additions
behind `main`**, against roughly 2,250 it has that `main` does not. (The figure
was 17,672 when `CLAUDE.md` was written on 1 November, before PR #1 landed; it
moves whenever `main` does, so re-derive rather than trusting either number.)

The branch is a frozen archive of the pre-September app, superseded wholesale.
Keep it or delete it; do not try to reconcile it.

---

## Cluster 1 — 16–17 July: the app, the pipeline, the releases

*Reconstructed from commits and their diffs.*

The repository began life as a markdown-editor demo. `ce73a9b` replaced it
wholesale with the KC Diamonds app, and the next seventeen commits built out a
working system in a single day and a morning:

- **The app** — Kotlin, Jetpack Compose, Room, seeded from a bundled JSON
  artifact with the confirmed official roster (`d0734a6`).
- **CI and the APK** (`8d4a2de`, `10037a1`) — pinned to Gradle 9.3.1, the
  minimum AGP 9.1.1 would accept.
- **The scraper**, iterated hard across six commits (`be684ae` → `70871a7`)
  to "scraper v5": discover every game on the league platform, visit each,
  capture KC's half of the box score. `0719ec8` records that
  `workflow_dispatch` was not permitted for the integration, so the scrape had
  to be kicked by pushing — the `scrape-trigger.txt` convention still in use.
- **The first full season** (`a0699c2`) — 25 games, 21 complete box scores.
- **The nightly pipeline** (`8b30312`) — scrape → parse → reseed → rebuild.
- **The releases** — `latest-apk` as a rolling one-tap download (`ed755d7`),
  then `v1-stable` frozen as an immutable rollback (`eb43518`).
- **In-app sync** (`2d2b1ef`) — the app fetching `season-data.json` over the
  network rather than waiting for a new APK, with the scrape workflow uploading
  that feed itself (`86ac3b3`), because pushes made with the CI token do not
  trigger other workflows. That constraint is still true and still load-bearing.

**What was *not* decided here**, despite how foundational the cluster feels.
Checked directly against the branch, all of the following are **absent** from
it and arrived later, on `main`'s separate lineage:

| Absent from the July branch |
| --- |
| `srcId` as the game key — and the doubleheader bug that forced it |
| `scripts/verify-seed.py` — the gate the whole pipeline now turns on |
| `docs/` — the web dashboard |
| The Standings tab |

So the honest summary is narrower than it first appears: these eighteen commits
established **the app, the scraper, the pipeline's shape and both releases**.
Several of the decisions the project now rests on are not among them.

---

## Cluster 2 — 10 October: pitching, and the opponent's half

*Reconstructed from commits.*

Four commits of 10 October work, on two paths. `6c1c2f4` landed **directly on
`main`**; the other three came through `claude/pitching-parser`, which was cut
from `main` a day later and fast-forwarded back in on 13 October — no merge
commit, no pull request. (`git branch --contains 6c1c2f4` does list the branch,
because the commit is an ancestor of its tip, but it was not part of its work.)

**On `main`:**

- `6c1c2f4` — **capture the opponent's box by clicking its own toggle.** The
  box score opens on the away team. For a road game, clicking "KC Diamonds"
  changed nothing, so both halves were saved as KC's and the opponent was
  missing from exactly the 16 away games. The fix clicks the opponent's own
  label — which is why the code uses the *truncated line-score spelling*
  ("Atlanta Smok", "New York Ris"): that is what the clickable toggle says.
  Normalising it to the seed's spelling breaks the click.

**On `claude/pitching-parser`:**

- `cffb489` — a prototype parser for the pitching table, until then discarded.
- `1bca7bb` — pitching folded into the seed.
- `c2b8ea2` — the opponent's half stored too.

This is where **innings became outs**. A box score's `5.1` means five and one
third; storing it as the decimal `5.1` makes every ERA quietly wrong by a
little, which is worse than loudly wrong. It is stored as `16`.

It is also where the project acquired its largest piece of dead weight. Counted
from `seed.json` on 2 November:

| Array | Rows | Read by anything? |
| --- | --- | --- |
| `lines` (KC batting) | 429 | **Yes** — the app's primary data |
| `opponentLines` | 372 | No |
| `pitching` (KC) | 81 | No |
| `opponentPitching` | 110 | No |

**563 rows** — the opposing batting lines plus both pitching tables — are
collected, checksummed and verified on every run and read by nothing. That is
`CLAUDE.md` §7 item 16 and TODO item 6: give it a consumer or stop collecting
it. (The briefing misstates this as "429 opposing batting lines"; 429 is KC's
own batting data, which the app very much does read. See the corrections
section.)

---

## Cluster 3 — 19–20 October: two capture incidents, and the off-season

This is where the conversation record begins in full.

### The 18 October incident — a thin capture that passed quietly

A run captured **33 games instead of 44**. Nothing noticed, because nothing
compared a run's harvest against the last one, and the seed's gap-filling merge
meant the missing games simply stayed as they were. A collection failure that
leaves correct data behind is invisible by construction.

**Fix (`10d5b42`): floor game-id discovery on the seed.** Discovery now unions
in every `srcId` the seed already holds, so a short listing page cannot shrink a
run. A shortfall marker is written and the workflow fails on it (elided here;
the real source builds `note` between the two statements):

```js
const reached = boxOk + gated;
if (priorBox && reached < priorBox) {
  // … const note = `reached ${reached} of ${priorBox} games …`
  fs.writeFileSync('scraped/CAPTURE-SHORTFALL.txt', `${note}\n`);
  console.log(`::error::capture shortfall: ${note}`);
}
```

That `reached = boxOk + gated` line carries a known bug — `gated` is already
inside `boxOk`, so the guard over-reports exactly in the situation the gating
counter was added for. It is `CLAUDE.md` §7 item 7. **The *gated* branch of it
has never fired and is untested; the guard itself has.** It fired the very next
day, and the marker is committed at `a78d96e`:

> `reached 36 of 41 games that already have batting lines in the seed (box
> scores ok: 36, login-gated: 0, ids fetched: 44)`

That is the guard doing its job on the incident below, one day after being
added.

### The 20 October incident — good captures overwritten by error stubs

Five pages failed with `ERR_TOO_MANY_REDIRECTS`. The error path wrote a stub
(`{id, url, error}`) over each one's good capture and deleted its screenshot.
**The seed survived on gap-filling — which is precisely what made the loss
invisible**, and what the shortfall marker above caught anyway.

**Fix (`667a57e`): preserve on failure.** The previous capture is moved aside
before a run and restored for any page that fails:

```js
const PREV = '.scrape-prev';
fs.rmSync(PREV, { recursive: true, force: true });
if (fs.existsSync('scraped')) fs.renameSync('scraped', PREV);
fs.mkdirSync('scraped', { recursive: true });
```

Plus a navigation retry (two attempts, five seconds apart). The five lost
captures were restored from `6e00764` and proved byte-identical to what the next
live run produced — independent confirmation that the restore was correct, not
merely plausible.

**One deliberate exception:** the stats snapshot is *not* preserved. It is what
`verify-seed.py` checks against, and checking today's seed against a stale copy
of yesterday's official aggregates would be worse than not checking at all.

`0c7db5e` kicked a scrape to confirm the capture recovered. It did.

### The off-season cadence (`dbdd3a9`)

The user noted there would be no more games until next summer. The scrape
dropped from four runs a day to one a week — `'7 14 * * 1'`, replacing
`'7 2 * * *'`, `'7 8 * * *'`, `'7 14 * * *'` and `'7 20 * * *'`.

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

### What was built

Four commits of conversion — `9eb571e`, `e32cd7d`, `7ce3752`, `90f63ef` — of
the seven on the branch; the other three are the setup documentation in Cluster
5a below.

`app/` becomes a thin Android shell. Everything real moves to a new `shared/`
module — the view model, the Room database, the sync, the stats engine, every
screen, the theme. `ios/` holds an XcodeGen project and a SwiftUI entry point.
`e32cd7d` adds **`ios/README.md`**, explaining what each file under `ios/` is
and what it needs; `90f63ef` also added `ios-shared-check.yml`, without which
nothing compiled the iPhone half on CI.

**Android-only APIs had to be replaced** for code that compiles on both
platforms:

| Android API | Replacement | Comparison test? |
| --- | --- | --- |
| `org.json` | a small JSON shim | **Yes** — `JsonCompatTest` |
| `String.format` | a fixed-decimal helper | **Yes** — `NumbersTest` |
| `MessageDigest` | a pure-Kotlin SHA-256 | **Yes** — `Sha256Test` |
| OkHttp | platform `Http` (OkHttp / `NSURLSession`) | No |
| `SimpleDateFormat` | `kotlin.time.Clock` | No |
| `SharedPreferences` | a platform settings abstraction | No |

**Three of the six** ship with a test running the replacement and the Android
API side by side, because a wrong rounding or a wrong hash is a silent wrong
number rather than a crash. The other three do not, and the originating commit
scopes its claim more narrowly than this archive originally did.

### A behaviour change, not a port: the rollback guard

```kotlin
internal fun olderThan(seed: String?, feed: String?): Boolean =
    seed != null && feed != null && STAMP.matches(seed) && STAMP.matches(feed) && seed < feed
```

**This is new.** `olderThan` does not exist on `main` and never has — `main`'s
`Seeder.sync()` reads the bundled asset and goes straight to `merge()` with no
`generatedAt` comparison at all. The conversion *added* protection the shipping
Android app does not have: an APK installed over a phone that has already synced
newer data would otherwise roll the standings back on every launch.

Both stamps must be readable before the guard engages. Holding the bundled seed
back on a *guess* would strand a phone with no data at all.

This belongs on the reviewer's decision list alongside the landscape change
below — it is a deliberate difference from today's behaviour, not a port.

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
  *slower* in the port.
- **A test copied from the sibling project referenced a fixture that does not
  exist here** — the single failure out of 45. Repointed at a real captured box
  score.

### A correction made to the user

I initially described the landscape behaviour as "a new thing to try." That was
wrong: **from 600dp the app moves to a left rail with list-beside-detail, and an
ordinary phone in landscape exceeds that threshold.** Before the conversion,
landscape was portrait with a bottom bar. It is a visible change to Android, not
an incidental one. I corrected this and put three options to the user — keep it,
raise the threshold, or lock to portrait — recommending keep.

*(The review that surfaced it ran during the session and left no artifact in the
repository; this paragraph is recollection, not something a reader can verify
from `git`.)*

**That question was never answered, and it is why the KMP branch's pull request
was never opened.**

### State at the end of the cluster

Built and tested on both platforms. Android unit tests pass; a test APK is
published to the `shared-code-test` pre-release (`kc-diamonds-shared-test.apk`,
from commit `90f63ef`, 28 Oct). The shared code compiles for a real iPhone,
links, and passes its tests on a simulator.

The session also recorded that a build reached **TestFlight** successfully.
That happened outside the repository and **cannot be confirmed from anything
committed here** — the evidence is the workflow definition and the session's own
account, not an artifact. Treat it accordingly.

---

## Cluster 5a — 29 October: the Apple and GitHub setup

Three commits, written because the user had not yet done the App Store Connect
and GitHub steps and asked for them in detail.

- `eb956f7` — **`ios/APPLE-SETUP.md`, written out click by click.**
- `24d45f1` — a correction. The user sent a screenshot of the Actions tab asking
  what they were supposed to do; the "Run workflow" button was not there.
  Verified against GitHub's own documentation: **the button only appears once
  the workflow file is on the repository's default branch.** Added to two parts
  of the guide plus two troubleshooting entries.
- `efa435a` — **check the key may sign, not merely that Apple accepts it.** Not
  documentation: it adds 24 lines to `ios/check_apple_keys.py`. A credential
  that authenticates but lacks signing authority fails later, in an hour-long
  Mac build, rather than immediately.

The user completed the Apple steps and the GitHub secrets, and the key check
passed 13 of 13.

**Nothing in this archive, in `CLAUDE.md`, or in the repository contains a key,
token, certificate or password.** The TestFlight pipeline uses cloud signing: no
certificate or provisioning profile is stored here. It reads repository secrets
whose names are given in `ios/APPLE-SETUP.md` and whose values appear nowhere in
the repo. The committed debug keystore is deliberate and is not a secret in any
meaningful sense — a stable signature is what lets every build install over the
last one and keep its data.

---

## Cluster 5b — 1 November: the handoff, and three dated bugs

The project was moving to a different account. The request: write `CLAUDE.md` as
a complete handoff, update the README, and open a pull request.

### The documentation (`b581b39`, `927526f`)

`CLAUDE.md` in ten sections — state, architecture, the scrape, builds and
releases, the decisions and their reasoning, conventions, known bugs, the TODO
list in priority order, the branches, and how to work on it.

The parts worth writing down were never the file listing. They are the things
that look arbitrary until you know the incident behind them: `srcId` as the key
because a doubleheader collapsed two games into one row; innings as outs; a
gated table parsing to *nothing* rather than zeros, because a dash is an absence
and a zero is a claim; the capture committed even when verification fails,
because discarding it took the evidence with it.

A section titled **"Things that look like oversights but are not"** records
eight behaviours that have each been tried the other way and reverted.

The README was rewritten too. It had drifted into describing the app's first
week — it still claimed seeding "only runs when the database is empty," which
stopped being true when the merge became gap-filling, and it predated the
Standings tab, the network sync, the whole pipeline and the dashboard.

### How to read a healthy verify-seed run

`CLAUDE.md` §3 tells the next maintainer that **0 failures and ~36 warnings is
the steady state, not a backlog**, and that only a *failure* is an alert. That
headline is sound and important.

Its *explanation* is not, and this archive repeated it before checking. §3 says
the official aggregate page "appears to count the regular season only," which is
why it reads 26-13 against the seed's 27-14. **The arithmetic does not support
that.** Computed from `seed.json`:

| | |
| --- | --- |
| All scored games | **27-14** |
| Postseason rows | 5 (four scored, **2-2**) |
| Regular season only | **25-12** |
| Official aggregate page | **26-13** |

Removing the postseason gives 25-12, not 26-13. The gap is exactly one win and
one loss — two games — which the season split does not explain. The code is more
careful than the prose: `verify-seed.py`'s own warning says the aggregate page is
"simply behind."

The project's own rule applies here, to its own documentation: *when two sources
disagree, find out why rather than picking one.* The honest statement is that
the seed is corroborated game by game against the published schedule, the
aggregate page is two games short, and **nobody has established why.**

A second figure in the same passage was wrong: "about twenty per-player totals"
read as over-counts. The real number is **31 over-count lines across 12
players** — and only 16 of them print, because `warnings[:20]` truncates the
output while the summary counts them all. That is §7 item 9 biting in practice.

### The three dated breakages (`cd66174`)

Reading the repository systematically turned up three bugs that were fine that
day and **certain to fire on a known date** — the worst shape a bug can have.

**1. `verify-seed.py` hard-coded the year `2026`.** The league platform's
schedule rows carry no year at all (`Sat 8/8`), so the year had to come from
somewhere, and it had been written in. From the first 2027 game, every scored
game in the seed would have failed as uncorroborated and the pipeline would have
refused to ship any seed at all.

Replaced by `season_year()`, which takes the latest four-digit year from the
team-site captures (they do print `Oct 17, 2026`). Two consequences:

- The corroboration check **and** the seed-side aggregates are now scoped to
  that one season, because the pages upstream publish one season at a time. An
  all-time seed compared against a one-season page reads as every batter
  over-counting — the same dated breakage by a different route.
- When no captured page carries a year, the check is **skipped with a warning**
  rather than run on a guess. A guessed year fails every game, which is exactly
  the failure being replaced.

**2. `season_for()` hard-coded `2026-10-01` and `2026-08-03`.** Worse than stale
— it *inverts*. The comparisons are on strings, and `'2027-06-15' >=
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
entirely valid**. Widened to all twelve months and any four-digit year.

The same commit also had to widen `update-seed.py`'s date regex at the other
end, from a literal `", "` to `",\s*"` — otherwise the scraper's looser finder
could accept a context the parser could not then read.

### How the fixes were checked

The standing rule is that a fix must never write zeros or drop rows, so none of
this was assumed:

| Check | Result |
| --- | --- |
| Against the committed capture | **Byte-identical**: 27-14, 41 scored games, 41 box scores, `SEED_CHANGED=no`, 0 failures and the same 36 warnings line for line |
| Simulated 2027 run | Old script: **75 failures, exit 1.** New script: **passes** |
| A genuine 2027 misdating | Still caught and still named — scoped, not defanged |
| Capture with no dated page | Warns and continues rather than failing |

*(The simulated-2027 figure is from the fixtures built during the session; a
later independent reconstruction with slightly different fixtures produced 73.
The direction — a pipeline that stops dead versus one that passes — is the
finding; the exact count depends on the scenario.)*

The scrape cannot be exercised from a development sandbox — the sites are
unreachable — so the scraper change was verified the only way available: the
widened pattern matches **exactly the same 34 captured contexts** as the old
one, and no captured page contains a date in a month the widening newly admits.
It is inert on 2026 data by construction.

### The cron work, stopped deliberately (`1d9f0be`)

The last substantive request was to restore the four daily crons. An audit was
started first, because these would fire many hundreds of times before opening
day and because the season-scoping landed an hour earlier changes what a run
does.

**The user stopped it mid-flight** — the project was moving to their personal
account — and asked for it to be written down instead. It is now `CLAUDE.md`
TODO item 1, carrying the exact crons, a warning that the comment block above
them argues for the off-season cadence and must be rewritten in the same edit,
and the check that has to come first. Paraphrasing it:

> Between roughly March and May 2027 the team site will list the 2027 schedule
> while no 2027 game has been played, so `season_year()` returns `2027` and
> `in_season()` excludes every game in the seed — leaving `seed_w`/`seed_l` at
> 0-0, an empty `agg`, and a per-player comparison against a stats capture that
> may still hold 2026 totals. Two questions, **in this order**: does it exit 1,
> and does it **pass vacuously**? The second is worse. A verifier that checks
> nothing while reporting success is how a bad seed ships unnoticed.

**That question is open and is the first thing the next maintainer should
settle.**

### PR #1

Opened as a documentation pull request, then rewritten when the fixes landed on
the same branch — its original body opened with the words "two files, no code,"
which stopped being true. *(That phrasing is from the session; GitHub does not
expose body edit history through the available tools, so it cannot be
re-verified from the PR as it now stands.)*

**Merged into `main` on 1 November** (`c895a67`): 4 commits, 5 files,
+755 / −54.

---

## Where things stand

### Branches

| Branch | Merged into `main`? | Status |
| --- | --- | --- |
| `claude/handoff-claude-md` | **Yes** — PR #1, 1 Nov | Done. Safe to delete. |
| `claude/pitching-parser` | **Yes** — fast-forwarded 13 Oct | Done. Safe to delete. |
| `claude/kmp-ios-app` | **No** — 7 commits ahead | Built and tested. Awaiting the landscape decision. |
| `claude/session-archive` | **No** — 1 commit ahead | Carries this document. |
| `claude/kc-diamonds-stats-app-r7de5h` | **No — and it never can be** | Dead history. Do not push. |

**One further `claude/` branch exists** —
`claude/ku-volleyball-app-feasibility-c4se1m` — which belongs to a different app
entirely and is not part of this project. That makes **six** on `origin`.
(`CLAUDE.md` §9 lists only three and is now stale on both counts; see below.)

### Releases

| Tag | What it is |
| --- | --- |
| `latest-apk` | Rolling. Carries `kc-diamonds.apk` **and** `season-data.json` — both the download link and the app's sync feed. |
| `v1-stable` | Frozen July build, the rollback if a newer one misbehaves. |
| `shared-code-test` | Pre-release. The Kotlin Multiplatform test APK from `90f63ef`. |

### Open questions carried out of this session

1. **The landscape layout on the KMP branch.** Keep the two-pane layout on a
   phone in landscape, raise the threshold, or lock to portrait? Unanswered, and
   the reason no pull request was opened for that branch. The rollback guard
   above is a second, smaller decision of the same kind.
2. **The March–May 2027 season-rollover case.** Settle it before restoring the
   daily crons.
3. **Why the aggregate page reads 26-13.** The postseason explanation does not
   survive arithmetic, and nothing has replaced it.
4. **The Android test APK verdict** — the user was asked to try it, in
   particular the Standings tab in airplane mode, and had not reported back.
5. Everything else is in `CLAUDE.md` §8, in priority order.

---

## Corrections owed to `CLAUDE.md`

`CLAUDE.md` merged into `main` on 1 November. Fact-checking **this** document
against the code turned up four errors it inherited from the briefing, which
means the briefing has them too. None has been fixed there.

| § | Claim | What the code shows |
| --- | --- | --- |
| **§7 item 16** | "429 opposing batting lines" are collected and unread | 429 is `lines` — **KC's own** batting data, which the app reads. The opposing figure is **372**. The unread total is 563 rows. |
| **§5** | `Seeder.olderThan()` holds the bundled seed back, described as live behaviour | `olderThan` **does not exist on `main`**. It is new in `9eb571e` on the unmerged KMP branch. The shipping app has no such guard. |
| **§9** | "Each replacement ships with a test comparing it against the Android API" | **Three of six** do: `JsonCompatTest`, `NumbersTest`, `Sha256Test`. No test for the HTTP, date or settings replacements. |
| **§3** | The aggregate page "appears to count the regular season only," explaining 26-13 vs 27-14 | Regular season only is **25-12**. The explanation does not account for the gap. Also "about twenty" over-counts is really **31 lines across 12 players**. |

Two further staleness items, both minor and both a consequence of this session's
own last few hours: §9's branch table predates PR #1 merging and the creation of
`claude/session-archive`, and §1's "There is no `main`-side work in flight" was
written before that merge.

---

## What this session kept getting right, and what it got wrong

**The failures that mattered were all silent ones.** A thin capture that
gap-filling made invisible. Error stubs written over good data, hidden by the
same mechanism. A regex that would stop matching and degrade to a plausible
wrong answer. A season label that inverts rather than going stale. In every case
the system kept reporting success. The guards added in response are mostly not
*checks that something is right* — they are checks that something was actually
**reached**, actually **compared**, actually **published**.

**Fail closed, and never let a fix write zeros.** A gated table parses to
nothing rather than a row of zeros. A re-derive that would more than halve a
game's at-bats is refused outright. A missing year skips a check rather than
guessing one. Each was a decision to produce *less* data rather than confident
wrong data.

**Verify before pointing a parser somewhere new.** Before fixing the hard-coded
year, the captures were checked for whether they contained a year at all — they
do not, which ruled out the obvious fix.

**Where it went wrong, and this is the part worth keeping.** Three mistakes, of
rising seriousness:

1. I described the landscape change as something to try, when it was a visible
   behaviour change on ordinary hardware. Caught in review.
2. I applied visual parity fixes to the new navigation rail and missed the
   bottom bar every upright phone uses. Caught in review, before it shipped.
3. **I wrote a handoff document containing four factual errors, and then wrote
   an archive that repeated them.** They were found only because this document
   was fact-checked against the code rather than against the documentation it
   came from. The 429 figure had been copied forward twice without anyone
   counting the array; the `olderThan` guard was described as protecting a
   shipped app it has never been part of; the 26-13 explanation was asserted as
   fact and fails on arithmetic any reader could do.

The third is the instructive one. Every one of those errors was *plausible*,
sat in a document whose whole purpose was to be trusted, and would have been
inherited by the next maintainer as settled fact. The project's own rule —
**when two sources disagree, find out why rather than picking one** — turns out
to apply to its documentation as forcefully as to its data, and the
documentation is where it was not being applied.

---

*Archived from a Claude Code session. The commit record is authoritative. The
narrative for 16 July–17 October is reconstructed from commits and their diffs;
18 July–9 October is undocumented; recollections that the repository cannot
corroborate are marked as such in the text. Every figure here was re-derived
from the repository on 2 November 2026.*
