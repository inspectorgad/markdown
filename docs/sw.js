// Service worker for the KC Diamonds dashboard.
//
// What it is for: this gets added to a phone's home screen, so it should open
// and show the last-known season from a ballpark with no signal. What it must
// not do is quietly show last week's numbers to someone who has signal - a
// stats page that lies without saying so is worse than one that admits it is
// offline.
//
// So the page and the feed are network-first and fall back to the cache only
// when the network cannot answer. The icons and manifest, which change about
// once a year, are cache-first.

// Bumping this drops every previously cached response on activate, which is how
// a stale copy already sitting on a phone gets thrown away.
const VERSION = "v1";
const CACHE = `kc-diamonds-${VERSION}`;

// Enough to open cold with no network. The feed is not precached - it is
// cached on first successful fetch instead, so a fresh install never ships a
// stale season.
const SHELL = [
  ".",
  "index.html",
  "manifest.json",
  "icon-180.png",
  "icon-192.png",
  "icon-512.png",
  "icon-maskable-512.png",
];

// Where the page reads its season data. Unlike the volleyball dashboard, which
// keeps its own copy beside the page, this one reads straight from the repo and
// falls back to the release asset - both cross-origin. They are listed here
// because a cross-origin response is worth keeping for exactly one reason: it
// is the entire content of the app, and without it an offline launch is a shell
// with an error banner.
const FEED_HOSTS = new Set([
  "raw.githubusercontent.com",
  "github.com",
  "objects.githubusercontent.com",
]);

self.addEventListener("install", (event) => {
  event.waitUntil(
    // Added individually: one missing file would otherwise reject the whole
    // install and leave the page with no worker at all.
    caches.open(CACHE)
      .then((cache) => Promise.allSettled(SHELL.map((url) => cache.add(url))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))
      ))
      .then(() => self.clients.claim())
      // Claiming is not enough on the visit that installs this worker: the page
      // on screen was fetched and rendered before this worker existed, from a
      // cache it had no say in. Reload it once, now that requests come through
      // here. Activation happens once per worker version, so this cannot loop.
      .then(() => self.clients.matchAll({ type: "window" }))
      .then((clients) => clients.forEach((client) => {
        try { client.navigate(client.url); } catch (e) { /* not worth failing over */ }
      }))
      .catch(() => {})
  );
});

/** Store a response without ever letting a cache failure break the page. */
function remember(request, response) {
  if (!response || !response.ok) return;
  const copy = response.clone();
  caches.open(CACHE)
    // A redirected or partial response can be rejected by the Cache API. That
    // is fine - it only costs this one offline copy, so it is swallowed rather
    // than surfaced.
    .then((c) => c.put(request, copy).catch(() => {}))
    .catch(() => {});
}

const isIcon = (url) =>
  /\.(png|svg)$/.test(url.pathname) || url.pathname.endsWith("manifest.json");

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET") return;

  const url = new URL(request.url);
  const sameOrigin = url.origin === self.location.origin;
  const isFeed = FEED_HOSTS.has(url.hostname);
  if (!sameOrigin && !isFeed) return;

  if (sameOrigin && isIcon(url)) {
    event.respondWith(
      caches.match(request).then((hit) => hit || fetch(request).then((resp) => {
        remember(request, resp);
        return resp;
      }))
    );
    return;
  }

  // "Network-first" is not first enough on its own. A plain fetch still
  // consults the browser's HTTP cache, and GitHub Pages serves the page with a
  // max-age, so this handler can answer without a byte leaving the phone and
  // still believe it went to the network. On the volleyball dashboard that is
  // precisely how a corrected result reached every screen except the phone
  // reading it. cache: "reload" bypasses that cache outright.
  //
  // Only for our own origin: the feed request already carries cache: "no-cache"
  // from the page, and rebuilding a cross-origin request here would drop the
  // CORS mode it needs to be readable.
  //
  // Rebuilt from the URL rather than copied, because a navigation request
  // cannot be passed to the Request constructor. Nothing here needs its headers.
  const netRequest = sameOrigin ? new Request(url.href, { cache: "reload" }) : request;

  event.respondWith(
    fetch(netRequest)
      .then((resp) => {
        remember(request, resp);
        return resp;
      })
      .catch(() => caches.match(request).then((hit) => hit
        // A navigation that misses still has somewhere to go: the cached shell.
        || (request.mode === "navigate" ? caches.match("index.html") : undefined)
        || Response.error()))
  );
});
