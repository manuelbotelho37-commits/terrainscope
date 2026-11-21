# TerraNova Android — prototype indépendant (ex-TerrainScope)

**Identité visuelle TerraNova luxe** : bleu nuit (#0B1620), or (#D4AF37),
vert nature (#1E7F53), gris ardoise (#2B3642).
L'application Android affiche **TerraNova** et utilise une icône vectorielle
fondée sur l'emblème approuvé. L'icône luxe approuvée est désormais intégrée à l'APK Android en WebP
et utilisée pour le lanceur et l'en-tête. Son rendu haute définition reste
à contrôler sur le Samsung.
Les captures de maquette présentant des prix et quantités sont uniquement
illustratives ; l'application n'enregistre ni n'affiche de faux résultats.

**Statut : expérimental, non validé sur le Samsung.** Le site TerrainScope de production
(GitHub Pages, branche main) reste inchangé. En particulier, la rubrique
**Lotisseurs · programmes et terrains** reste disponible avec ses 18 organismes.

## Objectif fonctionnel (TerraNova 0.4.0)

1. Saisir une ou plusieurs communes du 37, séparées par des virgules.
2. Appuyer **une seule fois** sur **Rechercher les terrains**.
3. TerraNova ouvre successivement, en arrière-plan, les recherches de
   Le Bon Coin, SeLoger, Bien'ici, iad et notaires **lorsqu'une URL locale
   vérifiée existe**, puis lit les annonces HTML/JSON-LD accessibles.
4. Les liens « page suivante » disponibles peuvent être suivis automatiquement
   (maximum 4 pages par couple commune/site). Le chargement d'une page
   est limité à 18 secondes pour ne pas bloquer la recherche entière.
5. **Annonces triées** : terrains issus des pages récupérées, avec les
   constructeurs exclus, doublons prouvés regroupés et incertitudes signalées.
6. **Toutes les annonces** : l'ensemble des fiches effectivement lues,
   y compris les exclusions et leur motif.
7. **18 lotisseurs** : catalogue embarqué directement dans TerraNova sous
   forme de tuiles bleu nuit et or ; accès vers leurs sites officiels.

**Limite majeure :** ce système n'a pas d'accès API universel aux portails.
Le moteur ne garantit pas de lire toutes les annonces, ni celles chargées
derrière une authentification, un CAPTCHA ou un parcours de plus de quatre
pages. Ce n'est pas un moyen de contourner leurs restrictions. Un portail
indisponible n'est **jamais** comptabilisé comme « zéro terrain ».

## Règle de doublons

Pour une même commune, une même surface et un même prix, une annonce publiée
sur plusieurs portails ne doit être affichée qu'une fois parmi les annonces
triées. Priorité **Le Bon Coin > SeLoger > Bien'ici > Notaires > iad > autres**.
Le lien conservé correspond à la source prioritaire. Les sources secondaires
sont indiquées sur la fiche. Tous les exemplaires restent inspectables dans
« Toutes les annonces ».

Précautions : les annonces ayant des **numéros de lot ou des adresses
explicitement distincts ne sont pas fusionnées** ; deux annonces différentes
sur un même portail ne sont pas fusionnées sur le seul prix et la surface.
Les annonces sans prix ou surface peuvent être importées mais ne sont pas
fusionnées automatiquement sur ces champs inconnus.

## Exclusions

18 marques de constructeurs ciblées (avec variantes d'orthographe), et les
offres explicites maison + terrain. **Maisons CPR reste conservé.**
Ne pas supprimer un particulier, une agence immobilière, iad ou un lotisseur
parce que son annonce porte la mention « pro » ou « libre de constructeur ».
Une annonce dont la commune ou l'annonceur est absent des données récupérées
reste visible avec une indication « à vérifier », et non supprimée à tort.

## Contraintes importantes

- Aucun service externe de scraping, crédit, abonnement ou clé payante.
- La collecte est désormais lancée automatiquement par une seule recherche,
  mais elle ne lit que les annonces accessibles dans chaque page WebView ; Les sites peuvent masquer les données,
  bloquer la WebView, imposer un CAPTCHA, une connexion ou modifier leur HTML.
- Les URL de recherche par commune sont générées lorsque leurs structures
  sont connues. Pour SeLoger hors des communes disposant d'un identifiant exact
  ou pour les notaires, il faut sélectionner la commune et « Terrain » sur le site.
- Les résultats importés ne sont pas des disponibilités certifiées.
- Pas d'enregistrement d'historique entre recherches ; « Nouvelle recherche »
  vide la liste en mémoire. Aucun identifiant de connexion n'est lu ou transmis.
- L'utilisateur consulte les pages directement et déclenche l'import lui-même.
  Toute récupération doit respecter les conditions d'utilisation des portails.

## Compilation et tests

Ouvrir le dossier android-app dans Android Studio avec JDK 17 et SDK 35.
La configuration est Gradle 8.10.2 / Android Gradle Plugin 8.7.3.

Tests du moteur indépendants d'Android :

    mkdir -p build/smoke
    javac -encoding UTF-8 -d build/smoke \
      app/src/main/java/fr/terrainscope/app/ListingEngine.java \
      tests/ListingEngineSmokeTest.java
    java -cp build/smoke fr.terrainscope.app.ListingEngineSmokeTest

Depuis la racine du dépôt :

    node android-app/tests/ExtractorSmokeTest.cjs

Sur le dépôt GitHub public, le workflow de la branche expérimente la
compilation d'un APK de test à l'aide de runners publics standards (gratuits).
**Ne pas publier ni présenter l'APK comme validé avant réussite des tests
sur un téléphone réel.**

## Scénarios de validation avant diffusion

- Monnaie, Notre-Dame-d'Oé, Tours, Azay-le-Rideau et communes non préconfigurées.
- Même parcelle sur les trois portails : conserver Le Bon Coin.
- Sans Le Bon Coin : conserver SeLoger plutôt que Bien'ici.
- Même surface et prix, mais lots A12 / A13 : garder les deux.
- Annonces de particuliers, agences, iad, notaires et lotisseurs visibles.
- Maisons CPR conservé ; concurrents listés écartés ; cas ambigus vérifiables.
- Indiquer une extraction incomplète plutôt qu'un « zéro terrain » trompeur.
- La fermeture/recherche suivante efface les annonces précédentes.
- Le module des 18 lotisseurs et la PWA actuelle continuent de fonctionner.

**Jalon actuel : squelette Android + extraction locale déclenchée à la demande +
moteur de classification/doublons. La récupération multi-sites exhaustive
reste non démontrée et nécessite des essais site par site.**
