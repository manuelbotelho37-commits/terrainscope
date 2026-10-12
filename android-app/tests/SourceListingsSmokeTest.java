package fr.terrainscope.app;

import java.util.Arrays;
import java.util.List;

/** Contrôle réel (sans Android) des cinq onglets de consultation. */
public final class SourceListingsSmokeTest {
    private static void check(boolean ok, String explanation) {
        if (!ok) throw new AssertionError(explanation);
    }

    private static ListingEngine.Listing item(String site, String seller, String path) {
        return new ListingEngine.Listing(site, "https://example.org/" + path,
            "Terrain constructible", "Monnaie", seller, "", "",
            "", 71000, 374, true);
    }

    public static void main(String[] args) {
        ListingEngine.Listing lbc = item("Le Bon Coin", "Agence A", "lbc");
        ListingEngine.Listing seLoger = item("SeLoger", "Agence B", "seloger");
        ListingEngine.Listing bieniciCid = item(
            "Bien’ici", "CONSTRUCTIONS IDEALE DEMEURE", "cid");
        ListingEngine.Listing iad = item("IAD", "Conseiller IAD", "iad");
        ListingEngine.Listing notaires = item("Notaires", "Etude notariale", "notaires");
        List<ListingEngine.Result> classified = ListingEngine.classify(
            Arrays.asList(lbc, seLoger, bieniciCid, iad, notaires));

        check(SourceListings.select(classified, "", false).size() == 5,
            "Toutes les annonces doit garder les cinq sources");
        for (String site : Arrays.asList("Le Bon Coin", "SeLoger", "Bien’ici", "IAD", "Notaires")) {
            check(SourceListings.count(classified, site) == 1,
                "Un compte distinct doit exister pour " + site);
            List<ListingEngine.Result> selected = SourceListings.select(classified, site, false);
            check(selected.size() == 1 &&
                ListingEngine.normalize(selected.get(0).listing.source).equals(
                    ListingEngine.normalize(site)),
                "L'onglet " + site + " n'affiche que ses propres annonces");
        }
        check(SourceListings.select(classified, "Bien’ici", false).get(0).state ==
            ListingEngine.State.BUILDER,
            "Bien'ici conserve l'annonce CID dans son onglet");
        check(SourceListings.select(classified, "Bien’ici", true).isEmpty(),
            "CID est exclu des annonces triées");
        check(SourceListings.count(classified, "Site inaccessible") == 0,
            "Source indisponible : zéro import, sans inventer d'annonce");
        check(SourceListings.select(classified, "", true).size() == 4,
            "Les annonces triées ne conservent que les quatre terrains admis");
        ListingEngine.Listing expensive = new ListingEngine.Listing(
            "Le Bon Coin", "https://example.org/expensive",
            "Terrain cher", "Azay-sur-Cher", "Agence Alpha",
            "", "", "A", 180000, 600, true);
        ListingEngine.Listing cheap = new ListingEngine.Listing(
            "SeLoger", "https://example.org/cheap",
            "Terrain abordable", "Azay-sur-Cher", "Agence Beta",
            "", "", "B", 49000, 350, true);
        ListingEngine.Listing mid = new ListingEngine.Listing(
            "Bien’ici", "https://example.org/mid",
            "Terrain moyen", "Azay-sur-Cher", "Agence Gamma",
            "", "", "C", 93000, 450, true);
        ListingEngine.Listing unknown = new ListingEngine.Listing(
            "SeLoger", "https://example.org/unknown",
            "Terrain prix inconnu", "Azay-sur-Cher", "Agence Delta",
            "", "", "D", 0, 400, true);
        List<ListingEngine.Result> prices = ListingEngine.classify(
            Arrays.asList(expensive, unknown, mid, cheap));
        List<ListingEngine.Result> allSorted = SourceListings.select(prices, "", false);
        check(allSorted.get(0).listing == cheap &&
              allSorted.get(1).listing == mid &&
              allSorted.get(2).listing == expensive &&
              allSorted.get(3).listing == unknown,
              "Toutes : 49 000 € puis 93 000 € puis 180 000 €, prix inconnu à la fin");
        List<ListingEngine.Result> acceptedSorted = SourceListings.select(prices, "", true);
        check(acceptedSorted.size() == 4 &&
              acceptedSorted.get(0).listing == cheap &&
              acceptedSorted.get(3).listing == unknown,
              "Annonces triées : du moins cher au plus cher");
        List<ListingEngine.Result> sourceSorted = SourceListings.select(prices, "SeLoger", false);
        check(sourceSorted.size() == 2 &&
              sourceSorted.get(0).listing == cheap &&
              sourceSorted.get(1).listing == unknown,
              "Chaque portail est également trié par prix croissant");
        System.out.println("TerraNova : onglets et prix croissants testés, CID exclu du tri.");
    }
}
