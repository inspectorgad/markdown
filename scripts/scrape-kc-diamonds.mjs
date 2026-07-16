// Scrapes KC Diamonds game results and box scores using a real headless
// browser (runs in GitHub Actions, where outbound network is open).
// Output under scraped/: games.json (date/opponent/box-score-url mapping from
// the team site) and box-*.json (line score + full BOX tab text per game).
import { chromium } from 'playwright';
import fs from 'fs';

fs.rmSync('scraped', { recursive: true, force: true });
fs.mkdirSync('scraped', { recursive: true });

const browser = await chromium.launch();
const context = await browser.newContext({
  userAgent:
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
  viewport: { width: 1400, height: 2400 },
});

// --- 1. Past results page: map each game (date, opponent) to its box score URL.
const gamesPage = await context.newPage();
await gamesPage.goto('https://thekcdiamonds.com/schedule/past-games-results', {
  waitUntil: 'domcontentloaded',
  timeout: 60_000,
});
await gamesPage.waitForTimeout(6_000);
for (let i = 0; i < 15; i++) {
  await gamesPage.evaluate(() => window.scrollBy(0, 1200));
  await gamesPage.waitForTimeout(1_000);
}
const gameBlocks = await gamesPage.evaluate(() => {
  const out = [];
  for (const a of document.querySelectorAll('a[href*="ballclubz.com/kcdiamonds/live/"]')) {
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
fs.writeFileSync(
  'scraped/past-results.txt',
  await gamesPage.evaluate(() => document.body.innerText)
);
fs.writeFileSync('scraped/games.json', JSON.stringify(gameBlocks, null, 2));
console.log(`Mapped ${gameBlocks.length} game blocks with box score links`);
await gamesPage.close();

// --- 2. Each game page: capture the line score (WRAP) and full box score (BOX tab).
const urls = [...new Set(gameBlocks.map((g) => g.href))];
let i = 0;
for (const url of urls) {
  const name = `box-${String(i++).padStart(2, '0')}`;
  const page = await context.newPage();
  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await page.waitForTimeout(7_000);
    const wrap = await page.evaluate(() => document.body.innerText);
    let box = '';
    try {
      await page.getByText('BOX', { exact: true }).first().click();
      await page.waitForTimeout(5_000);
      box = await page.evaluate(() => document.body.innerText);
    } catch (e) {
      box = `BOX TAB ERROR: ${e.message}`;
    }
    fs.writeFileSync(`scraped/${name}.json`, JSON.stringify({ url, wrap, box }, null, 2));
    await page.screenshot({ path: `scraped/${name}.png`, fullPage: true });
    console.log(`${name}: ${url} wrap=${wrap.length} box=${box.length}`);
  } catch (e) {
    fs.writeFileSync(`scraped/${name}.json`, JSON.stringify({ url, error: e.message }));
    console.log(`${name}: ERROR ${e.message}`);
  } finally {
    await page.close();
  }
}

// --- 3. Season stats page (aggregate batting/pitching), kept current each run.
const statsPage = await context.newPage();
try {
  await statsPage.goto('https://www.ballclubz.com/kcdiamonds/stats', {
    waitUntil: 'domcontentloaded',
    timeout: 60_000,
  });
  await statsPage.waitForTimeout(7_000);
  fs.writeFileSync(
    'scraped/ballclubz-stats.txt',
    await statsPage.evaluate(() => document.body.innerText)
  );
} catch (e) {
  fs.writeFileSync('scraped/ballclubz-stats.txt', `ERROR: ${e.message}`);
} finally {
  await statsPage.close();
}

await browser.close();
console.log('Done. Files:', fs.readdirSync('scraped').join(', '));
