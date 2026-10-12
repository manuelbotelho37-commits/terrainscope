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
    document, window: { __terrainScopeRequestedCity: city,
      __terrainScopeRequestedPostal: city === "Monnaie" ? "37380" : city === "Tours" ? "37000" : "" },
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


// Données inspirées de fiches visibles lors du contrôle des deux portails
// à Monnaie : vendeur d'agence, lotisseur, constructeur et commune voisine.
const agency = extract("www.seloger.com", "Monnaie", [{
  itemListElement: [{item: {
    name: "Terrain constructible 838 m² à Monnaie", description: "Terrain plat divisible",
    url: "https://www.seloger.com/annonce/838-bsk",
    address: {addressLocality:"Monnaie", postalCode:"37380"},
    offers:{price:"145000",seller:{name:"SOPHIE VIALE BSK IMMOBILIER"}}
  }}]
}]);
check(agency.retrieved===1 && agency.listings[0].seller.includes("BSK IMMOBILIER") &&
    agency.listings[0].cityConfirmed, "Annonce d'agence BSK Monnaie conservée");

const nearby = extract("www.seloger.com", "Monnaie", [{
  itemListElement: [{item: {
    name: "Terrain constructible à vendre à Monnaie de 1 349 m²",
    description: "À seulement 5 minutes de Monnaie, opportunité à Crotelles",
    url: "https://www.seloger.com/annonce/crotelles",
    address:{addressLocality:"Crotelles",postalCode:"37380"},
    offers:{price:"70000",seller:{name:"JEAN CHARLES VIDONI BSK IMMOBILIER"}}
  }}]
}]);
check(nearby.retrieved===1 && !nearby.listings[0].cityConfirmed,
    "Annonce sur Crotelles refusée malgré Monnaie dans le titre");

const sameUrl = "https://www.seloger.com/annonce/terrain-310";
const htmlSupplement = {
  innerText:"Terrain viabilisé à Monnaie (37380) - 310 m² - 65 900 € Villadim Aménagement",
  textContent:"Terrain viabilisé à Monnaie (37380) - 310 m² - 65 900 € Villadim Aménagement",
  getClientRects:()=>[1],
  matches:()=>false,
  querySelector(selector) {
    if (selector.includes("h2")) return {textContent:"Terrain viabilisé à Monnaie"};
    if (selector.includes("price")) return {textContent:"65 900 €"};
    if (selector.includes("surface")) return {textContent:"310 m²"};
    if (selector.includes("agency")) return {textContent:"Villadim Aménagement"};
    return null;
  },
  querySelectorAll(selector) {
    return selector==="a[href]"?[{
      href:sameUrl,textContent:"Voir cette parcelle",
      getAttribute:key=>key==="href"?"/annonce/terrain-310":""
    }]:[];
  }
};
const merged = extract("www.seloger.com", "Monnaie",
  [{itemListElement:[{item:{
    name:"Terrain viabilisé à Monnaie",url:sameUrl,
    description:"Terrain disponible sur Monnaie",offers:{}
  }}]}], [htmlSupplement]);
check(merged.retrieved===1, "Une seule fiche par URL même si JSON-LD et HTML");
check(merged.listings[0].seller==="Villadim Aménagement",
  "Le vendeur présent dans la carte HTML enrichit le JSON-LD");
check(merged.listings[0].priceEuros===65900 && merged.listings[0].areaM2===310,
  "Prix et surface complétés depuis la carte");

const conflicted = extract("www.seloger.com", "Monnaie",
  [{itemListElement:[{item:{
    name:"Terrain à Monnaie",url:sameUrl,
    address:{addressLocality:"Crotelles",postalCode:"37380"},
    offers:{price:"65900"}
  }}]}], [htmlSupplement]);
check(!conflicted.listings[0].cityConfirmed,
  "Un HTML trompeur ne doit pas écraser la commune structurée Crotelles");

const onlyMarketing = extract("www.seloger.com", "Monnaie", [{
  itemListElement:[{item:{
    name:"Terrain constructible 1 349 m²",
    description:"À seulement cinq minutes de Monnaie, idéal pour bâtir à Crotelles",
    url:"https://www.seloger.com/annonce/lointain",
    offers:{price:"70000"}
  }}]
}]);
check(!onlyMarketing.listings[0].cityConfirmed,
  "Une commune citée uniquement dans une publicité n'est pas confirmée");

const empty = extract("www.leboncoin.fr", "Monnaie");
check(empty.retrieved === 0, "page vide lisible, distincte du stock réel");
console.log("TerrainScope: " + checks + " vérifications de lecture des fiches réussies.");
