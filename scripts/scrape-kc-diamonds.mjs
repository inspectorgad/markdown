// Scrapes KC Diamonds game results and box scores using a real headless
// browser (runs in GitHub Actions, where outbound network is open).
// Output under scraped/: games.json (date/opponent/url mapping), box-*.json
// (line score + opponent box + KC Diamonds box per game), ballclubz-stats.txt.
import { chromium } from 'playwright';
import fs from 'fs';

fs.rmSync('scraped', { recursive: true, force: true });
fs.mkdirSync('scraped', { recursive: true });

// Game IDs confirmed from previous scrape rounds (hex-sorted = chronological).
const KNOWN_IDS = [
  '2b3fea2', '2b40732', '2b43179', '2b49d0a', '2b49d93',
  '2b513d8', '2b51461', '2b514ea', '2b51573', '2b7aa5e',
];

const browser = await chromium.launch();
const context = await browser.newContext({
  userAgent:
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
  viewport: { width: 1400, height: 2400 },
});

async function textOf(page) {
  return page.evaluate(() => (document.body ? document.body.innerText : ''));
}

// --- 0. Schedule, standings, and brand assets -------------------------------
// The upcoming-schedule page is the source of truth for future games,
// postseason rounds, fall exhibitions and promo nights, so the app can pick up
// anything newly added without a code change.
async function collectSchedule() {
  const page = await context.newPage();
  const out = [];
  try {
    await page.goto('https://thekcdiamonds.com/schedule/schedule', {
      waitUntil: 'domcontentloaded', timeout: 60_000 });
    await page.waitForTimeout(8_000);
    for (let i = 0; i < 20; i++) {
      await page.evaluate(() => window.scrollBy(0, 1000));
      await page.waitForTimeout(600);
    }
    fs.writeFileSync('scraped/schedule.txt', await textOf(page));
    await page.screenshot({ path: 'scraped/schedule.png', fullPage: true });
  } catch (e) {
    fs.writeFileSync('scraped/schedule.txt', `ERROR: ${e.message}`);
  } finally {
    await page.close();
  }
  return out;
}

async function collectStandings() {
  const urls = [
    'https://www.professionalsoftballleague.com/standings',
    'https://www.professionalsoftballleague.com/league-standings',
    'https://www.professionalsoftballleague.com/stats',
    'https://www.professionalsoftballleague.com/statistics',
    'https://www.professionalsoftballleague.com/',
    'https://thekcdiamonds.com/standings',
  ];
  const found = [];
  for (const url of urls) {
    const page = await context.newPage();
    try {
      const resp = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
      await page.waitForTimeout(7_000);
      for (let i = 0; i < 8; i++) {
        await page.evaluate(() => window.scrollBy(0, 1000));
        await page.waitForTimeout(600);
      }
      const text = await textOf(page);
      const status = resp ? resp.status() : 0;
      const slug = url.replace(/https?:\/\//, '').replace(/[^a-z0-9]+/gi, '-').slice(0, 60);
      fs.writeFileSync(`scraped/standings-${slug}.txt`, `URL: ${url}\nHTTP: ${status}\n\n${text}`);
      if (status === 200 && /\bW\b[\s\S]{0,40}\bL\b|standings/i.test(text)) {
        await page.screenshot({ path: `scraped/standings-${slug}.png`, fullPage: true });
        found.push(url);
      }
      console.log(`standings ${url}: HTTP ${status}, ${text.length} chars`);
    } catch (e) {
      console.log(`standings ${url}: ERROR ${e.message}`);
    } finally {
      await page.close();
    }
  }
  return found;
}

// Grab the team's own logo art so the app icon can be built from it.
async function collectLogo() {
  const page = await context.newPage();
  try {
    await page.goto('https://thekcdiamonds.com/', { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await page.waitForTimeout(7_000);
    const srcs = await page.evaluate(() =>
      Array.from(document.querySelectorAll('img'))
        .map((i) => ({ src: i.currentSrc || i.src, alt: i.alt || '', w: i.naturalWidth, h: i.naturalHeight }))
        .filter((i) => i.src && i.w >= 100));
    fs.writeFileSync('scraped/logo-candidates.json', JSON.stringify(srcs, null, 2));
    // Every press photo is named "KCDiamonds_OpeningDay_...", so matching on
    // "diamond" scores the whole gallery and buries the actual mark. Match only
    // on words that mean *logo*, and require a near-square crop.
    const isMark = (i) => /(logo|wordmark|crest|badge|emblem)/i.test(i.src + ' ' + i.alt);
    const seen = new Set();
    const scored = srcs
      .map((i) => ({ ...i, ratio: i.w / Math.max(1, i.h) }))
      .filter((i) => !seen.has(i.src) && seen.add(i.src))
      .filter((i) => i.ratio > 0.6 && i.ratio < 1.7)
      .sort((a, b) => (isMark(b) - isMark(a)) || (b.w - a.w));
    let i = 0;
    for (const cand of scored.slice(0, 4)) {
      try {
        const resp = await page.request.get(cand.src);
        if (resp.ok()) {
          fs.writeFileSync(`scraped/logo-${i}.png`, await resp.body());
          console.log(`logo-${i}: ${cand.w}x${cand.h} ${cand.src.slice(0, 90)}`);
          i++;
        }
      } catch {}
    }
  } catch (e) {
    console.log('logo: ERROR ' + e.message);
  } finally {
    await page.close();
  }
}

await collectSchedule();
await collectStandings();
await collectLogo();

// --- 1. Past-results page: map (date, opponent) -> box score URL; long waits.
const gamesPage = await context.newPage();
await gamesPage.goto('https://thekcdiamonds.com/schedule/past-games-results', {
  waitUntil: 'domcontentloaded',
  timeout: 60_000,
});
await gamesPage.waitForTimeout(10_000);
for (let i = 0; i < 20; i++) {
  await gamesPage.evaluate(() => window.scrollBy(0, 1000));
  await gamesPage.waitForTimeout(800);
}
const gameBlocks = await gamesPage.evaluate(() => {
  const out = [];
  for (const a of document.querySelectorAll('a[href*="ballclubz.com"]')) {
    let el = a;
    for (let i = 0; i < 8 && el.parentElement; i++) {
      el = el.parentElement;
      const t = el.innerText || '';
      if (/\b(Jun|Jul|Aug|Sep|Oct)\s+\d{1,2},\s*2026/.test(t) && t.length < 500) break;
    }
    out.push({ href: a.href.split('#')[0], context: (el.innerText || '').slice(0, 400) });
  }
  return out;
});
fs.writeFileSync('scraped/past-results.txt', await textOf(gamesPage));
fs.writeFileSync('scraped/games.json', JSON.stringify(gameBlocks, null, 2));
console.log(`Mapped ${gameBlocks.length} game blocks`);
await gamesPage.close();

// --- 2. BallClubz team pages: discover every game link the platform lists.
const discovered = new Set(KNOWN_IDS);
for (const url of [
  'https://www.ballclubz.com/kcdiamonds',
  'https://www.ballclubz.com/kcdiamonds/schedule',
  'https://www.ballclubz.com/kcdiamonds/games',
  'https://www.ballclubz.com/kcdiamonds/stats',
]) {
  const p = await context.newPage();
  try {
    await p.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await p.waitForTimeout(7_000);
    for (let i = 0; i < 6; i++) {
      await p.evaluate(() => window.scrollBy(0, 1200));
      await p.waitForTimeout(800);
    }
    const name = 'ballclubz-' + (url.split('/').pop() || 'root');
    fs.writeFileSync(`scraped/${name}.txt`, `URL: ${url}\n\n${await textOf(p)}`);
    const links = await p.evaluate(() =>
      Array.from(document.querySelectorAll('a[href]')).map((a) => a.href)
    );
    for (const h of links) {
      const m = h.match(/ballclubz\.com\/kcdiamonds\/(?:live|game|boxscore)\/([a-z0-9]+)/i);
      if (m) discovered.add(m[1]);
    }
  } catch (e) {
    console.log(`${url}: ERROR ${e.message}`);
  } finally {
    await p.close();
  }
}
for (const g of gameBlocks) {
  const m = g.href.match(/\/(?:live|game|boxscore)\/([a-z0-9]+)/i);
  if (m) discovered.add(m[1]);
}
const ids = [...discovered].sort();
console.log(`Game ids to fetch (${ids.length}):`, ids.join(', '));

// --- 3. Every game: line score (WRAP), opponent box, and KC Diamonds box.
let i = 0;
let boxOk = 0;
for (const id of ids) {
  const url = `https://www.ballclubz.com/kcdiamonds/live/${id}`;
  const name = `box-${String(i++).padStart(2, '0')}-${id}`;
  const page = await context.newPage();
  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await page.waitForTimeout(8_000);
    const wrap = await textOf(page);
    let boxAway = '';
    let boxKC = '';
    // A game that was never scored has no batting table to reach, so don't
    // spend clicks hunting for a tab that cannot be there.
    const neverScored = /not available/.test(wrap);
    let lastErr = '';
    if (!neverScored) {
      // BallClubz renamed this tab from "BOX" to "STATS" in early Aug 2026,
      // which silently emptied every box capture until it was noticed. Try the
      // known labels in turn and keep whichever yields a batting table, so a
      // future rename degrades to one bad night rather than an unbounded one.
      for (const label of ['STATS', 'BOX']) {
        try {
          const tab = page.getByText(label, { exact: true }).first();
          if (!(await tab.count())) continue;
          await tab.click({ timeout: 20_000 });
          await page.waitForTimeout(5_000);
          const away = await textOf(page);
          // The box view has a team toggle; click the KC Diamonds side.
          await page.evaluate(() => {
            const els = Array.from(document.querySelectorAll('*')).filter(
              (e) => e.children.length === 0 && e.textContent.trim() === 'KC Diamonds'
            );
            if (els.length) els[els.length - 1].click();
          });
          await page.waitForTimeout(5_000);
          const kc = await textOf(page);
          if (kc.includes('Batters\t')) { boxAway = away; boxKC = kc; break; }
          boxAway = boxAway || away;
        } catch (e) {
          lastErr = e.message.split('\n')[0];
        }
      }
      if (!boxKC.includes('Batters\t')) {
        console.log(`${name}: no batting table${lastErr ? ' - ' + lastErr : ''}`);
        if (lastErr) boxAway = boxAway || `BOX TAB ERROR: ${lastErr}`;
      } else {
        boxOk++;
      }
    }
    fs.writeFileSync(
      `scraped/${name}.json`,
      JSON.stringify({ id, url, wrap, boxAway, boxKC }, null, 2)
    );
    await page.screenshot({ path: `scraped/${name}.png`, fullPage: true });
    console.log(`${name}: wrap=${wrap.length} away=${boxAway.length} kc=${boxKC.length}`);
  } catch (e) {
    fs.writeFileSync(`scraped/${name}.json`, JSON.stringify({ id, url, error: e.message }));
    console.log(`${name}: ERROR ${e.message}`);
  } finally {
    await page.close();
  }
}

// --- 4. Season aggregate stats, kept current each run.
const statsPage = await context.newPage();
try {
  await statsPage.goto('https://www.ballclubz.com/kcdiamonds/stats', {
    waitUntil: 'domcontentloaded',
    timeout: 60_000,
  });
  await statsPage.waitForTimeout(7_000);
  fs.writeFileSync('scraped/ballclubz-stats.txt', await textOf(statsPage));
} catch (e) {
  fs.writeFileSync('scraped/ballclubz-stats.txt', `ERROR: ${e.message}`);
} finally {
  await statsPage.close();
}

await browser.close();
console.log('Done. Files:', fs.readdirSync('scraped').join(', '));

// A tab rename upstream emptied every batting table for days without anything
// failing, because a missing box score only ever looked like a warning. If no
// game yields a batting table, the selector is broken, not the season - say so
// loudly and fail the run so the alert fires the same night.
console.log(`Batting tables captured: ${boxOk}/${ids.length}`);
if (ids.length > 3 && boxOk === 0) {
  console.log('::error::no batting table captured for ANY game - the box-score ' +
    'tab selector is probably broken upstream (it was renamed BOX -> STATS once)');
  process.exit(1);
}
