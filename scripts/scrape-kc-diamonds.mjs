// Scrapes public KC Diamonds / Professional Softball League pages using a real
// headless browser (runs in GitHub Actions, where outbound network is open).
// Saves rendered text, HTML, and full-page screenshots under scraped/ so the
// results can be parsed into the app's seed data.
import { chromium } from 'playwright';
import fs from 'fs';

const PAGES = [
  ['team-home', 'https://thekcdiamonds.com/'],
  ['team-schedule', 'https://thekcdiamonds.com/schedule/schedule'],
  ['team-roster', 'https://thekcdiamonds.com/about/meet-the-team'],
  ['league-home', 'https://www.professionalsoftballleague.com/'],
  ['league-schedule', 'https://www.professionalsoftballleague.com/schedule'],
  ['league-standings', 'https://www.professionalsoftballleague.com/standings'],
  ['league-stats', 'https://www.professionalsoftballleague.com/stats'],
];

fs.mkdirSync('scraped', { recursive: true });

const browser = await chromium.launch();
const context = await browser.newContext({
  userAgent:
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
  viewport: { width: 1400, height: 2400 },
});

for (const [name, url] of PAGES) {
  const page = await context.newPage();
  try {
    const resp = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    // Give JS-rendered sites (Wix etc.) time to paint their content.
    await page.waitForTimeout(8_000);
    const status = resp ? resp.status() : 0;
    const text = await page.evaluate(() => (document.body ? document.body.innerText : ''));
    fs.writeFileSync(`scraped/${name}.txt`, `URL: ${url}\nHTTP: ${status}\n\n${text}`);
    fs.writeFileSync(`scraped/${name}.html`, await page.content());
    await page.screenshot({ path: `scraped/${name}.png`, fullPage: true });
    console.log(`${name}: HTTP ${status}, ${text.length} chars of text`);
  } catch (e) {
    fs.writeFileSync(`scraped/${name}.txt`, `URL: ${url}\nERROR: ${e.message}`);
    console.log(`${name}: ERROR ${e.message}`);
  } finally {
    await page.close();
  }
}

// Follow links that look like results/stats/box scores discovered on the pages above.
const discovered = new Set();
for (const [name] of PAGES) {
  const file = `scraped/${name}.html`;
  if (!fs.existsSync(file)) continue;
  const html = fs.readFileSync(file, 'utf8');
  const re = /href="(https?:\/\/(?:www\.)?(?:thekcdiamonds\.com|professionalsoftballleague\.com)[^"]*(?:stat|score|result|standing|game|box)[^"]*)"/gi;
  let m;
  while ((m = re.exec(html)) !== null) discovered.add(m[1].split('#')[0]);
}

let i = 0;
for (const url of [...discovered].slice(0, 15)) {
  const name = `extra-${String(i++).padStart(2, '0')}`;
  const page = await context.newPage();
  try {
    const resp = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await page.waitForTimeout(8_000);
    const text = await page.evaluate(() => (document.body ? document.body.innerText : ''));
    fs.writeFileSync(`scraped/${name}.txt`, `URL: ${url}\nHTTP: ${resp ? resp.status() : 0}\n\n${text}`);
    await page.screenshot({ path: `scraped/${name}.png`, fullPage: true });
    console.log(`${name}: ${url} -> ${text.length} chars`);
  } catch (e) {
    console.log(`${name}: ${url} ERROR ${e.message}`);
  } finally {
    await page.close();
  }
}

await browser.close();
console.log('Done. Files in scraped/:', fs.readdirSync('scraped').join(', '));
