package fr.terrainscope.app;

import java.util.Arrays;
import java.util.List;

public final class DeveloperListingsSmokeTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static ListingEngine.Listing listing(String source, String seller,
                                                 String url, long price) {
        return new ListingEngine.Listing(source, url, "Terrain constructible",
            "Azay-sur-Cher", seller, "", "", "", price, 450, true);
    }

    public static void main(String[] args) {
        ListingEngine.Listing creditMutuel = listing("SeLoger",
            "CREDIT MUTUEL AMENAGEMENT FONCIER TOURS",
            "https://www.seloger.com/annonce/afedim-a", 79000);
        ListingEngine.Listing afedim = listing("Bien’ici", "AFEDIM",
            "https://www.bienici.com/annonce/vente/afedim-b", 92000);
        ListingEngine.Listing cmcic = listing("IAD", "CM-CIC Aménagement Foncier",
            "https://www.iadfrance.fr/annonces/afedim-c", 110000);
        ListingEngine.Listing otherAgent = listing("Le Bon Coin", "Agence du Val",
            "https://www.leboncoin.fr/ad/ventes_immobilieres/abc", 129000);
        ListingEngine.Listing official = listing("Autre", "Agence du Val",
            "https://www.afedim.fr/fr/achat-immobilier/annonces/terrain-123", 150000);
        ListingEngine.Listing homeBuilder = listing("Bien’ici",
            "CONSTRUCTIONS IDEALE DEMEURE",
            "https://www.bienici.com/annonce/vente/maison-terrain", 225000);
        List<ListingEngine.Result> results = ListingEngine.classify(
            Arrays.asList(creditMutuel, afedim, cmcic, otherAgent, official, homeBuilder));
        check(DeveloperListings.count(results, "CM-CIC · AFEDIM", "afedim.fr") == 4,
            "Les annonces AFEDIM et Crédit Mutuel vérifiées doivent apparaître sur sa pastille");
        check(DeveloperListings.count(results, "Villadim", "villadim.fr") == 0,
            "Ne pas attribuer un lotisseur sans preuve");
        check(DeveloperListings.sellerMatches("CM-CIC · AFEDIM",
            "CREDIT MUTUEL IMMOBILIER"), "Crédit Mutuel Immobilier alias AFEDIM");
        check(!DeveloperListings.sellerMatches("CM-CIC · AFEDIM",
            "Agence immobilière"), "Ne pas confondre une agence et AFEDIM");
        check(DeveloperListings.officialUrlMatches("afedim.fr",
            "https://www.afedim.fr/annonces/abc"), "Sous-domaines officiels acceptés");
        check(!DeveloperListings.officialUrlMatches("afedim.fr",
            "https://afedim.fr.malveillant.example/terrain"), "Domaines imitateurs refusés");
        check(DeveloperListings.count(results, "Nexity", "nexity.fr") == 0,
            "Aucune fiche Nexity ne doit être inventée");
        System.out.println("TerraNova : regroupement AFEDIM / CM-CIC et compteurs lotisseurs partiels testés.");
    }
}
