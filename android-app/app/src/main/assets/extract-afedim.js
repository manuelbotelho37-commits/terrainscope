/*
 * Extracteur strict du programme officiel AFEDIM consulté dans la WebView.
 * Lire les lots dans le tableau visible (optionnés inclus), sans requête réseau
 * supplémentaire ni déduction du nombre de parcelles.
 */
(function () {
  "use strict";
  const clean = s => String(s || "").replace(/\s+/g," ").trim();
  const norm = s => clean(s).normalize("NFD").replace(/[\u0300-\u036f]/g,"")
    .toLowerCase().replace(/[^a-z0-9]+/g," ").trim();
  const requestedCity = String(window.__terrainScopeRequestedCity || "");
  const city = norm(requestedCity);
  const host = location.hostname.toLowerCase();
  const body = clean(document.body && document.body.innerText || "");
  // Quatre fiches PROGRAMME vérifiées. Pas de mélange avec les villes voisines.
  const programSlugs = {
    "azay sur cher": "azay-sur-cher",
    "artannes sur indre": "artannes-sur-indre",
    "chanceaux sur choisille": "chanceaux-sur-choisille",
    "la croix en touraine": "la-croix-en-touraine"
  };
  const programNames = {
    "azay sur cher": "La Closerie de la Trute",
    "artannes sur indre": "Programme terrains d'Artannes-sur-Indre",
    "chanceaux sur choisille": "Les Vergers de Pont Pérou",
    "la croix en touraine": "Les Jardins de la Vignerie"
  };
  const slug = programSlugs[city] || "";
  const allowed = (host === "afedim.fr" || host === "www.afedim.fr") &&
    !!slug && location.pathname.toLowerCase().includes("/" + slug + "-37/") &&
    norm(document.title + " " + body.slice(0, 5000)).includes(city) &&
    body.includes("Les terrains disponibles") &&
    !/ce programme n.est plus disponible/i.test(body.slice(0, 2500));
  const listings = [];
  if (allowed) {
    const first = body.indexOf("Les terrains disponibles");
    const start = first >= 0 ? first : 0;
    const last = body.indexOf("Etat des risques", start);
    const scoped = body.slice(start, last > start ? last : start + 15000);
    // Les numéros de lots peuvent être "12 BIS". Relever uniquement
    // les lignes de terrains avec surface et prix explicites.
    const pattern = /\bTerrain\s+(\d{1,3}(?:\s*BIS)?)\b([\s\S]{0,170}?)\b(\d{2,5})\s*m(?:²|2)\s*([\d\s\u00a0.]{2,14}),00\s*EUR/gi;
    const found = new Map();
    for (const match of scoped.matchAll(pattern)) {
      const lot = match[1];
      const area = Number(match[3]);
      const euros = Number(match[4].replace(/[^\d]/g, ""));
      if (area < 100 || area > 10000 || euros < 1000 || euros > 1000000) continue;
      const optioned = /optionn[eé]/i.test(match[2]);
      const prev = found.get(lot);
      if (!prev || prev.optioned !== optioned) {
        found.set(lot, {lot, area, euros, optioned});
      }
    }
    for (const lot of found.values()) {
      listings.push({
        source: "CM-CIC · AFEDIM",
        url: location.href.split("#")[0],
        title: "Terrain lot " + lot.lot + " · " + programNames[city],
        seller: "CREDIT MUTUEL AMENAGEMENT FONCIER",
        description: (lot.optioned ? "Optionné : disponibilité à confirmer. " :
          "Statut de disponibilité à confirmer. ") +
          "Programme officiel AFEDIM à " + requestedCity + ".",
        city: requestedCity, cityConfirmed: true,
        priceEuros: lot.euros, areaM2: lot.area, lot: lot.lot,
        address: "",
        imageUrl: ""
      });
    }
  }
  return JSON.stringify({
    source: "CM-CIC · AFEDIM", requestedCity, listings,
    retrieved: listings.length, declaredCount: null,
    scannedCards: listings.length, nextPageUrl: "",
    accessBlocked: !allowed || (/\b(?:captcha|access denied|accès refusé)\b/i.test(body) && !listings.length),
    warning: "Liste lue sur le programme officiel ; statuts et ventes à reconfirmer auprès du lotisseur."
  });
})();