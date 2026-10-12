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
  const requestedPostal = clean(window.__terrainScopeRequestedPostal || "");
  const knownCities = (Array.isArray(window.__terrainScopeKnownCities)
    ? window.__terrainScopeKnownCities : []).map(normalized).filter(Boolean);
  const strongLocationPrefixes = [
    "sur la commune de", "dans la commune de", "opportunite a",
    "situe a", "situee a", "localise a", "localisee a",
    "terrain situe a", "terrain situee a", "parcelle situee a"
  ];
  const land = /\b(?:terrain(?:s)?|parcelle(?:s)?|lot(?:s)?\s+(?:a|à)\s+(?:batir|bâtir)|lotissement|foncier)\b/i;
  const unrelated = /\b(?:appartement(?:s)?|local commercial|bureaux à vendre|parking|garage à vendre)\b/i;
  const houseTitle = /^(?:(?:vente|a vendre|à vendre)\s+)?(?:maison(?:s)?|appartement(?:s)?|villa|villas|pavillon(?:s)?|immeuble(?:s)?|studio|longère)\b/i;
  const listingLink = /\/(?:annonce|annonces|ad|vente|ventes_immobilieres|immobilier|terrain|terrains|achat)[/-]/i;
  const records = [];
  const seen = new Map();
  const absolute = href => { try { return new URL(href, base).href; } catch (_) { return ""; } };
  const valueFrom = (el, sel) => clean(el.querySelector(sel)?.textContent);
  function add(item) {
    if (!item || records.length >= 200) return;
    const title = clean(item.title), url = absolute(item.url), details = clean(item.description);
    if (!url || !/^https?:/.test(url) || !title) return;
    // Ne jamais transformer le lien « rechercher d'autres terrains » ou la
    // page de résultats elle-même en annonce consultable.
    const parsed = new URL(url);
    const path = parsed.pathname.toLowerCase();
    if (url === base || /\/(?:recherche|cl)\//.test(path) ||
        /\/annonces-immobilieres\/vente\/terrain\/[^/]+-37\/?$/.test(path)) return;
    if (!/\/(?:annonce(?:s)?|ad|ventes_immobilieres|annonce-immobiliere|annonce-immo)(?:\/|[-])/.test(path))
      return;
    // Attention : « Maison 6 pièces, terrain 655 m² » n'est PAS un terrain.
    // Seule une fiche de terrain (type de bien indiqué dans son titre) entre
    // dans la collecte. L'expression « terrain » dans la description seule
    // ne suffit jamais à convertir une maison en parcelle à vendre.
    if (houseTitle.test(title) || unrelated.test(title)) return;
    if (!land.test(title)) return;
    const key = url.split("#")[0].replace(/\/$/, "");
    const raw = title + " " + details;
    const money = clean(item.price) || (raw.match(/(\d[\d \u00a0.]{2,})\s*€/i) || [])[1] || "";
    const area = clean(item.area) || (raw.match(/(\d[\d \u00a0]*(?:[,.]\d+)?)\s*m(?:²|2)(?![a-z0-9])/i) || [])[1] || "";
    // Prix : la carte peut contenir « 65 900 € · 210 €/m² ». Ne jamais
    // concaténer ces nombres (65900210) ni assimiler le prix/m² au total.
    const moneyTokens = [...money.matchAll(/(\d[\d\s\u00a0.]{0,17})\s*(?:€|euros?|EUR)/gi)];
    const candidates = moneyTokens.map(m => Number(m[1].replace(/[^\d]/g, "")))
      .filter(n => Number.isFinite(n) && n >= 1000 && n < 100000000);
    const priceNumber = candidates.length ? Math.max(...candidates) :
      Number((money.match(/^\s*[\d\s\u00a0.]+\s*$/) ? money : "")
        .replace(/[^\d]/g, "")) || 0;
    const areaMatch = area.match(/(\d[\d\s\u00a0]*(?:[,.]\d+)?)\s*m(?:²|2)/i);
    const areaCandidate = areaMatch ? areaMatch[1] : area;
    const normalizedArea = areaCandidate.replace(/[\s\u00a0]/g, "").replace(",", ".").replace(/[^\d.]/g, "");
    // La commune mentionnée seulement dans une description commerciale
    // n'est pas une preuve : un terrain de Crotelles peut être "à 5 min de Monnaie".
    // Préférer la localité structurée, le couple ville+code postal, puis le titre.
    const explicitLocation = normalized(item.location || "");
    // Certaines annonces sont indexées « Monnaie » alors qu'elles annoncent
    // « opportunité à Crotelles » dans le descriptif ; même code postal,
    // mais PAS la même commune. Chercher une localisation explicite
    // et non une simple mention « proche de ».
    const normalizedDetails = " " + normalized(details) + " ";
    const conflictingLocality = knownCities.some(other =>
      other !== requestedNormalized && strongLocationPrefixes.some(prefix =>
        normalizedDetails.includes(" " + prefix + " " + other + " ")
      )
    );
    const full = " " + normalized(raw) + " ";
    const name = " " + normalized(title) + " ";
    const explicitMatches = !explicitLocation ||
      (" " + explicitLocation + " ").includes(" " + requestedNormalized + " ");
    const cityInCard = requestedNormalized && !conflictingLocality && (
      explicitLocation
        ? explicitMatches
        : (requestedPostal && full.includes(" " + requestedNormalized + " " + requestedPostal + " ")) ||
          name.includes(" " + requestedNormalized + " ")
    );
    const current = {
      source, url, title, description: details.slice(0, 1500),
      city: requested, cityConfirmed: !!cityInCard,
      locationConflict: !!(conflictingLocality || (explicitLocation && !explicitMatches)),
      seller: clean(item.seller), address: clean(item.address), lot: clean(item.lot),
      priceEuros: priceNumber,
      areaM2: Number(normalizedArea) || 0
    };
    if (seen.has(key)) {
      // JSON-LD peut ne contenir ni vendeur ni prix alors que la carte HTML
      // les fournit. Une même annonce doit être enrichie, non éliminée.
      const earlier = records[seen.get(key)];
      for (const field of ["seller", "address", "lot", "description"]) {
        if (!earlier[field] && current[field]) earlier[field] = current[field];
      }
      if (!earlier.priceEuros && current.priceEuros) earlier.priceEuros = current.priceEuros;
      if (!earlier.areaM2 && current.areaM2) earlier.areaM2 = current.areaM2;
      // Une adresse structurée sur une autre commune ne doit jamais être
      // écrasée par une mention promotionnelle de la commune demandée.
      if (current.locationConflict) {
        earlier.locationConflict = true;
        earlier.cityConfirmed = false;
      } else if (current.cityConfirmed && !earlier.locationConflict) {
        earlier.cityConfirmed = true;
      }
    } else {
      seen.set(key, records.length);
      records.push(current);
    }
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
    const address = candidate.address || offer0?.availableAtOrFrom;
    const addressObj = typeof address === "object" && address !== null ? address : {};
    const location = typeof address === "string" ? address : addressObj.addressLocality || "";
    if (title && (candidate.url || candidate["@id"] || value.url)) {
      add({title, url:candidate.url || candidate["@id"] || value.url,
        description:candidate.description || "",
        price:String(offer0.price || candidate.price || ""),
        area:String(candidate.floorSize?.value || candidate.area?.value || ""),
        seller:sellerName,location, address:typeof address==="string"?address:""});
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
    '[data-test-id*="listing"]', '[data-qa-id*="classified"]',
    'a[href*="/annonce/terrain-vente-"]',
    'a[href*="/annonce/vente/"]',
    'a[href*="/annonce/achat/"]',
    'a[href*="/annonces/vente/terrain"]',
    '[data-testid*="property-card"]', '[class*="search-result-card"]'
  ];
  const nodes = [...new Set(selectors.flatMap(sel => {
    try { return [...document.querySelectorAll(sel)]; } catch (_) { return []; }
  }))];
  for (const node of nodes.slice(0, 800)) {
    if (records.length >= 200) break;
    if (node.getClientRects().length === 0) continue;
    let card = node;
    // iad et Bien'ici rendent parfois leur titre comme un lien isolé ;
    // prix et surface se trouvent alors dans son parent. Remonter uniquement
    // tant que l'on reste dans UNE fiche, jamais au conteneur de la liste.
    if (node.matches?.("a[href]")) {
      const own = clean(node.innerText || node.textContent);
      if (!/\d[\d\s\u00a0]*\s*€/.test(own)) {
        let parent = node.parentElement;
        for (let depth = 0; parent && depth < 3; depth++, parent = parent.parentElement) {
          const nearby = clean(parent.innerText || parent.textContent);
          if (nearby.length > 2200) break;
          if (/\d[\d\s\u00a0]*\s*€/.test(nearby) && land.test(nearby)) {
            card = parent;
            break;
          }
        }
      }
    }
    const text = clean(card.innerText || card.textContent);
    if (text.length < 18 || text.length > 2500 || !land.test(text)) continue;
    // Le Bon Coin expose souvent une carte sous la forme d'un <a> racine,
    // sans lien <a> enfant. On doit donc inclure ce nœud lui-même.
    const links = [
      ...(node.matches?.("a[href]") ? [node] : []),
      ...card.querySelectorAll("a[href]")
    ].filter(a => !/^javascript:/i.test(a.getAttribute("href") || ""));
    const linked = links.find(a => listingLink.test(a.getAttribute("href") || "")) || links[0];
    if (!linked) continue;
    let title = valueFrom(card, "h2, h3, h4, [data-testid*='title'], [class*='title']") || clean(linked.textContent);
    if (!title || title.length < 5) title = text.slice(0, 170);
    // Les cartes Le Bon Coin « Terrain · 500m² » ont parfois pour titre
    // générique « Immobilier Neuf ». Ne pas les rater, mais NE JAMAIS
    // importer une carte explicitement étiquetée Maison/Appartement.
    const primaryHouse = /\b(?:Maison|Appartement|Studio|Villa|Pavillon)\s*[·:,]/i.test(text);
    const terrainKind = /\bTerrain\s*[·:]\s*\d+[\d\s]*(?:m²|m2)/i.test(text);
    if (!land.test(title) && terrainKind && !primaryHouse)
      title = "Terrain à vendre · " + title;
    const seller = valueFrom(card, '[data-testid*="agency"], [data-testid*="seller"], [data-testid*="advertiser"], [class*="agency-name"], [class*="seller-name"], [class*="advertiser-name"], [class*="merchant-name"]');
    const locationText = valueFrom(card, '[data-testid*="location"], [data-testid*="locality"], [data-qa-id*="location"], [class*="property-location"], [class*="card-location"]');
    const price = valueFrom(card, '[data-testid*="price"], [class*="price"], [data-cy*="price"]');
    const area = valueFrom(card, '[data-testid*="area"], [class*="surface"], [class*="area"]');
    const address = valueFrom(card, '[data-testid*="address"], [class*="address"]');
    const lot = (text.match(/\blot\s*(?:n[°o.]?\s*)?([A-Z]?\d{1,4})\b/i) || [])[1] || "";
    add({title, url:linked.href, description:text, price, area, seller, address, lot, location:locationText});
  }
  const pageTop = clean(document.body?.innerText?.slice(0, 3500) || "");
  const accessBlocked = records.length === 0 &&
    /(?:captcha|recaptcha|verify (?:that )?you are human|access denied|too many requests|acces refuse|accès refusé|vérifiez que vous êtes humain|confirmez que vous êtes humain|robot verification|verification humaine|vérification humaine)/i.test(pageTop + " " + document.title);
  return JSON.stringify({
    source, pageUrl: base, pageTitle: document.title,
    accessBlocked,
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