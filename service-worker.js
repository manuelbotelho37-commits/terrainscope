// TerrainScope : cache limité à ses propres fichiers (même origine que d'autres apps).
const CACHE = 'terrainscope-v7-shell';
const PREFIX = 'terrainscope-';
const FILES = ['./', './index.html', './manifest.webmanifest', './icon-192.png', './icon-512.png'];
const PATHS = new Set(FILES.map(path => new URL(path, self.registration.scope).pathname));

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE)
      .then(cache => Promise.allSettled(FILES.map(file => cache.add(file))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(key => key.startsWith(PREFIX) && key !== CACHE).map(key => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  const req = event.request;
  const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin || !PATHS.has(url.pathname)) return;
  event.respondWith(
    fetch(req).then(response => {
      if (response.ok) {
        const copy = response.clone();
        event.waitUntil(caches.open(CACHE).then(cache => cache.put(req, copy)));
      }
      return response;
    }).catch(async error => {
      const cached = await caches.match(req);
      if (cached) return cached;
      throw error;
    })
  );
});
