const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const main = fs.readFileSync(path.join(__dirname,
  "../app/src/main/java/fr/terrainscope/app/MainActivity.java"),"utf8");
const catalog = JSON.parse(fs.readFileSync(path.join(__dirname,
  "../app/src/main/assets/lotisseurs-37.json"),"utf8"));
function contains(term,description) {
  assert.ok(main.includes(term),description+" absent du code Android");
}
contains("startAutomatedSearch()","Lancement automatique avec un seul bouton");
contains('action("⌕  RECHERCHER LES TERRAINS", true)',"Bouton de recherche multi-sites");
contains("collectScanPage(run, taskIndex)","Import intégré au parcours");
contains('readAsset("extract-visible.js")',"Extraction des vraies annonces");
contains("startNextScanTask(run)","Passage au site suivant");
contains("scanPageNumber < 4","Pagination contrôlée");
contains("new int[]{Color.rgb(29, 44, 58), DARK}","Dégradé de l'accueil luxe");
contains('heading("TerraNova", 27, GOLD, true)',"Titre TerraNova doré");
contains('readAsset("lotisseurs-37.json")',"Catalogue de lotisseurs embarqué");
contains('int columns = screen >= 730 ? 3 : screen >= 470 ? 2 : 1;',"Grille adaptative Fold 8");
contains('action("Annonces triées", true)',"Onglet annonces triées");
contains('action("Toutes les annonces", false)',"Onglet toutes les annonces");
contains("accessBlocked","Prise en compte des CAPTCHA");
assert.ok(!main.includes('action("Importer la page"'),
  "L'ancien bouton Importer ne doit plus apparaître.");
assert.equal(catalog.developers.length,18,"Le catalogue doit compter 18 lotisseurs");
// Régression Z Fold 8 : aucune hauteur restante pondérée ni liste en
// ScrollView imbriquée ; tout le contenu doit défiler avec le bandeau.
contains("screenScroller = new ScrollView(this)", "Défilement vertical global");
contains("screenScroller.addView(main", "Panneaux inclus dans le défilement");
contains("setContentView(screenScroller)", "ScrollView racine affiché");
contains("results = new LinearLayout(this)", "Résultats sans défilement imbriqué");
contains("main.addView(results, new LinearLayout.LayoutParams(-1, -2))",
  "Liste complète en hauteur naturelle");
assert.ok(!main.includes("results = new ScrollView(this)"),
  "Interdire le défilement imbriqué qui bloque les lotisseurs");
assert.ok(!main.includes("new LinearLayout.LayoutParams(-1, 0, 1f)"),
  "Interdire les zones de résultats de hauteur nulle sur Fold déplié");
console.log("TerraNova luxe : 22 contrôles UI, dont défilement Fold 8, valides.");
