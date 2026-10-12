/* Tests reproductibles de l'extracteur de pages, sans réseau et sans Android. */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");

const code = fs.readFileSync(path.resolve(__dirname, "../app/src/main/assets/extract-visible.js"), "utf8");
let checks = 0;

function extract(host, city, jsonDocuments = [], cards = []) {
  const document = {
    title: "Terrains à vendre",
    body: { innerText: "23 annonces pour la commune recherchée" },
    querySelector() { return null; },
    querySelectorAll(selector) {
      if (selector === 'script[type="application/ld+json"]') {
        return jsonDocuments.map(data => ({ textContent: JSON.stringify(data) }));
      }
      if (selector === "article") return cards;
      return [];
    }
  };
  const context = {
    document, window: { __terrainScopeRequestedCity: city },
    location: { hostname: host, href: "https://" + host + "/recherche/achat/" },
    URL
  };
  return JSON.parse(vm.runInNewContext(code, context));
}
function check(condition, message) {
  assert.ok(condition, message);
  checks++;
}
const fixture = {
  "@type": "ItemList",
  itemListElement: [{ item: {
    name: "Terrain constructible de 500 m² à Monnaie",
    description: "Terrain situé à Monnaie, 500 m²",
    url: "https://www.bienici.com/annonce/vente/terrain-123",
    offers: { price: "85000", seller: { name: "Guy Hoquet" } }
  }}]
};
const fromJson = extract("www.bienici.com", "Monnaie", [fixture]);
check(fromJson.retrieved === 1, "JSON-LD terrain lu");
check(fromJson.listings[0].priceEuros === 85000, "prix JSON-LD");
check(fromJson.listings[0].areaM2 === 500, "surface m² JSON-LD");
check(fromJson.listings[0].seller === "Guy Hoquet", "annonceur agence");
check(fromJson.listings[0].cityConfirmed, "commune confirmée sur la fiche");
const htmlCard = {
  innerText: "Terrain à Monnaie 602 m² • 128 000 € • agence",
  getClientRects: () => [1],
  querySelector(selector) {
    if (selector.includes("h2")) return { textContent: "Terrain constructible à Monnaie" };
    if (selector.includes("price")) return { textContent: "128 000 €" };
    if (selector.includes("surface")) return { textContent: "602 m²" };
    return null;
  },
  querySelectorAll(selector) {
    return selector === "a[href]" ?
      [{ href: "https://www.seloger.com/annonce/456", textContent: "Voir",
         getAttribute: name => name === "href" ? "/annonce/456" : "" }] : [];
  }
};
const fromHtml = extract("www.seloger.com", "Monnaie", [], [htmlCard]);
check(fromHtml.retrieved === 1, "carte HTML terrain lue");
check(fromHtml.listings[0].source === "SeLoger", "source correcte");
check(fromHtml.listings[0].priceEuros === 128000, "prix carte HTML");
check(fromHtml.listings[0].areaM2 === 602, "surface carte HTML");
const unrelated = extract("www.bienici.com", "Monnaie", [{
  name: "Maison 4 pièces avec jardin", url: "https://www.bienici.com/annonce/vente/maison",
  offers: { price: "230000" }
}]);
check(unrelated.retrieved === 0, "maison sans terrain ignorée");
const houseWithGarden = extract("www.leboncoin.fr", "Monnaie", [{
  name: "Maison 6 pièces, 120 m² avec jardin", description: "Terrain : 655 m² à Monnaie",
  url: "https://www.leboncoin.fr/ad/ventes_immobilieres/44444",
  offers: { price: "299000" }
}]);
check(houseWithGarden.retrieved === 0,
  "Maison avec surface de terrain dans la description non importée comme terrain");
const standaloneLand = extract("www.leboncoin.fr", "Monnaie", [{
  name: "Terrain constructible 655 m²",
  description: "À Monnaie (37380), terrain à bâtir",
  url: "https://www.leboncoin.fr/ad/ventes_immobilieres/55555",
  offers: { price: "89900" }
}]);
check(standaloneLand.retrieved === 1, "Vraie annonce de terrain conservée");
const lbcCard = {
  innerText: "Immobilier Neuf • Terrain · 406m² • 78 300 € • Monnaie 37380 • Villadim",
  textContent: "Immobilier Neuf • Terrain · 406m² • 78 300 € • Monnaie 37380 • Villadim",
  getClientRects: () => [1],
  matches: sel => sel === "a[href]",
  href: "https://www.leboncoin.fr/ad/ventes_immobilieres/55555",
  getAttribute: key => key === "href" ? "/ad/ventes_immobilieres/55555" : "",
  querySelector: () => null,
  querySelectorAll: () => []
};
const lbc = extract("www.leboncoin.fr", "Monnaie", [], [lbcCard]);
check(lbc.retrieved === 1, "Carte Le Bon Coin avec lien racine détectée");
check(lbc.listings[0].areaM2 === 406, "Surface Le Bon Coin");
check(lbc.listings[0].priceEuros === 78300, "Prix Le Bon Coin");
check(lbc.declaredCount === 23, "Nombre d'annonces visible détecté");


const empty = extract("www.leboncoin.fr", "Monnaie");
check(empty.retrieved === 0, "page vide lisible, distincte du stock réel");
console.log("TerrainScope: " + checks + " vérifications de lecture des fiches réussies.");
