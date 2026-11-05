/* Tests autonomes de lecture des pages officielles AFEDIM, sans réseau. */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const js = fs.readFileSync(path.join(__dirname,
    "../app/src/main/assets/extract-afedim.js"), "utf8");

function extract(city, slug, text, title = city) {
    const context = {
        window: { __terrainScopeRequestedCity: city },
        document: { title, body: { innerText: text } },
        location: {
            hostname: "www.afedim.fr",
            pathname: "/fr/achat-immobilier/programme/terrain/" + slug + "-37/000000/Fiche",
            href: "https://www.afedim.fr/fr/achat-immobilier/programme/terrain/" + slug + "-37/000000/Fiche"
        }
    };
    return JSON.parse(vm.runInNewContext(js, context));
}

const azay = `AZAY SUR CHER, La Closerie de la Trute.
Les terrains disponibles
Terrain 18 Optionné Image (voir l'information complémentaire) 522 m² 84 000,00 EUR Télécharger le plan du lot
Terrain 13 Optionné Image (voir l'information complémentaire) 522 m² 88 000,00 EUR
Terrain 20 550 m² 91 000,00 EUR
Terrain 21 Optionné Image 550 m² 91 000,00 EUR
Terrain 15 550 m² 94 000,00 EUR
Etat des risques`;
const result = extract("Azay-sur-Cher", "azay-sur-cher", azay);
assert.equal(result.accessBlocked, false);
assert.equal(result.retrieved, 5);
assert.equal(result.listings.filter(x=>x.description.startsWith("Optionné")).length, 3);
assert.equal(result.listings.find(x=>x.lot === "18").priceEuros, 84000);
assert.equal(result.listings.find(x=>x.lot === "20").areaM2, 550);
assert.ok(result.listings.every(x=>x.cityConfirmed && x.city === "Azay-sur-Cher"));

const artannes = extract("Artannes-sur-Indre", "artannes-sur-indre",
    "ARTANNES SUR INDRE Les terrains disponibles Terrain 320 460 m² 74 500,00 EUR Etat des risques");
assert.equal(artannes.retrieved, 1);
assert.equal(artannes.listings[0].lot, "320");
assert.ok(artannes.listings[0].title.includes("Artannes-sur-Indre"));

const chanceaux = extract("Chanceaux-sur-Choisille", "chanceaux-sur-choisille",
    "CHANCEAUX SUR CHOISILLE Les terrains disponibles Terrain 12 BIS 360 m² 79 000,00 EUR Etat des risques");
assert.equal(chanceaux.retrieved, 1);
assert.equal(chanceaux.listings[0].lot, "12 BIS");

const croix = extract("La Croix-en-Touraine", "la-croix-en-touraine",
    "LA CROIX EN TOURAINE Les terrains disponibles Terrain 04 464 m² 56 000,00 EUR Etat des risques");
assert.equal(croix.retrieved, 1);
assert.equal(croix.listings[0].priceEuros, 56000);

const wrongCity = extract("Azay-le-Rideau", "artannes-sur-indre", artannes.listings[0].title);
assert.equal(wrongCity.retrieved, 0);
assert.equal(wrongCity.accessBlocked, true);
const unavailable = extract("Azay-sur-Cher", "azay-sur-cher",
    "Ce programme n'est plus disponible. AZAY SUR CHER");
assert.equal(unavailable.retrieved, 0);
assert.equal(unavailable.accessBlocked, true);
console.log("TerraNova : 4 communes officielles AFEDIM, lot BIS, prix, options et ville exacte vérifiés.");
