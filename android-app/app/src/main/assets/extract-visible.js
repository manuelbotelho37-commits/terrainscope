/*
 * Lecture locale du DOM de la page ouverte par l'utilisateur dans la WebView.
 * Aucun fetch, aucun serveur, aucune extraction hors de la page chargée.
 * La structure des portails peut changer : les résultats restent « à vérifier ».
 */
(function () {
  "use strict";
  const host = location.hostname.toLowerCase();
  const base = location.href;
  const source = /leboncoin\.fr$/.test(host) ? "Le Bon Coin" :
    /seloger\.com$/.test(host) ? "SeLoger" :
    /bienici\.com$/.test(host) ? "Bien’ici" :
    /iadfrance\.fr$/.test(host) ? "IAD" :
    /(immobilier\.notaires\.fr|immonot\.com)$/.test(host) ? "Notaires" : host;
  const clean = s => String(s || "").replace(/\s+/g, " ").trim();
  const normalized = s => clean(s).normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .toLowerCase().replace(/[’']/g, " ").replace(/[^a-z0-9]+/g, " ").trim();
  const requested = clean(window.__terrainScopeRequestedCity || "");
  const requestedNormalized = normalized(requested);
  const land = /\b(?:terrain(?:s)?|parcelle(?:s)?|lot(?:s)?\s+(?:a|à)\s+(?:batir|bâtir)|lotissement|foncier)\b/i;
  const unrelated = /\b(?:appartement(?:s)?|local commercial|bureaux à vendre|parking|garage à vendre)\b/i;
  const houseTitle = /^(?:(?:vente|a vendre|à vendre)\s+)?(?:maison(?:s)?|appartement(?:s)?|villa|villas|pavillon(?:s)?|immeuble(?:s)?|studio|longère)\b/i;
  const listingLink = /\/(?:annonce|annonces|ad|vente|ventes_immobilieres|immobilier|terrain|terrains|achat)[/-]/i;
  const records = [];
  const seen = new Set();
  const absolute = href => { try { return new URL(href, base).href; } catch (_) { return ""; } };
  const valueFrom = (el, sel) => clean(el.querySelector(sel)?.textContent);
  function add(item) {
    if (!item || records.length >= 200) return;
    const title = clean(item.title), url = absolute(item.url), details = clean(item.description);
    if (!url || !/^https?:/.test(url) || !title) return;
    // Attention : « Maison 6 pièces, terrain 655 m² » n'est PAS un terrain.
    // Seule une fiche de terrain (type de bien indiqué dans son titre) entre
    // dans la collecte. L'expression « terrain » dans la description seule
    // ne suffit jamais à convertir une maison en parcelle à vendre.
    if (houseTitle.test(title) || unrelated.test(title)) return;
    if (!land.test(title)) return;
    const key = url.split("#")[0].replace(/\/$/, "");
    if (seen.has(key)) return;
    seen.add(key);
    const raw = title + " " + details;
    const money = clean(item.price) || (raw.match(/(\d[\d \u00a0.]{2,})\s*€/i) || [])[1] || "";
    const area = clean(item.area) || (raw.match(/(\d[\d \u00a0]*(?:[,.]\d+)?)\s*m(?:²|2)(?![a-z0-9])/i) || [])[1] || "";
    const normalizedPrice = money.replace(/[^\d]/g, "");
    const normalizedArea = area.replace(/[\s\u00a0]/g, "").replace(",", ".").replace(/[^\d.]/g, "");
    const cityInCard = requestedNormalized && (" " + normalized(raw) + " ").includes(" " + requestedNormalized + " ");
    records.push({
      source, url, title, description: details.slice(0, 1500),
      city: requested, cityConfirmed: !!cityInCard,
      seller: clean(item.seller), address: clean(item.address), lot: clean(item.lot),
      priceEuros: Number(normalizedPrice) || 0,
      areaM2: Number(normalizedArea) || 0
    });
  }
  // Fiches JSON-LD déjà intégrées à la page : elles complètent les cartes HTML.
  function visitJson(value, level) {
    if (!value || level > 6) return;
    if (Array.isArray(value)) { value.slice(0, 200).forEach(x => visitJson(x, level + 1)); return; }
    if (typeof value !== "object") return;
    const candidate = value.item && typeof value.item === "object" ? value.item : value;
    const title = clean(candidate.name || candidate.headline);
    const offer = candidate.offers || value.offers || {};
    const offer0 = Array.isArray(offer) ? offer[0] : offer;
    const seller = offer0.seller || candidate.seller || candidate.provider;
    const sellerName = typeof seller === "string" ? seller : seller?.name || "";
    if (title && (candidate.url || candidate["@id"] || value.url)) {
      add({title, url:candidate.url || candidate["@id"] || value.url,
        description:candidate.description || "",
        price:String(offer0.price || candidate.price || ""),
        area:String(candidate.floorSize?.value || candidate.area?.value || ""),
        seller:sellerName});
    }
    for (const key of ["itemListElement", "@graph", "mainEntity", "hasPart", "items"]) {
      if (value[key]) visitJson(value[key], level + 1);
    }
  }
  for (const script of document.querySelectorAll('script[type="application/ld+json"]')) {
    if (script.textContent.length > 500000) continue;
    try { visitJson(JSON.parse(script.textContent), 0); } catch (_) {}
  }
  const selectors = [
    'article', '[data-testid*="listing"]', '[data-testid*="ad-card"]',
    '[data-testid*="search-result"]', '[class*="ListingCard"]',
    '[class*="listing-card"]', '[class*="PropertyCard"]',
    '[class*="property-card"]', '[class*="annonce-card"]',
    '[class*="AnnonceCard"]', 'li[data-testid]', '[data-cy*="listing"]',
    '[data-qa-id="aditem_container"]', 'a[href*="/ad/ventes_immobilieres/"]',
    '[data-test-id*="listing"]', '[data-qa-id*="classified"]'
  ];
  const nodes = [...new Set(selectors.flatMap(sel => {
    try { return [...document.querySelectorAll(sel)]; } catch (_) { return []; }
  }))];
  for (const node of nodes.slice(0, 800)) {
    if (records.length >= 200) break;
    if (node.getClientRects().length === 0) continue;
    const text = clean(node.innerText || node.textContent);
    if (text.length < 18 || text.length > 2500 || !land.test(text)) continue;
    // Le Bon Coin expose souvent une carte sous la forme d'un <a> racine,
    // sans lien <a> enfant. On doit donc inclure ce nœud lui-même.
    const links = [
      ...(node.matches?.("a[href]") ? [node] : []),
      ...node.querySelectorAll("a[href]")
    ].filter(a => !/^javascript:/i.test(a.getAttribute("href") || ""));
    const linked = links.find(a => listingLink.test(a.getAttribute("href") || "")) || links[0];
    if (!linked) continue;
    let title = valueFrom(node, "h2, h3, h4, [data-testid*='title'], [class*='title']") || clean(linked.textContent);
    if (!title || title.length < 5) title = text.slice(0, 170);
    // Les cartes Le Bon Coin « Terrain · 500m² » ont parfois pour titre
    // générique « Immobilier Neuf ». Ne pas les rater, mais NE JAMAIS
    // importer une carte explicitement étiquetée Maison/Appartement.
    const primaryHouse = /\b(?:Maison|Appartement|Studio|Villa|Pavillon)\s*[·:,]/i.test(text);
    const terrainKind = /\bTerrain\s*[·:]\s*\d+[\d\s]*(?:m²|m2)/i.test(text);
    if (!land.test(title) && terrainKind && !primaryHouse)
      title = "Terrain à vendre · " + title;
    const seller = valueFrom(node, '[data-testid*="agency"], [data-testid*="seller"], [class*="agency-name"], [class*="seller-name"]');
    const price = valueFrom(node, '[data-testid*="price"], [class*="price"], [data-cy*="price"]');
    const area = valueFrom(node, '[data-testid*="area"], [class*="surface"], [class*="area"]');
    const address = valueFrom(node, '[data-testid*="address"], [class*="address"]');
    const lot = (text.match(/\blot\s*(?:n[°o.]?\s*)?([A-Z]?\d{1,4})\b/i) || [])[1] || "";
    add({title, url:linked.href, description:text, price, area, seller, address, lot});
  }
  return JSON.stringify({
    source, pageUrl: base, pageTitle: document.title,
    requestedCity: requested,
    declaredCount: (() => {
      const headline = clean(document.querySelector("h1")?.textContent || "");
      const header = clean(document.body?.innerText?.slice(0, 4000) || "");
      const match = (headline + " " + header).match(/\b(\d{1,5})\s+annonces?\b/i);
      return match ? Number(match[1]) : null;
    })(),
    scannedCards: nodes.length, retrieved: records.length,
    listings: records,
    warning: "Import limité au contenu de la page chargée. Ni exhaustivité ni disponibilité garanties."
  });
})();