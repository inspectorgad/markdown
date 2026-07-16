// Scrapes public KC Diamonds pages using a real headless browser (runs in
// GitHub Actions, where outbound network is open). Saves rendered text, HTML,
// and screenshots under scraped/ so the results can be parsed into seed data.
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

async function scrapePage(name, url, { scroll = true, saveHtml = true } = {}) {
  const page = await context.newPage();
  try {
    const resp = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await page.waitForTimeout(6_000);
    if (scroll) {
      for (let i = 0; i < 8; i++) {
        await page.evaluate(() => window.scrollBy(0, 1500));
        await page.waitForTimeout(1_200);
      }
    }
    const status = resp ? resp.status() : 0;
    const text = await page.evaluate(() => (document.body ? document.body.innerText : ''));
    fs.writeFileSync(`scraped/${name}.txt`, `URL: ${url}\nHTTP: ${status}\n\n${text}`);
    if (saveHtml) fs.writeFileSync(`scraped/${name}.html`, await page.content());
    await page.screenshot({ path: `scraped/${name}.png`, fullPage: true });
    console.log(`${name}: HTTP ${status}, ${text.length} chars of text`);
  } catch (e) {
    fs.writeFileSync(`scraped/${name}.txt`, `URL: ${url}\nERROR: ${e.message}`);
    console.log(`${name}: ERROR ${e.message}`);
  }
  return page; // caller must close
}

// The pages that matter: past results, the BallClubz stats platform, and news.
const TARGETS = [
  ['past-results', 'https://thekcdiamonds.com/schedule/past-games-results'],
  ['ballclubz-stats', 'https://www.ballclubz.com/kcdiamonds/stats'],
  ['ballclubz-live', 'https://www.ballclubz.com/kcdiamonds/live/2b7aa5e'],
  ['news-media', 'https://thekcdiamonds.com/news-media/news-media'],
];

const discovered = new Set();
for (const [name, url] of TARGETS) {
  const page = await scrapePage(name, url);
  try {
    const links = await page.evaluate(() =>
      Array.from(document.querySelectorAll('a[href]')).map((a) => a.href)
    );
    for (const h of links) {
      // Follow anything that looks like a game page, box score, or news article.
      if (/ballclubz\.com\/kcdiamonds\/(?!stats$)[a-z0-9/_-]+/i.test(h)) discovered.add(h.split('#')[0]);
      if (/thekcdiamonds\.com\/(news|post|blog|news-media)\/.+/i.test(h)) discovered.add(h.split('#')[0]);
    }
  } catch {}
  await page.close();
}

let i = 0;
for (const url of [...discovered].slice(0, 40)) {
  const p = await scrapePage(`sub-${String(i++).padStart(2, '0')}`, url, { saveHtml: false });
  await p.close();
}
fs.writeFileSync('scraped/discovered-links.txt', [...discovered].join('\n'));

await browser.close();
console.log('Done. Files in scraped/:', fs.readdirSync('scraped').join(', '));
