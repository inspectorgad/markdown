#!/usr/bin/env python3
"""Fold freshly scraped BallClubz box scores into app/src/main/assets/seed.json.

Gap-filling only: existing scores and stat lines in the seed are never
overwritten. New game ids are mapped to dates via the team site's
past-results page (scraped/games.json); ids the site doesn't map are matched
to unscored seed games chronologically (BallClubz ids sort by creation time)
and accepted only when the opponent named in the game's own line score
matches. Prints SEED_CHANGED=yes/no for the CI workflow.
"""
import collections
import datetime
import glob
import json
import re
import sys

SEED = 'app/src/main/assets/seed.json'

# Verified historical mapping (game id -> date); site mapping extends this.
EMBEDDED_MAP = {
    '2ae8c04': '2026-06-12', '2ae8d16': '2026-06-13', '2ae8e28': '2026-06-14',
    '2b2653d': '2026-06-17', '2b265c6': '2026-06-18', '2b2664f': '2026-06-19',
    '2b266d8': '2026-06-20', '2b2bfae': '2026-06-22', '2b3fea2': '2026-06-23',
    '2b40732': '2026-06-24', '2b49d0a': '2026-06-26',
    '2b49d93': '2026-06-27', '2b513d8': '2026-06-29', '2b51461': '2026-06-30',
    '2b514ea': '2026-07-01', '2b51573': '2026-07-02', '2b515fc': '2026-07-03',
    '2b51685': '2026-07-04', '2b44ffa': '2026-06-25', '2b7a94c': '2026-07-13', '2b7a9d5': '2026-07-14',
    '2b7aa5e': '2026-07-15', '2b7aae7': '2026-07-16',
    '2a519a5': '2026-07-09', '2a51a2e': '2026-07-10', '2a51ab7': '2026-07-11',
    '2b7ab70': '2026-07-19', '2b7abf9': '2026-07-20', '2b7ac82': '2026-07-21',
}
MONTHS = {'Jan': 1, 'Feb': 2, 'Mar': 3, 'Apr': 4, 'May': 5, 'Jun': 6,
          'Jul': 7, 'Aug': 8, 'Sep': 9, 'Oct': 10, 'Nov': 11, 'Dec': 12}
# Substrings of (possibly truncated) BallClubz line-score team names.
OPP_HINTS = {
    'Atlanta Smoke': ('Atlanta',), 'Florida Vibe': ('Vibe',),
    'Florida Breeze': ('Bree',), 'Florida Heat': ('Heat',),
    'NY Rise': ('New York', 'NY Ris'), 'St. Louis Gateway Gold': ('St', 'Gateway'),
    'China National Team': ('China',),
}
CANONICAL_OPP = {
    'ATLANTA SMOKE': 'Atlanta Smoke', 'FLORIDA VIBE': 'Florida Vibe',
    'FLORIDA BREEZE': 'Florida Breeze', 'FLORIDA HEAT': 'Florida Heat',
    'NY RISE': 'NY Rise', 'ST. LOUIS GATEWAY GOLD': 'St. Louis Gateway Gold',
    'CHINA NATIONAL TEAM': 'China National Team',
}

seed = json.load(open(SEED))
jersey_to_name = {p['jerseyNumber']: p['name'] for p in seed['players'] if p['jerseyNumber']}
last_to_name = {p['name'].split()[-1].upper(): p['name'] for p in seed['players']}
last_to_name.update({'LUCAS': 'Lauren Lucas Thornhill', 'THORNHILL': 'Lauren Lucas Thornhill'})

# Box-score identities that refer to a player already on the roster under a
# different rendering (placeholder rows, misspellings, short/long first names).
# Keyed by "LAST|First" exactly as the box score renders it.
ROW_ALIASES = {
    'PLAYER87|Meg': 'Meg Houk',
    'HOUK|Meaghan': 'Meg Houk',
    'PLAYER10|Lauren': 'Stephanie Smith',
    'MARCIENO|Brianna': 'Briana Marcelino',
    'LUCAS|Lauren': 'Lauren Lucas Thornhill',
}
known_names = {p['name'] for p in seed['players']}
changed = False


def parse_box_kc(text):
    """Parse the KC Diamonds batting table -> ordered {player_name: Counter}."""
    global changed
    lines = text.split('\n')
    try:
        start = next(i for i, l in enumerate(lines) if l.startswith('Batters\t'))
    except StopIteration:
        return None
    stats = collections.defaultdict(collections.Counter)
    order = []
    i = start + 1
    while i < len(lines) - 1:
        row = lines[i]
        if row.strip().startswith('TOTALS'):
            break
        m = re.match(r'^\s*[0-9F]*\s*\t?([A-Z][A-Z0-9 \'.-]+) #(\d+)$', row.strip())
        if m:
            detail = lines[i + 1] if i + 1 < len(lines) else ''
            # Identity comes from the row's own name text. Jersey numbers are
            # reused across players during a season, so they cannot key a
            # player - they are recorded as an attribute only.
            last_raw = m.group(1).split()[0].upper()
            first_raw = detail.split('\t')[0].strip().split()[0] if detail.strip() else ''
            name = ROW_ALIASES.get(f'{last_raw}|{first_raw.title()}')
            if not name and re.fullmatch(r'PLAYER\d+', last_raw):
                # Placeholder row (the scorer had no name yet): the jersey is
                # the only identity signal available, so use the roster's.
                name = jersey_to_name.get(m.group(2))
            if not name and first_raw:
                candidate = f'{first_raw.title()} {last_raw.title()}'
                match = next((n for n in known_names if n.lower() == candidate.lower()), None)
                if not match:
                    # A roster name that ends in this surname and shares the
                    # first name (handles compound surnames like Lucas Thornhill).
                    match = next(
                        (n for n in known_names
                         if n.upper().endswith(last_raw)
                         and n.split()[0].lower() == first_raw.lower()), None)
                name = match or candidate
                if name not in known_names:
                    seed['players'].append(
                        {'name': name, 'jerseyNumber': m.group(2), 'position': ''})
                    known_names.add(name)
                    changed = True
            if not name:
                name = last_to_name.get(last_raw)  # last resort
            nums = detail.split('\t')[1:]
            if name and len(nums) >= 7:
                vals = [int(x) if x.strip().isdigit() else 0 for x in nums[:7]]
                c = stats[name]
                c.update(dict(zip(['pa', 'ab', 'r', 'h', 'rbi', 'bb', 'so'], vals)))
                if name not in order:
                    order.append(name)
            i += 2
            continue
        i += 1
    tail = text[text.find('Batters\t'):]
    pit = tail.find('Pitchers\t')
    tail = tail[:pit] if pit != -1 else tail
    for key, field in {'2B': '2b', '3B': '3b', 'HR': 'hr', 'SB': 'sb',
                       'HBP': 'hbp', 'SF': 'sf'}.items():
        m = re.search(rf'^{key}: (.+)$', tail, re.M)
        if not m:
            continue
        for part in m.group(1).split(','):
            part = part.strip()
            cnt = 1
            n = re.search(r'\((\d+)\)', part)
            if n:
                cnt, part = int(n.group(1)), part[:n.start()].strip()
            name = last_to_name.get(part.split()[0].upper()) if part else None
            if name and name in stats:
                stats[name][field] += cnt
    return {n: stats[n] for n in order}


def parse_result(wrap):
    m = re.search(r'([A-Za-z .]+?) wins (\d+)-(\d+)', wrap)
    if not m:
        return None
    winner, a, b = m.group(1).strip(), int(m.group(2)), int(m.group(3))
    return (a, b) if winner == 'KC Diamonds' else (b, a)


def opponent_matches(opp, wrap):
    return any(h.lower() in wrap.lower() for h in OPP_HINTS.get(opp, (opp[:6],)))


# Game id -> date, from the embedded map, the seed's own record of which id
# produced each game's data (srcId), and the site's past-results page.
id_to_date = dict(EMBEDDED_MAP)
for g in seed['games']:
    if g.get('srcId'):
        id_to_date[g['srcId']] = g['date']
try:
    for block in json.load(open('scraped/games.json')):
        mid = re.search(r'/live/([a-z0-9]+)$', block['href'])
        md = re.search(r'(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+(\d{1,2}), (\d{4})',
                       block['context'])
        if mid and md:
            id_to_date[mid.group(1)] = (
                f'{md.group(3)}-{MONTHS[md.group(1)]:02d}-{int(md.group(2)):02d}')
except FileNotFoundError:
    pass

games_by_date = {g['date']: g for g in seed['games']}
today = datetime.date.today().isoformat()

# --- Reconcile retractions -------------------------------------------------
# BallClubz sometimes corrects a published box score (a mis-entered player is
# removed). A seed line the source no longer lists would otherwise inflate our
# totals above the official aggregates forever, so drop it. Only games whose
# data this pipeline owns (srcId set) and whose snapshot still parses are
# touched; numbers are never rewritten, lines are only removed.
snapshot_by_id = {}
for f in glob.glob('scraped/box-*.json'):
    d = json.load(open(f))
    gid_ = d.get('id') or d['url'].rsplit('/', 1)[1]
    snapshot_by_id[gid_] = d.get('boxKC', '') or ''

for g in seed['games']:
    gid_ = g.get('srcId')
    if not gid_ or 'lines' not in g:
        continue
    box = snapshot_by_id.get(gid_, '')
    if 'Batters\t' not in box:
        continue  # no usable snapshot; leave the game alone
    present = parse_box_kc(box) or {}
    if len(present) < 8:
        continue  # partial/unparsed snapshot: never clobber good data with it
    rebuilt = []
    for nm, c in present.items():
        if c['pa'] == 0 and c['ab'] == 0 and c['r'] == 0:
            continue
        rebuilt.append({'player': nm, 'ab': c['ab'], 'r': c['r'], 'h': c['h'],
                        '2b': c['2b'], '3b': c['3b'], 'hr': c['hr'], 'rbi': c['rbi'],
                        'bb': c['bb'], 'so': c['so'], 'hbp': c['hbp'], 'sf': c['sf'],
                        'sb': c['sb']})
    if rebuilt and rebuilt != g['lines']:
        before = {l['player'] for l in g['lines']}
        after = {l['player'] for l in rebuilt}
        for nm in sorted(before - after):
            print(f're-derived {g["date"]}: dropped {nm}')
        for nm in sorted(after - before):
            print(f're-derived {g["date"]}: added {nm}')
        g['lines'] = rebuilt
        changed = True

for f in sorted(glob.glob('scraped/box-*.json')):
    d = json.load(open(f))
    gid = d.get('id') or d['url'].rsplit('/', 1)[1]
    wrap = d.get('wrap', '')
    res = parse_result(wrap)
    if not res:
        continue
    date = id_to_date.get(gid)
    if not date:
        # Chronological fallback: earliest past seed game still without a
        # score whose opponent matches the line score.
        for g in sorted(seed['games'], key=lambda x: x['date']):
            if g['date'] <= today and 'teamScore' not in g \
                    and g['date'] not in id_to_date.values() \
                    and opponent_matches(g['opponent'], wrap):
                date = g['date']
                id_to_date[gid] = date
                print(f'inferred: {gid} -> {date} vs {g["opponent"]}')
                break
    if not date:
        print(f'unmapped game id {gid} (result {res}), skipping', file=sys.stderr)
        continue
    game = games_by_date.get(date)
    if game is None:
        continue  # not a scheduled KC game we know about
    if not opponent_matches(game['opponent'], wrap):
        print(f'!! {date}: opponent {game["opponent"]} not in line score, skipping',
              file=sys.stderr)
        continue
    if game.get('srcId') not in (None, gid):
        continue  # this date's data came from a different game id
    if 'teamScore' not in game:
        game['teamScore'], game['opponentScore'] = res
        game['srcId'] = gid
        changed = True
    elif game.get('srcId') is None:
        game['srcId'] = gid
        changed = True
    if 'lines' not in game:
        parsed = parse_box_kc(d.get('boxKC', '') or '')
        if parsed:
            out = []
            for name, c in parsed.items():
                if c['pa'] == 0 and c['ab'] == 0 and c['r'] == 0:
                    continue
                out.append({'player': name, 'ab': c['ab'], 'r': c['r'], 'h': c['h'],
                            '2b': c['2b'], '3b': c['3b'], 'hr': c['hr'], 'rbi': c['rbi'],
                            'bb': c['bb'], 'so': c['so'], 'hbp': c['hbp'], 'sf': c['sf'],
                            'sb': c['sb']})
            if out:
                game['lines'] = out
                changed = True

if changed:
    seed['formatVersion'] = 1
    seed['generatedAt'] = datetime.datetime.now(datetime.timezone.utc).strftime(
        '%Y-%m-%dT%H:%M:%SZ')
    json.dump(seed, open(SEED, 'w'), indent=2)
scored = [g for g in seed['games'] if 'teamScore' in g]
wins = sum(1 for g in scored if g['teamScore'] > g['opponentScore'])
losses = sum(1 for g in scored if g['teamScore'] < g['opponentScore'])
print(f'Record {wins}-{losses}, {len(scored)} scored games, '
      f"{sum(1 for g in seed['games'] if 'lines' in g)} with box scores")
print(f'SEED_CHANGED={"yes" if changed else "no"}')
