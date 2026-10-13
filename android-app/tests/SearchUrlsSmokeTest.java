package fr.terrainscope.app;

import java.util.HashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public final class SearchUrlsSmokeTest {
    private static int count = 0;
    private static void verify(boolean yes, String message) {
        count++;
        if (!yes) throw new AssertionError(message);
    }
    private static String post(String... parts) { return String.join("|", parts); }

    public static void main(String[] args) throws Exception {
        Map<String, String> postals = new HashMap<>();
        postals.put("monnaie", "37380");
        postals.put("notre dame d oe", "37390");
        postals.put("tours", "37000");
        postals.put("azay le rideau", "37190");
        postals.put("chinon","37500");
        postals.put("monts","37260");
        postals.put("veigne","37250");
        postals.put("montlouis sur loire","37270");
        Map<String, String> seloger = new HashMap<>();
        seloger.put("monnaie", "monnaie-37380/ad08fr14679");
        SearchUrls search = new SearchUrls(postals, seloger);
        String lbcMonnaie = search.urlFor("Le Bon Coin", "Monnaie");
        verify(lbcMonnaie.equals("https://www.leboncoin.fr/cl/ventes_immobilieres/cp_monnaie_37380/real_estate_type%3A3"),
            "URL Terrain Le Bon Coin Monnaie : catégorie 3 et code postal");
        verify(!lbcMonnaie.contains("/recherche?") && !lbcMonnaie.contains("category=9"),
            "Pas de recherche générale dans toute la France");
        verify(search.urlFor("Le Bon Coin", "Notre-Dame-d’Oé").contains("cp_notre-dame-d-oe_37390/"),
            "Communes avec apostrophe et accents");
        verify(search.urlFor("Le Bon Coin", "Tours").contains("cp_tours_37000/"),
            "Tours filtré");
        verify(search.urlFor("Le Bon Coin", "Azay-le-Rideau").contains("cp_azay-le-rideau_37190/"),
            "Azay-le-Rideau filtré");
        verify(search.urlFor("SeLoger", "Monnaie").endsWith("/monnaie-37380/ad08fr14679"),
            "SeLoger commune contrôlée");
        verify(search.urlFor("SeLoger", "Tours") == null, "SeLoger sans identifiant : aucun lien national");
        verify(search.urlFor("Bien’ici", "Monnaie").equals(
            "https://www.bienici.com/recherche/achat/monnaie-37380/terrain"),
            "Bien’ici terrain par commune");
        verify(search.urlFor("IAD", "Monnaie").equals(
            "https://www.iadfrance.fr/annonces/monnaie-37380/vente/terrain"),
            "IAD terrain par commune");
        verify(search.urlFor("Notaires", "Monnaie") == null,
            "Notaires Monnaie non confirmé : jamais la page des 35 000 annonces");
        verify(search.urlFor("Notaires", "Chinon").endsWith("/vente/terrain/chinon-37"),
            "Chinon : vrais terrains notarials vérifiés");
        verify(search.urlFor("Notaires", "Monts").endsWith("/vente/terrain/monts-37"),
            "Monts : vrais terrains notarials vérifiés");
        verify(search.urlFor("Notaires", "Veigné").endsWith("/vente/terrain/veigne-37"),
            "Veigné : lien exact terrain notaire");
        verify(search.urlFor("Notaires", "Montlouis-sur-Loire")
            .endsWith("/vente/terrain/montlouis-sur-loire-37"),
            "Montlouis : type terrain et localité correcte");
        verify(search.urlForDeveloper("CM-CIC · AFEDIM", "Azay-sur-Cher")
            .endsWith("/azay-sur-cher-37/015753/Fiche"),
            "Programme AFEDIM Azay-sur-Cher exact");
        verify(search.urlForDeveloper("CM-CIC · AFEDIM", "Artannes-sur-Indre")
            .endsWith("/artannes-sur-indre-37/020086/Fiche"),
            "Programme AFEDIM Artannes-sur-Indre exact");
        verify(search.urlForDeveloper("CM-CIC · AFEDIM", "Chanceaux-sur-Choisille")
            .endsWith("/chanceaux-sur-choisille-37/017180/Fiche"),
            "Programme AFEDIM Chanceaux-sur-Choisille exact");
        verify(search.urlForDeveloper("CM-CIC · AFEDIM", "La Croix-en-Touraine")
            .endsWith("/la-croix-en-touraine-37/018133/Fiche"),
            "Programme AFEDIM La Croix-en-Touraine exact");
        verify(search.urlForDeveloper("CM-CIC · AFEDIM", "Azay-le-Rideau") == null,
            "Ne pas confondre Azay-le-Rideau avec Artannes ou Azay-sur-Cher");
        verify(search.urlForDeveloper("Villadim", "Azay-sur-Cher") == null,
            "Pas de programme sans lien exact vérifié");
        verify(search.urlFor("Le Bon Coin", "Commune inventée") == null,
            "Commune absente : ne pas ouvrir une recherche nationale");
        verify(search.urlFor("Le Bon Coin", "") == null, "Commune vide non acceptée");
        // Un second fichier d'essai généré par Node couvre les 272 communes
        // réelles du référentiel 37, pas seulement les quelques exemples.
        if (args.length > 0) {
            for (String row : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
                String[] data = row.split("\\t", -1);
                if (data.length != 2) throw new AssertionError("Ligne de commune incorrecte");
                String city = data[0], postal = data[1];
                Map<String, String> local = new HashMap<>();
                local.put(ListingEngine.normalize(city), postal);
                SearchUrls source = new SearchUrls(local, new HashMap<>());
                String expectedPath = "cp_" + ListingEngine.normalize(city).replace(' ', '-') +
                    "_" + postal + "/real_estate_type%3A3";
                String actual = source.urlFor("Le Bon Coin", city);
                verify(actual != null && actual.endsWith(expectedPath),
                    "Terrain + code postal exact " + city + " " + postal);
                verify(source.urlFor("Bien’ici", city).endsWith("/terrain"),
                    "Bien’ici type Terrain " + city);
                String notary = source.urlFor("Notaires", city);
                verify(notary == null ||
                    (notary.startsWith("https://www.immobilier.notaires.fr/fr/annonces-immobilieres/vente/terrain/")
                     && notary.endsWith("-37")),
                    "Jamais de recherche notaire nationale " + city);
            }
        }
        System.out.println("TerrainScope: " + count + " vérifications des recherches strictes réussies.");
    }
}
