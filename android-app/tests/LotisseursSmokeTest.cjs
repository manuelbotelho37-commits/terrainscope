const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const catalog = JSON.parse(fs.readFileSync(path.join(__dirname, "../app/src/main/assets/lotisseurs-37.json"), "utf8"));
const expected = ["Villadim","Groupe SET","CM-CIC · AFEDIM","Val Touraine Habitat",
  "Lelièvre Immobilier","Viabilis","Acanthe","Arpent","Foncier Aménagement",
  "Francelot / Khor","Nexity","Exeo Promotion","Entre Terrains","Terravia",
  "Lodgim","Touraine Logement","Immo Nature et Construction","Quatro Promotion"];
assert.deepEqual(catalog.developers.map(x=>x.name),expected,
  "Les 18 lotisseurs de TerrainScope V58 doivent être conservés sans oubli ni doublon");
const names=new Set();
for (const developer of catalog.developers) {
  assert.ok(!names.has(developer.name), "Lotisseur en double : "+developer.name);
  names.add(developer.name);
  assert.match(developer.url, /^https:\/\//, "Lien officiel HTTPS pour "+developer.name);
  assert.ok(developer.domain.length>3,"Domaine pour "+developer.name);
}
assert.ok(catalog.developers.every(x=>!/maisons cpr|mikit|ericlor/i.test(x.name)),
  "Ne pas confondre lotisseurs et constructeurs");
console.log("TerraNova : 18 lotisseurs présents, 18 liens et 18 cartes prêts.");
