#!/usr/bin/env python3
"""Parse KC's pitching lines out of the captured BallClubz box scores.

A prototype. Nothing here writes seed.json: it parses every capture, checks the
result against the evidence already in the box, and reports. The point is to
find out whether the pitching table can be trusted BEFORE anything depends on
it - the batting side learned that the expensive way.

Run:  python3 scripts/parse-pitching.py [--json out.json]

The table looks like this, with the surname on its own line and the given name
leading the numbers row (the same two-line shape the batting table uses):

    Pitchers	IP	H	R	ER	BB	SO	P-S
    HEARD
    Jori	1.1	3	5	3	1	3	39-22
    RICARD
    Kasey	0.2	1	2	2	1	0	12-6
    TOTALS	6.0	5	8	5	3	6	89-55

Three things in there are checkable without trusting the parser:

  * TOTALS is a real checksum, not a repeat of the last row. Summed outs, hits,
    runs, earned runs, walks, strikeouts, pitches and strikes must all match it.
  * Runs charged to KC's pitchers must equal the opponent's final score.
  * IP is thirds, not decimals: 1.1 is four outs. 1.1 + 0.2 + 3.1 + 0.2 = 6.0
    only under that reading, so a decimal misreading shows up as a TOTALS
    mismatch rather than as quietly wrong ERAs.
"""

import argparse
import collections
import glob
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SEED = os.path.join(HERE, 'app/src/main/assets/seed.json')

# Same guard the batting parser uses. A login-gated table serves dashes where
# the numbers go; those must never be read as zeros, because a zero is a claim
# ("she faced batters and allowed nothing") while a dash is an absence.
GATED_BOX = re.compile(r'Register/Login|\t-\t-\t-')

# "VALDESPINO" / "DE LA CRUZ" - surname alone on its own line, upper case.
SURNAME = re.compile(r"^([A-Z][A-Z0-9 '.-]*)$")
# "Hope\t5.0\t0\t0\t0\t1\t6\t75-45" - given name then the seven stat columns.
NUMS = re.compile(
    r"^([A-Za-z][A-Za-z'.-]*)\t"      # given name
    r"(\d+\.\d)\t"                    # IP, thirds notation
    r"(\d+)\t(\d+)\t(\d+)\t(\d+)\t(\d+)"   # H R ER BB SO
    r"(?:\t(\d+)-(\d+))?\s*$")        # P-S, absent in some captures


class ParseError(Exception):
    """The table is present but does not read the way this parser expects."""


def ip_to_outs(text):
    """'5.1' -> 16. Innings pitched are whole innings plus thirds.

    Rejects a fractional part other than .0/.1/.2 rather than rounding it: if
    BallClubz ever switches to true decimals, 5.5 would silently become five
    and two-thirds, and every ERA built on it would be wrong by a little. A
    loud failure is recoverable; a plausible wrong number is not.
    """
    whole, _, third = text.partition('.')
    if third not in ('0', '1', '2'):
        raise ParseError(f'innings {text!r}: fractional part is not thirds')
    return int(whole) * 3 + int(third)


def outs_to_ip(outs):
    """16 -> '5.1', for display and for comparing against a published total."""
    return f'{outs // 3}.{outs % 3}'


def parse_pitchers(text):
    """Parse one team's pitching table -> (rows, totals).

    Returns None when the table is missing or gated - never a partial read,
    and never zeros standing in for numbers that were not served.
    """
    if GATED_BOX.search(text):
        return None
    lines = text.split('\n')
    try:
        start = next(i for i, l in enumerate(lines) if l.startswith('Pitchers\t'))
    except StopIteration:
        return None

    rows, totals = [], None
    i = start + 1
    while i < len(lines):
        line = lines[i].rstrip()
        if not line.strip():
            i += 1
            continue
        if line.strip().startswith('TOTALS'):
            cells = line.strip().split('\t')
            if len(cells) >= 7:
                ps = cells[7].split('-') if len(cells) > 7 and '-' in cells[7] else ['0', '0']
                totals = {
                    'outs': ip_to_outs(cells[1]),
                    'h': int(cells[2]), 'r': int(cells[3]), 'er': int(cells[4]),
                    'bb': int(cells[5]), 'so': int(cells[6]),
                    'pitches': int(ps[0]), 'strikes': int(ps[1]),
                }
            break
        m = SURNAME.match(line.strip())
        if m and i + 1 < len(lines):
            detail = NUMS.match(lines[i + 1].rstrip())
            if detail:
                g = detail.groups()
                rows.append({
                    'last': m.group(1).title(),
                    'first': g[0],
                    'outs': ip_to_outs(g[1]),
                    'h': int(g[2]), 'r': int(g[3]), 'er': int(g[4]),
                    'bb': int(g[5]), 'so': int(g[6]),
                    'pitches': int(g[7]) if g[7] else 0,
                    'strikes': int(g[8]) if g[8] else 0,
                })
                i += 2
                continue
        i += 1

    if not rows:
        return None
    return rows, totals


def opponent_box(box):
    """The opponent's half of the box, or None if this capture does not hold it.

    boxAway is not dependable: in a good capture it holds the other team's
    tables, but when the page's team tab does not switch in time the scraper
    stores a second copy of KC's own half instead. Byte-identical halves are
    that failure, and reading one as the opponent's would credit KC's pitching
    line to whoever they were playing. Checked, not assumed.
    """
    kc, away = box.get('boxKC') or '', box.get('boxAway') or ''
    if not away or away == kc:
        return None
    return away


def decisions(wrap):
    """{'win': 'Valdespino', 'loss': 'Pease'} from the wrap's decision line."""
    out = {}
    m = re.search(r'Win:\s*([^\n-]+?)\s*-\s*Loss:\s*([^\n]+)', wrap)
    if m:
        out['win'] = m.group(1).strip()
        out['loss'] = m.group(2).strip()
    m = re.search(r'Save:\s*([^\n-]+)', wrap)
    if m:
        out['save'] = m.group(1).strip()
    return out


def check(rows, totals):
    """Every way the box can contradict the parse. Returns a list of strings."""
    problems = []
    if totals is None:
        return ['no TOTALS row to check against']
    for field in ('outs', 'h', 'r', 'er', 'bb', 'so', 'pitches', 'strikes'):
        got = sum(r[field] for r in rows)
        want = totals[field]
        # Pitch counts are missing from some rows; only check when all have them.
        if field in ('pitches', 'strikes') and any(r['pitches'] == 0 for r in rows):
            continue
        if got != want:
            shown = (f'{outs_to_ip(got)} vs {outs_to_ip(want)}'
                     if field == 'outs' else f'{got} vs {want}')
            problems.append(f'{field} sums to {shown}')
    return problems


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--json', help='write parsed lines here')
    args = ap.parse_args()

    seed = json.load(open(SEED))
    by_src = {g['srcId']: g for g in seed['games'] if g.get('srcId')}
    roster = {p['name'].split()[-1].upper(): p['name'] for p in seed['players']}

    parsed, skipped, bad = [], [], []
    opp_have, opp_missing = [], []
    for path in sorted(glob.glob(os.path.join(HERE, 'scraped/box-*.json'))):
        box = json.load(open(path))
        name = os.path.basename(path)
        try:
            got = parse_pitchers(box.get('boxKC') or '')
        except ParseError as exc:
            bad.append((name, [str(exc)]))
            continue
        if got is None:
            skipped.append(name)
            continue
        rows, totals = got

        problems = check(rows, totals)

        # The strongest check available: runs charged to KC's pitchers are the
        # runs the opponent scored, and the seed already knows that number from
        # a different part of the page.
        game = by_src.get(box.get('id'))
        if game and game.get('opponentScore') is not None:
            charged = sum(r['r'] for r in rows)
            if charged != game['opponentScore']:
                problems.append(
                    f"runs charged {charged} != opponent score {game['opponentScore']}")

        for r in rows:
            r['player'] = roster.get(r['last'].upper(), f"{r['first']} {r['last']}")
            if r['last'].upper() not in roster:
                problems.append(f"{r['first']} {r['last']} is not on the roster")

        entry = {
            'srcId': box.get('id'),
            'date': game['date'] if game else None,
            'opponent': game['opponent'] if game else None,
            'decisions': decisions(box.get('wrap', '')),
            'pitchers': rows,
        }
        parsed.append(entry)
        if problems:
            bad.append((name, problems))

        (opp_have if opponent_box(box) else opp_missing).append(
            game['date'] if game else name)

    print(f'parsed {len(parsed)} games, skipped {len(skipped)} '
          f'(no table or gated), {len(bad)} with problems')

    innings = sum(r['outs'] for g in parsed for r in g['pitchers'])
    er = sum(r['er'] for g in parsed for r in g['pitchers'])
    print(f'total: {outs_to_ip(innings)} IP, {er} ER, '
          f'team ERA {er * 21 / innings:.2f}' if innings else 'no innings')

    who = collections.Counter()
    for g in parsed:
        for r in g['pitchers']:
            who[r['player']] += r['outs']
    print('\npitchers by innings:')
    for player, outs in who.most_common():
        print(f'  {outs_to_ip(outs):>6} IP  {player}')

    total = len(opp_have) + len(opp_missing)
    print(f'\nopponent half captured for {len(opp_have)}/{total} games '
          f'({len(opp_missing)} hold a second copy of KC\'s own box instead)')
    if opp_missing:
        print('  missing: ' + ', '.join(sorted(opp_missing)))

    if bad:
        print('\nPROBLEMS:')
        for name, problems in bad:
            for p in problems:
                print(f'  {name}: {p}')

    if args.json:
        json.dump(parsed, open(args.json, 'w'), indent=1)
        print(f'\nwrote {args.json}')

    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
