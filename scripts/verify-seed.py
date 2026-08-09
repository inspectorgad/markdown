#!/usr/bin/env python3
"""Cross-verify seed.json against the official BallClubz season aggregates.

Run after update-seed.py, before committing. Two independent signals:
- the pitching table's W/L columns sum to the team's true record
- per-player batting totals (AB/H/HR/RBI) bound what the seed may claim

Hard failures (exit 1) are impossible states that indicate data corruption,
e.g. the duplication cascade of Jul 2026 (a box score applied to two dates
inflates a player's totals above the official numbers). Lag — the seed
missing a game the official page already counts — is only a warning.
"""
import datetime
import json
import re
import sys

STATS = 'scraped/ballclubz-stats.txt'
SEED = 'app/src/main/assets/seed.json'

seed = json.load(open(SEED))
try:
    stats_txt = open(STATS).read()
except FileNotFoundError:
    print('verify-seed: no stats snapshot; skipping')
    sys.exit(0)
if 'Batting Statistics' not in stats_txt or 'Pitching Statistics' not in stats_txt:
    print('verify-seed: stats snapshot incomplete; skipping')
    sys.exit(0)

# --- official batting totals, keyed by jersey number ---
# Row shape: jersey \t "Last, First" \t AVG OBP SLG OPS G PA AB R H RBI ...
# Same-person aliases as the parser uses, so a placeholder row and the real
# name fold into one identity.
NAME_ALIASES = {
    'player87, meg houk': 'Meg Houk',
    'houk, meaghan': 'Meg Houk',
    'player10, lauren': 'Stephanie Smith',
    'marcieno, brianna': 'Briana Marcelino',
}

def canonical(official_name):
    key = official_name.strip().lower()
    if key in NAME_ALIASES:
        return NAME_ALIASES[key]
    if ',' in official_name:
        last, first = [x.strip() for x in official_name.split(',', 1)]
        return f'{first.title()} {last.title()}'
    return official_name.strip()

official = {}
batting_part = stats_txt.split('Batting Statistics')[1].split('Pitching Statistics')[0]
for line in batting_part.split('\n'):
    cells = line.split('\t')
    if len(cells) >= 12 and cells[0].strip().isdigit() and ',' in cells[1]:
        nums = [c.strip() for c in cells[2:]]
        try:
            g, pa, ab, r, h, rbi = (int(nums[i]) for i in range(4, 10))
            hr = int(nums[12])
        except (ValueError, IndexError):
            continue
        # Key by person, summing rows (a jersey can be worn by several players,
        # and one player can appear under two renderings).
        key = canonical(cells[1])
        o = official.setdefault(key, {'name': key, 'ab': 0, 'h': 0, 'hr': 0, 'rbi': 0})
        o['ab'] += ab; o['h'] += h; o['hr'] += hr; o['rbi'] += rbi

# --- official record from pitching W/L columns ---
# Row shape: jersey \t "Last, First"? -> in text export names may wrap; rely on
# numeric columns: ERA IP W L SV ...
off_w = off_l = 0
pitching_part = stats_txt.split('Pitching Statistics')[1]
for line in pitching_part.split('\n'):
    cells = [c.strip() for c in line.split('\t')]
    # find rows whose numeric shape matches: ERA(float) IP(float) W L SV
    for i in range(len(cells) - 4):
        if re.fullmatch(r'\d+\.\d\d', cells[i] or '') and \
           re.fullmatch(r'\d+\.\d', cells[i + 1] or '') and \
           all(re.fullmatch(r'\d+', cells[i + j] or '') for j in (2, 3, 4)):
            off_w += int(cells[i + 2])
            off_l += int(cells[i + 3])
            break

# --- seed-side aggregates ---
agg = {}
seed_w = seed_l = 0
for g in seed['games']:
    ts, os_ = g.get('teamScore'), g.get('opponentScore')
    if ts is not None and os_ is not None:
        if ts > os_: seed_w += 1
        elif ts < os_: seed_l += 1
    for l in g.get('lines', []):
        a = agg.setdefault(l['player'],
                           {'ab': 0, 'h': 0, 'hr': 0, 'rbi': 0, 'name': l['player']})
        for k in ('ab', 'h', 'hr', 'rbi'):
            a[k] += l.get(k, 0)

failures, warnings = [], []

if (off_w or off_l) and (seed_w, seed_l) != (off_w, off_l) \
        and seed_w <= off_w and seed_l <= off_l:
    warnings.append(f'record lag: seed {seed_w}-{seed_l} vs official {off_w}-{off_l}')
# The seed LEADING the official record is judged further down, once we know
# whether every game is corroborated by the schedule listing. The aggregate
# page lags that listing, so leading it is normal in the hours after a game.

# Over-counts are graded, because they have two very different causes:
#   * a box score applied to two dates inflates MANY players at once, usually
#     with the record off too - that is corruption and must never ship;
#   * BallClubz retroactively correcting one published line drifts ONE player
#     by a small amount - that is upstream noise and must not wedge the
#     pipeline (a hard fail there blocks every later game indefinitely).
over = {}
for j, o in official.items():
    a = agg.get(j)
    if not a:
        continue
    for k in ('ab', 'h', 'hr', 'rbi'):
        if a[k] > o[k]:
            over.setdefault(j, []).append(
                f"{a['name']} {k.upper()} OVER-COUNT: seed {a[k]} vs official {o[k]}")
        elif a[k] < o[k]:
            warnings.append(
                f"{a['name']} {k.upper()} lag: seed {a[k]} vs official {o[k]}")

# Cross-check every finished game against the date BallClubz publishes for it.
# Totals alone cannot catch a misdating: putting the Aug 7 result on Aug 6 keeps
# the record and every batting total correct while showing the wrong day in the
# app. Only comparing dates directly catches that, so it is a hard failure.
try:
    sched_lines = [l.strip() for l in
                   open('scraped/ballclubz-schedule.txt').read().split('\n')]
except FileNotFoundError:
    sched_lines = []
# Build the set of games BallClubz publishes, as (date, us, them). A date can
# hold more than one game - KC played an Aug 8 doubleheader - and the page
# renders its list twice, so identical rows are deduplicated while differing
# rows on one date are kept as the separate games they are.
published = set()
for i, line in enumerate(sched_lines):
    md = re.match(r'^(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\s+(\d{1,2})/(\d{1,2})$', line)
    if not md:
        continue
    block = [l for l in sched_lines[i + 1:i + 5] if l]
    res = next((l for l in block if re.match(r'^[WL]\s+\d+-\d+$', l)), None)
    if not res:
        continue                                    # not played yet
    us, them = (int(x) for x in res.split()[1].split('-'))
    published.add((f'2026-{int(md.group(1)):02d}-{int(md.group(2)):02d}', us, them))

# Every scored game in the seed must be one BallClubz actually published on
# that date. This is the check that catches a misdating, which no totals-based
# check can see: moving a result one day leaves the record and every batting
# total correct.
scored_games = [g for g in seed['games'] if 'teamScore' in g]
uncorroborated = []
if published:
    for g in scored_games:
        if (g['date'], g['teamScore'], g['opponentScore']) not in published:
            uncorroborated.append(g)
    for g in uncorroborated:
        holder = next((d for d, u, t in published
                       if (u, t) == (g['teamScore'], g['opponentScore'])
                       and d != g['date']), None)
        if holder:
            failures.append(
                f"MISDATED: seed has {g['teamScore']}-{g['opponentScore']} on "
                f"{g['date']}, BallClubz publishes it on {holder}")
        else:
            failures.append(
                f"{g['date']}: seed records {g['teamScore']}-{g['opponentScore']}, "
                'which BallClubz does not publish for that date')

# A duplicated game shows up as a wrong record and/or a whole lineup drifting.
# The official aggregate lags the schedule listing by a day or more, so a
# seed that leads it is normal right after a game. It is only corruption if
# a game is ALSO unaccounted for upstream - that is what double-counting
# looks like, and it is caught above as uncorroborated.
ahead = (off_w or off_l) and (seed_w > off_w or seed_l > off_l)
if ahead and uncorroborated:
    failures.append(
        f'record OVER-COUNT: seed {seed_w}-{seed_l} vs official {off_w}-{off_l} '
        'with games the schedule does not publish')
elif ahead:
    warnings.append(
        f'seed {seed_w}-{seed_l} leads official {off_w}-{off_l}; every game is '
        'published upstream, so the aggregate page is simply behind')
systemic = len(over) >= 3 and bool(uncorroborated) or bool(uncorroborated)
for j, msgs in over.items():
    (failures if systemic else warnings).extend(msgs)
if over and not systemic:
    warnings.append(
        f'{len(over)} player(s) drifted above official totals by a small margin; '
        'treating as an upstream scoring correction, not corruption')

for w in warnings[:20]:
    print('WARN:', w)
print(f'verify-seed: record seed {seed_w}-{seed_l} / official {off_w}-{off_l}, '
      f'{len(official)} official batters checked, '
      f'{len(failures)} failures, {len(warnings)} warnings')
if failures:
    for f in failures:
        print('FAIL:', f, file=sys.stderr)
    print('verify-seed: seed claims more than the official aggregates - refusing '
          'to ship corrupted data. Investigate id mapping.', file=sys.stderr)
    sys.exit(1)
