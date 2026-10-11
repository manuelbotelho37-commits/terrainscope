// Cache uniquement les fichiers de l’application : jamais les annonces ni les recherches.
const CACHE = 'terrainscope-v3-shell';
const FILES = ['./','./index.html','./manifest.webmanifest','./icon-192.png','./icon-512.png'];
self.addEventListener('install', event => { event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(FILES)).then(() => self.skipWaiting())); });
self.addEventListener('activate', event => {event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));});
self.addEventListener('fetch', event => {const req=event.request; const url=new URL(req.url); if(req.method!=='GET'||url.origin!==self.location.origin)return; if(!FILES.map(path=>new URL(path,self.registration.scope).pathname).includes(url.pathname))return; event.respondWith(fetch(req).then(resp=>{if(resp.ok){const copy=resp.clone();caches.open(CACHE).then(c=>c.put(req,copy));}return resp;}).catch(()=>caches.match(req)));});
