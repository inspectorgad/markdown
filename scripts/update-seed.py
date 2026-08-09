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


# BallClubz put per-player box scores behind registration in Aug 2026: the
# Batters table still renders, but every column except PA is an em-dash and the
# page ends with "Register/Login to view additional information". Those dashes
# used to parse as zeros, which let the re-derive step replace a season of real
# batting lines with all-zero rows. A gated table has no data in it, so it must
# be rejected outright rather than read as zeros.
GATED_BOX = re.compile(r'Register/Login|\t-\t-\t-')


def parse_box_kc(text):
    """Parse the KC Diamonds batting table -> ordered {player_name: Counter}.

    Returns None when the table carries no usable numbers.
    """
    global changed
    if GATED_BOX.search(text):
        return None
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


def is_tbd(opp):
    """A placeholder opponent, i.e. a bracket the league hasn't drawn yet."""
    return not opp or opp.strip().upper() == 'TBD'


def ballclubz_results():
    """[(date, opponent, us, them)] from BallClubz's own schedule listing.

    The listing states the date of every finished game outright:
        Fri 8/7
        VS. Florida Vibe
        W 4-3
    That is far stronger evidence than guessing which score-less date a loose
    game id belongs to, and it is the only thing that distinguishes a real
    date from a placeholder the team published but never played.
    """
    try:
        lines = [l.strip() for l in
                 open('scraped/ballclubz-schedule.txt').read().split('\n')]
    except FileNotFoundError:
        return []
    out = []
    for i, line in enumerate(lines):
        md = re.match(r'^(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\s+(\d{1,2})/(\d{1,2})$', line)
        if not md:
            continue
        block = [l for l in lines[i + 1:i + 5] if l]
        opp = next((l for l in block if re.match(r'^(VS\.|@)\s', l)), None)
        res = next((l for l in block if re.match(r'^[WL]\s+\d+-\d+$', l)), None)
        if not (opp and res):
            continue                       # PREVIEW / unplayed
        us, them = (int(x) for x in res.split()[1].split('-'))
        name = re.sub(r'^(VS\.|@)\s+', '', opp).strip()
        out.append((f'2026-{int(md.group(1)):02d}-{int(md.group(2)):02d}',
                    CANONICAL_OPP.get(name.upper(), name), us, them))
    return out


def opponent_from_wrap(wrap):
    """The non-KC team named in the box score's line-score rows, or None.

    The line score is the only place the opponent is stated in a stable,
    machine-readable position:
        Florida Vibe   2 0 2 0 1 1 0   6 14 1 12
        KC Diamonds    0 0 0 2 1 2 0   5  6 0  5
    """
    for line in wrap.split('\n'):
        m = re.match(r"^([A-Za-z][A-Za-z .&'-]+?)\t[\d\t]+$", line)
        if m:
            name = m.group(1).strip()
            if name != 'KC Diamonds':
                return CANONICAL_OPP.get(name.upper(), name)
    return None


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
    # Belt and braces: a re-derive may only ever correct a game, never gut it.
    # Whatever the upstream page does next, a snapshot that would erase recorded
    # production is a parsing failure by definition, not a scoring correction.
    old_ab = sum(l['ab'] for l in g['lines'])
    new_ab = sum(l['ab'] for l in rebuilt)
    if old_ab and new_ab * 2 < old_ab:
        print(f'!! {g["date"]}: refusing re-derive, at-bats would drop '
              f'{old_ab} -> {new_ab} (snapshot looks unparsed)', file=sys.stderr)
        continue
    if rebuilt and rebuilt != g['lines']:
        before = {l['player'] for l in g['lines']}
        after = {l['player'] for l in rebuilt}
        for nm in sorted(before - after):
            print(f're-derived {g["date"]}: dropped {nm}')
        for nm in sorted(after - before):
            print(f're-derived {g["date"]}: added {nm}')
        g['lines'] = rebuilt
        changed = True

def ingest_schedule_results():
    """Record scores BallClubz publishes in its schedule listing.

    A final is listed there whether or not a box score is reachable, so a game
    is never left blank in the app merely because no box could be tied to it.
    Batting lines still come from the box; this only fills the score.
    """
    changed = False
    for date, opp, us, them in ballclubz_results():
        game = games_by_date.get(date)
        if game is None or 'teamScore' in game:
            continue
        game['teamScore'], game['opponentScore'] = us, them
        if is_tbd(game.get('opponent')) and opp:
            game['opponent'] = opp
        print(f'schedule result: {date} vs {game["opponent"]} {us}-{them}')
        changed = True
    return changed


def ingest_box_scores():
    """Fold every published BallClubz box score into the seed."""
    changed = False
    sched_by_date = {d: (us, them) for d, _, us, them in ballclubz_results()}
    for f in sorted(glob.glob('scraped/box-*.json')):
        d = json.load(open(f))
        gid = d.get('id') or d['url'].rsplit('/', 1)[1]
        wrap = d.get('wrap', '')
        res = parse_result(wrap)
        if not res:
            continue
        date = id_to_date.get(gid)
        if not date:
            # BallClubz states the date of each finished game in its schedule
            # listing, so match this result against that listing. The match
            # must be UNIQUE: scores repeat across a season, and binding to
            # whichever row happened to be found first attached an 8-2 box to
            # Aug 8, whose real result was 7-8.
            hits = [(d, opp) for d, opp, us, them in ballclubz_results()
                    if (us, them) == res and opponent_matches(opp, wrap)
                    and d not in id_to_date.values()]
            if len(hits) == 1:
                date, opp = hits[0]
                id_to_date[gid] = date
                print(f'dated {gid} -> {date} vs {opp} {res[0]}-{res[1]} '
                      f'from the BallClubz schedule')
            elif hits:
                print(f'ambiguous: {gid} {res} matches {len(hits)} schedule rows '
                      f'({", ".join(d for d, _ in hits)}), not dating it',
                      file=sys.stderr)
        if not date:
            # Last resort: earliest past seed game still without a score whose
            # opponent matches. A TBD date is NOT a wildcard here - an undrawn
            # bracket often lists days that are never played, and letting one
            # absorb a result silently misdates it.
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
        # BallClubz's schedule row is the authority on what happened that day.
        # If this box disagrees with it, the box belongs to a different game -
        # never let it overwrite the day, and never guess which is right.
        official = sched_by_date.get(date)
        if official and official != res:
            print(f'!! {date}: box score says {res[0]}-{res[1]} but the '
                  f'BallClubz schedule says {official[0]}-{official[1]}; '
                  f'{gid} is not this game, skipping', file=sys.stderr)
            continue
        # Adopt the opponent the box score names while the schedule says TBD.
        if is_tbd(game.get('opponent')):
            named = opponent_from_wrap(wrap)
            if named:
                print(f'{date}: opponent TBD -> {named} (from box score)')
                game['opponent'] = named
                changed = True
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
                                '2b': c['2b'], '3b': c['3b'], 'hr': c['hr'],
                                'rbi': c['rbi'], 'bb': c['bb'], 'so': c['so'],
                                'hbp': c['hbp'], 'sf': c['sf'], 'sb': c['sb']})
                if out:
                    game['lines'] = out
                    changed = True
    return changed


# --- Sync the schedule -------------------------------------------------------
# The team's schedule page is the source of truth for which games exist -
# postseason rounds, fall exhibitions, reschedules and promo nights all show up
# there first. Parsing it every run means new games and special events reach
# the app without a code change. Only additive: scores and stat lines are never
# touched, and a game is never removed (a scraped page that briefly drops a
# game must not erase recorded history).
SCHEDULE_FILES = ('scraped/schedule.txt', 'scraped/past-results.txt')
NOISE = ('TICKETS', 'LIVE FEED', 'FCSN', 'PLUTOTV', 'BOX SCORE', 'VS.')
# Tokens the site writes in caps that must stay caps when a name is title-cased.
KEEP_CAPS = {'KU', 'KC', 'TBD', 'NY', 'PSL', 'MU'}
# Some blocks put a box-office sales pitch where the promo name usually goes.
# That is not an event, so it must not land in the game's event field.
EVENT_JUNK = re.compile(
    r'group ticket|call \d{3}[-.]|\bcall us\b|\btickets?\b.*\bavailable\b',
    re.I)


def smart_title(raw):
    """Title-case a scraped team name without mangling initialisms."""
    return ' '.join(
        w if w.upper() in KEEP_CAPS else w.title() for w in raw.split())


def season_for(date_str, opponent):
    """Postseason = the Aug championship week; Oct dates are fall exhibitions."""
    if date_str >= '2026-10-01':
        return '2026 Fall'
    if date_str >= '2026-08-03':
        return '2026 Postseason'
    return '2026'


def parse_schedule():
    """-> {date: {'opponent':…, 'event':…, 'location':…}} from the site's pages."""
    out = {}
    for path in SCHEDULE_FILES:
        try:
            text = open(path).read()
        except FileNotFoundError:
            continue
        lines = [l.strip() for l in text.split('\n')]
        for i, line in enumerate(lines):
            md = re.match(
                r'^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+(\d{1,2}),\s*(\d{4})',
                line)
            if not md:
                continue
            date = f'{md.group(3)}-{MONTHS[md.group(1)]:02d}-{int(md.group(2)):02d}'
            # Each block reads: <day> / <date time> / VS. / opponent /
            # location / optional promo-event line, then ticket links.
            try:
                vs = next(k for k in range(i + 1, min(i + 6, len(lines)))
                          if lines[k].upper() == 'VS.')
            except StopIteration:
                continue
            fields = []
            for k in range(vs + 1, min(vs + 9, len(lines))):
                cur = lines[k]
                if not cur:
                    continue
                if cur.upper() in NOISE or re.match(
                        r'^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)$', cur):
                    break
                if re.match(r'^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d',
                            cur):
                    break
                fields.append(cur)
                if len(fields) == 3:
                    break
            raw_opp = fields[0] if fields else ''
            opponent = (CANONICAL_OPP.get(raw_opp.upper(), smart_title(raw_opp))
                        if raw_opp.strip() else 'TBD')
            location = fields[1] if len(fields) > 1 else None
            if location and location.upper() == 'TBD':
                location = None          # venue not announced yet
            event = fields[2] if len(fields) > 2 else None
            if event and EVENT_JUNK.search(event):
                event = None
            # Championship week is listed as bare TBD matchups at a neutral
            # site; the site never names the bracket, so label it ourselves.
            if not event and season_for(date, opponent) == '2026 Postseason':
                event = 'PSL Championship'
            prev = out.get(date, {})
            out[date] = {
                'opponent': opponent,
                'location': location or prev.get('location'),
                'event': event or prev.get('event'),
            }
    return out


def sync_schedule():
    """Fold the published schedule into the seed. Returns True if anything moved.

    Runs BEFORE box scores are ingested: a postseason game is published as
    "TBD" until the bracket is drawn, and the box-score matcher needs the real
    opponent to tie a game id to a date. Doing this second cost the Aug 5
    championship result a full day.
    """
    changed = False
    scheduled = parse_schedule()
    if not scheduled:
        return changed
    for date, info in sorted(scheduled.items()):
        game = games_by_date.get(date)
        if game is None:
            game = {'date': date, 'opponent': info['opponent'],
                    'season': season_for(date, info['opponent'])}
            if info.get('event'):
                game['event'] = info['event']
            if info.get('location'):
                game['location'] = info['location']
            seed['games'].append(game)
            games_by_date[date] = game
            changed = True
            print(f"schedule: added {date} vs {game['opponent']} ({game['season']})")
        else:
            # Retract a sales pitch an earlier run mistook for an event name.
            if game.get('event') and EVENT_JUNK.search(game['event']):
                del game['event']
                changed = True
            # Fill in details the site publishes later without touching results.
            for key, val in (('event', info.get('event')),
                             ('location', info.get('location'))):
                if val and game.get(key) != val:
                    game[key] = val
                    changed = True
            want_season = season_for(date, game['opponent'])
            if game.get('season') != want_season and 'lines' not in game:
                game['season'] = want_season
                changed = True
            if game.get('opponent') in (None, 'TBD') and info['opponent'] != 'TBD':
                game['opponent'] = info['opponent']
                changed = True
    seed['games'].sort(key=lambda g: g['date'])
    return changed


# Schedule first: it resolves TBD opponents and adds new dates, both of which
# the box-score matcher depends on to tie a game id to a date.
if sync_schedule():
    changed = True
# Scores from the schedule listing first, so a box score can only ever enrich a
# day the schedule already agrees on, never define one on its own.
if ingest_schedule_results():
    changed = True
if ingest_box_scores():
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
