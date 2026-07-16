// Scrapes public KC Diamonds / Professional Softball League pages using a real
// headless browser (runs in GitHub Actions, where outbound network is open).
// Saves rendered text, HTML, and full-page screenshots under scraped/ so the
// results can be parsed into the app's seed data.
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

async function scrapePage(name, url, { scroll = false, saveHtml = true } = {}) {
  const page = await context.newPage();
  try {
    const resp = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await page.waitForTimeout(6_000);
    if (scroll) {
      // Trigger lazy-loaded content.
      for (let i = 0; i < 6; i++) {
        await page.evaluate(() => window.scrollBy(0, 1500));
        await page.waitForTimeout(1_500);
      }
    }
    const status = resp ? resp.status() : 0;
    const text = await page.evaluate(() => (document.body ? document.body.innerText : ''));
    fs.writeFileSync(`scraped/${name}.txt`, `URL: ${url}\nHTTP: ${status}\n\n${text}`);
    if (saveHtml) fs.writeFileSync(`scraped/${name}.html`, await page.content());
    await page.screenshot({ path: `scraped/${name}.png`, fullPage: true });
    console.log(`${name}: HTTP ${status}, ${text.length} chars of text`);
    return page; // caller must close
  } catch (e) {
    fs.writeFileSync(`scraped/${name}.txt`, `URL: ${url}\nERROR: ${e.message}`);
    console.log(`${name}: ERROR ${e.message}`);
    return page;
  }
}

// --- Team news: crawl the list page, then every article (game recaps live here).
{
  const page = await scrapePage('team-news', 'https://thekcdiamonds.com/news', { scroll: true });
  let links = [];
  try {
    links = await page.evaluate(() =>
      Array.from(document.querySelectorAll('a[href]'))
        .map((a) => a.href)
        .filter((h) => /thekcdiamonds\.com\/(news|post|blog)\/.+/i.test(h))
    );
  } catch {}
  await page.close();
  const unique = [...new Set(links)].slice(0, 30);
  console.log(`Found ${unique.length} news article links`);
  let i = 0;
  for (const url of unique) {
    const p = await scrapePage(`news-${String(i++).padStart(2, '0')}`, url, { saveHtml: false });
    await p.close();
  }
}

// --- League statistics page, with scrolling in case tables lazy-load.
{
  const p1 = await scrapePage('league-statistics', 'https://www.professionalsoftballleague.com/statistics', { scroll: true });
  await p1.close();
}

// --- Reference pages (roster/schedule) so each scrape run stays current.
for (const [name, url] of [
  ['team-schedule', 'https://thekcdiamonds.com/schedule/schedule'],
  ['team-roster', 'https://thekcdiamonds.com/about/meet-the-team'],
  ['league-schedule', 'https://www.professionalsoftballleague.com/schedule'],
]) {
  const p = await scrapePage(name, url);
  await p.close();
}

await browser.close();
console.log('Done. Files in scraped/:', fs.readdirSync('scraped').join(', '));
