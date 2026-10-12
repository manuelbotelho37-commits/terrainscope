package fr.terrainscope.app;

import java.util.Arrays;
import java.util.List;

/**
 * Test sans Android SDK :
 * javac -d build app/src/main/java/fr/terrainscope/app/ListingEngine.java tests/ListingEngineSmokeTest.java
 * java -cp build fr.terrainscope.app.ListingEngineSmokeTest
 */
public final class ListingEngineSmokeTest {
    private static int assertions;
    private static ListingEngine.Listing listing(String site, String title, String seller,
                                                 String city, long price, double area,
                                                 String lot, String url) {
        return new ListingEngine.Listing(site, url, title, city, seller, "", "", lot, price, area, true);
    }
    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
    private static ListingEngine.State status(List<ListingEngine.Result> items, String source) {
        for (ListingEngine.Result result : items) {
            if (result.listing.source.equals(source)) return result.state;
        }
        throw new AssertionError("Source manquante: " + source);
    }
    public static void main(String[] args) {
        ListingEngine.Listing lbc = listing("Le Bon Coin","Terrain constructible",
            "Agence Touraine","Monnaie",85000,500,"","https://www.leboncoin.fr/ad/vents/123");
        ListingEngine.Listing seloger = listing("SeLoger","Terrain à bâtir",
            "Agence Touraine","Monnaie",85000,500,"","https://www.seloger.com/annonce/456");
        ListingEngine.Listing bienici = listing("Bien’ici","Parcelle à bâtir",
            "Agence Touraine","Monnaie",85000,500,"","https://www.bienici.com/annonce/vente/789");

        List<ListingEngine.Result> duplicates = ListingEngine.classify(Arrays.asList(bienici, seloger, lbc));
        check(status(duplicates,"Le Bon Coin")==ListingEngine.State.ACCEPTED,"Priorité Le Bon Coin");
        check(status(duplicates,"SeLoger")==ListingEngine.State.DUPLICATE,"SeLoger est un doublon");
        check(status(duplicates,"Bien’ici")==ListingEngine.State.DUPLICATE,"Bien’ici est un doublon");
        check(ListingEngine.filtered(duplicates).size()==1,"Une seule fiche triée");
        check(ListingEngine.filtered(duplicates).get(0).listing.source.equals("Le Bon Coin"),"Lien Le Bon Coin conservé");

        List<ListingEngine.Result> secondary = ListingEngine.classify(Arrays.asList(bienici, seloger));
        check(status(secondary,"SeLoger")==ListingEngine.State.ACCEPTED,"SeLoger prioritaire sans Le Bon Coin");
        check(status(secondary,"Bien’ici")==ListingEngine.State.DUPLICATE,"Bien’ici secondaire");
        check(ListingEngine.priority("Notaires")>ListingEngine.priority("Bien’ici"),"Ordre notaires");

        ListingEngine.Listing anotherPrice = listing("Bien’ici","Terrain","Agence",
            "Monnaie",86000,500,"","https://www.bienici.com/annonce/vente/other");
        check(ListingEngine.filtered(ListingEngine.classify(Arrays.asList(lbc,anotherPrice))).size()==2,
              "Prix différent : deux terrains");

        ListingEngine.Listing anotherCity = listing("Bien’ici","Terrain","Agence",
            "Tours",85000,500,"","https://www.bienici.com/annonce/vente/tours");
        check(ListingEngine.filtered(ListingEngine.classify(Arrays.asList(lbc,anotherCity))).size()==2,
              "Commune différente : deux terrains");

        ListingEngine.Listing lot1 = listing("SeLoger","Terrain libre de constructeur",
            "Nexity","Monnaie",92000,400,"A12","https://www.seloger.com/annonce/lot1");
        ListingEngine.Listing lot2 = listing("Bien’ici","Terrain libre de constructeur",
            "Nexity","Monnaie",92000,400,"A13","https://www.bienici.com/annonce/lot2");
        check(ListingEngine.filtered(ListingEngine.classify(Arrays.asList(lot1,lot2))).size()==2,
              "Deux lots différents ne doivent pas être fusionnés");
        check(!ListingEngine.isBuilder(lot1),"Lotisseur libre de constructeur conservé");
        check(ListingEngine.isLotisseur(lot1),"Nexity reconnu comme lotisseur");

        ListingEngine.Listing competitor = listing("SeLoger","Maison + terrain",
            "Maisons de Stéphanie","Monnaie",230000,450,"","https://www.seloger.com/annonce/builder");
        check(ListingEngine.isBuilder(competitor),"Exclusion Maison de Stéphanie");
        ListingEngine.Listing cpr = listing("Bien’ici","Terrain constructible",
            "Maisons CPR","Monnaie",90000,530,"","https://www.bienici.com/annonce/vente/cpr");
        check(!ListingEngine.isBuilder(cpr),"CPR conservé");

        ListingEngine.Listing samePortal1 = listing("Bien’ici","Terrain 1",
            "Agence","Monnaie",90000,530,"","https://www.bienici.com/annonce/vente/one");
        ListingEngine.Listing samePortal2 = listing("Bien’ici","Terrain 2",
            "Agence","Monnaie",90000,530,"","https://www.bienici.com/annonce/vente/two");
        check(ListingEngine.filtered(ListingEngine.classify(Arrays.asList(samePortal1,samePortal2))).size()==2,
              "Deux fiches distinctes sur le même portail doivent rester visibles");
        check(ListingEngine.normalize("Notre-Dame-d’Oé").equals("notre dame d oe"),
              "Accents et apostrophes normalisés");
        check(ListingEngine.counts(duplicates).get(ListingEngine.State.DUPLICATE)==2,
              "Compteur doublons");
        System.out.println("TerrainScope : " + assertions + " vérifications du moteur réussies.");
    }
}
