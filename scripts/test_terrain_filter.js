/* Tests anti-faux terrains — exécution Node.js, sans accès aux sites tiers. */
'use strict';
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');

const source = fs.readFileSync(path.join(__dirname, '..', 'aggregator.js'), 'utf8');
const html = fs.readFileSync(path.join(__dirname, '..', 'index.html'), 'utf8');
const stubs = {
  uniqueResults: {replaceChildren(){},append(){}},
  uniqueSummary: {textContent:''},
  uniquePages: {hidden:false},
};
const document = {
  getElementById(id) {return stubs[id] || null;},
  createElement(tag) {return {tag, textContent:'', className:''};},
};
const window = {};
vm.runInNewContext(source, {window,document,URL,Intl,Set,Map,console,clearTimeout,setTimeout});
const accept = window.TerrainAggregator.isIndividualLandListing;
const previewSaysInactive = window.TerrainAggregator.previewSaysInactive;
assert.equal(typeof accept, 'function');
assert.equal(typeof previewSaysInactive, 'function');
const inactiveExamples = [
  ['Cette annonce est désactivée !', '', true],
  ['Terrain 420 m² à Nouzilly', 'Cette annonce est désactivée !', true],
  ['Terrain 320 m² à Monnaie', 'Annonce supprimée par son auteur', true],
  ['Terrain 550 m² à Veigné', 'Le bien a déjà été vendu', true],
  ['Terrain 350 m² à Tours', 'Description terrain viabilisé disponible', false],
  ['Terrain 600 m² à Monnaie', 'Informations à confirmer chez le vendeur', false],
];
for (const [title, description, expected] of inactiveExamples) {
  assert.equal(previewSaysInactive(title,description), expected,
    'Statut explicite : '+title+' / '+description);
}
const scenarios = [
  ['Annonce terrain Le Bon Coin', 'Terrain 404 m² Nouzilly - Ventes immobilières - Leboncoin', 'https://www.leboncoin.fr/ad/ventes_immobilieres/123456', true],
  ['Autre terrain Le Bon Coin', 'Terrain 544 m² Nouzilly - Ventes immobilières - Leboncoin', 'https://www.leboncoin.fr/ad/ventes_immobilieres/18881', true],
  ['Le Bon Coin page de tous les biens', 'Terrain 404 m² Nouzilly - Ventes immobilières - Leboncoin', 'https://www.leboncoin.fr/l/cp_nouzilly_37380', false],
  ['Le Bon Coin résultats maison', 'Terrain 404 m² Nouzilly - Ventes immobilières - Leboncoin', 'https://www.leboncoin.fr/cl/ventes_immobilieres/cp_nouzilly_37380/real_estate_type%3A1', false],
  ['Le Bon Coin résultats terrains', 'Terrain 404 m² Nouzilly - Ventes immobilières - Leboncoin', 'https://www.leboncoin.fr/cl/ventes_immobilieres/cp_nouzilly_37380/real_estate_type%3A3', false],
  ['Conseiller IAD Vouvray', 'Joël Ligeron - Conseiller immobilier à Vouvray (37210) - IAD', 'https://www.iadfrance.fr/conseiller-immobilier/joel-ligeron', false],
  ['Conseillère IAD Mettray', 'Lucie Legaré - Conseillère immobilier à Mettray (37390) - iad', 'https://www.iadfrance.fr/agents/lucie-legare', false],
  ['Catalogue IAD 1195 biens', '1 195 biens immobiliers à vendre : Indre-et-Loire (37) - IAD', 'https://www.iadfrance.fr/annonces/vente', false],
  ['Maison iad avec jardin', 'Maison à vendre 5 pièces Nouzilly 160 m² de terrain', 'https://www.iadfrance.fr/annonce/maison-vente-5-pieces-nouzilly-160m2/r2102612', false],
  ['Terrain IAD direct', 'Terrain à vendre 540 m² Nouzilly', 'https://www.iadfrance.fr/annonce/terrain-vente-nouzilly-540m2/r2036223', true],
  ['IAD page de recherche', 'Terrain à vendre Nouzilly', 'https://www.iadfrance.fr/annonces/nouzilly-37380/vente/terrain', false],
  ['Maison avec terrain LeBonCoin', 'Maison avec jardin sur terrain 900 m² Nouzilly', 'https://www.leboncoin.fr/ad/ventes_immobilieres/140001', false],
  ['Terrain SeLoger individuel', 'Terrain constructible 700 m² à Nouzilly', 'https://www.seloger.com/annonces/achat/terrain/nouzilly-37/2001.htm', true],
  ['Annuaire agents', 'Agent immobilier à Nouzilly, terrains à vendre', 'https://www.exemple.fr/agents/agent-de-nouzilly', false],
  ['Page 23 terrains', '23 terrains à vendre dans l’Indre-et-Loire', 'https://www.exemple.fr/terrains-a-vendre', false],
];
for (const [name,title,url,expected] of scenarios) {
  const actual = accept(title,url);
  assert.equal(actual,expected,name+': résultat incorrect');
}
assert.match(source, /kind!=='terrains'/, 'Seules les fiches classées terrain passent');
assert.match(source, /node\.dataset\.locationStatus!=='match'/, 'La commune doit être retrouvée dans le titre');
assert.doesNotMatch(source, /return cities\.length===1\?cities\[0\]/, 'Interdire attribution automatique de commune');
assert.match(html, /!isLandCard\|\|place\.status!==\x27match\x27/, 'Les pages Google non confirmées restent masquées');
assert.match(source, /previewSaysInactive\(title,description\)/, 'Les annonces explicitement désactivées sont exclues');
assert.doesNotMatch(source, /\{name:'Le Bon Coin',domain:'leboncoin.fr'\}/,
  'Aucun passage de collecte de fiches Google Le Bon Coin potentiellement périmées');
assert.match(source, /if\(host==='leboncoin.fr'\|\|host.endsWith\('\.leboncoin.fr'\)\)return null;/,
  'Exclure même les annonces LBC découvertes via les autres recherches Google');
assert.ok(html.indexOf('<section id="quickSources"') < html.indexOf('<details id="googleLeads"'),
  'Les recherches directes officielles doivent apparaître avant les pistes Google');
assert.match(html, /el\('googleLeads'\)\.open=false;/,
  'Les liens Google non vérifiés doivent être repliés à chaque recherche');
assert.match(html, /TerrainScope V38/,
  'Version cohérente V38');
assert.match(html, /id="selectedCities"[^>]*><\/div><button id="lotisseursTop"[^>]*type="button">/,
  'Lotisseurs doit être juste sous les communes sélectionnées');
assert.match(html, /id="lotisseursTop"[^>]*>[^<]*<\/button><button id="citySearchBtn"[^>]*type="submit">/,
  'Voir les terrains doit rester directement à côté dans les commandes des communes');
assert.equal((html.match(/id="lotisseursTop"/g)||[]).length,1,
  'Un seul bouton Lotisseurs');
assert.equal((html.match(/type="submit"/g)||[]).length,1,
  'Un seul bouton de recherche pour éviter les doublons en bas du formulaire');
assert.ok(html.indexOf('id="addCity"') < html.indexOf('id="lotisseursTop"') &&
  html.indexOf('id="lotisseursTop"') < html.indexOf('id="citySearchBtn"'),
  'Les boutons doivent être regroupés directement sous Ajouter cette commune');
assert.match(html, /el\('lotisseursTop'\)\.addEventListener\('click'/,
  'Le bouton Lotisseurs conserve son fonctionnement');
assert.match(source, /Annonces NON vérifiées sur les sites vendeurs/, 'Le statut doit rester incertain');

// Tester les URL TERRAIN de IAD construites depuis les codes postaux officiels.
// Un lien /annonces/<ville>/vente ou une recherche Google générale renvoie des maisons.
const communeData = JSON.parse(fs.readFileSync(path.join(__dirname,'..','communes-37.json'),'utf8'));
assert.equal(communeData.length,272,'Répertoire officiel du 37 complet');
const cityKey=value=>String(value||'').trim().normalize('NFD').replace(/[\u0300-\u036f]/g,'')
  .toLowerCase().replace(/[’'\-–—]/g,' ').replace(/[^a-z0-9]+/g,' ').replace(/\s+/g,' ').trim();
const normalized=value=>String(value||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
const postalCodes=new Map();
for(const city of communeData){
  assert.ok(Array.isArray(city.codesPostaux)&&city.codesPostaux.length,
    'Code postal manquant : '+city.nom);
  postalCodes.set(cityKey(city.nom),city.codesPostaux[0]);
}
const begin=html.indexOf('    const iadTerrainUrl=city=>{');
const end=html.indexOf('    /* Catalogue à preuve',begin);
assert.ok(begin>=0&&end>begin,'Fonction de lien IAD manquante');
const makeIadLink=vm.runInNewContext(
  html.slice(begin,end).replace('const iadTerrainUrl=','globalThis._iadTerrainUrl=')+
  'globalThis._iadTerrainUrl', {CITY_POSTAL_CODES:postalCodes,citySearchKey:cityKey,normalize:normalized,globalThis:{}}
); 
for(const [city,expected] of [
  ['Nouzilly','https://www.iadfrance.fr/annonces/nouzilly-37380/vente/terrain'],
  ['Monnaie','https://www.iadfrance.fr/annonces/monnaie-37380/vente/terrain'],
  ['Joué-lès-Tours','https://www.iadfrance.fr/annonces/joue-les-tours-37300/vente/terrain'],
  ['Vouvray','https://www.iadfrance.fr/annonces/vouvray-37210/vente/terrain'],
]){
  assert.equal(makeIadLink(city),expected,'Catégorie terrain et commune : '+city);
}
assert.match(html,/makeLink\('4 · IAD · terrains · '\+city,iadTerrainUrl\(city\)/,
  'Raccourci IAD devrait ouvrir une recherche terrain par commune');
assert.doesNotMatch(html,/linkFor\(\[city\],'iadfrance.fr'\)/,
  'Aucune recherche IAD générique susceptible de remonter des maisons');
console.log('TerrainScope : vérifications IAD strictes pour 4 communes, 272 codes postaux et filtrage terrain OK.');

console.log('TerrainScope : '+scenarios.length+' contrôles de type + '+inactiveExamples.length+' contrôles de statut et 5 tests de protection contre les liens périmés VALIDÉS.');
