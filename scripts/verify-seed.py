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
        official[cells[0].strip()] = {
            'name': cells[1].strip(), 'ab': ab, 'h': h, 'hr': hr, 'rbi': rbi}

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
jersey_of = {p['name']: p['jerseyNumber'] for p in seed['players']}
agg = {}
seed_w = seed_l = 0
for g in seed['games']:
    ts, os_ = g.get('teamScore'), g.get('opponentScore')
    if ts is not None and os_ is not None:
        if ts > os_: seed_w += 1
        elif ts < os_: seed_l += 1
    for l in g.get('lines', []):
        j = jersey_of.get(l['player'], '')
        a = agg.setdefault(j, {'ab': 0, 'h': 0, 'hr': 0, 'rbi': 0, 'name': l['player']})
        for k in ('ab', 'h', 'hr', 'rbi'):
            a[k] += l.get(k, 0)

failures, warnings = [], []

if off_w or off_l:
    if seed_w > off_w or seed_l > off_l:
        failures.append(f'record OVER-COUNT: seed {seed_w}-{seed_l} vs official {off_w}-{off_l}')
    elif (seed_w, seed_l) != (off_w, off_l):
        warnings.append(f'record lag: seed {seed_w}-{seed_l} vs official {off_w}-{off_l}')

for j, o in official.items():
    a = agg.get(j)
    if not a:
        continue
    for k in ('ab', 'h', 'hr', 'rbi'):
        if a[k] > o[k]:
            failures.append(
                f"{a['name']} (#{j}) {k.upper()} OVER-COUNT: seed {a[k]} vs official {o[k]}")
        elif a[k] < o[k]:
            warnings.append(
                f"{a['name']} (#{j}) {k.upper()} lag: seed {a[k]} vs official {o[k]}")

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
