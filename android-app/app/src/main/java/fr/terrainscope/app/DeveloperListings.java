package fr.terrainscope.app;

import java.net.URI;
import java.util.List;

/**
 * Compteurs PARTIELS des lotisseurs : seules les fiches de terrains
 * réellement extraites et acceptées sont comptées. Pas de chiffre fictif
 * obtenu depuis une page catalogue ou une source bloquée.
 */
public final class DeveloperListings {
    private DeveloperListings() {}

    private static boolean hasPhrase(String text, String phrase) {
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    private static boolean hasAny(String text, String... variants) {
        for (String name : variants)
            if (hasPhrase(text, ListingEngine.normalize(name))) return true;
        return false;
    }

    static boolean sellerMatches(String developerName, String seller) {
        String company = ListingEngine.normalize(developerName);
        String name = ListingEngine.normalize(seller);
        if (name.isEmpty() || name.equals("pro") || name.equals("professionnel"))
            return false;
        if (company.equals("cm cic afedim"))
            return hasAny(name, "afedim", "cm cic", "cmcic",
                "credit mutuel amenagement foncier", "credit mutuel immobilier",
                "credit mutuel foncier", "cic immobilier");
        if (company.equals("francelot khor"))
            return hasAny(name, "francelot", "khor immobilier", "khor");
        if (company.equals("groupe set"))
            return hasAny(name, "groupe set", "set amenagement");
        if (company.equals("lelievre immobilier"))
            return hasAny(name, "lelievre immobilier", "lelievre");
        if (company.equals("immo nature et construction"))
            return hasAny(name, "immo nature et construction", "immo nature constructions");
        if (company.equals("foncier amenagement"))
            return hasAny(name, "foncier amenagement");
        // Pour les autres partenaires, exiger le nom commercial entier dans l'annonceur.
        return hasPhrase(name, company);
    }

    static boolean officialUrlMatches(String siteDomain, String listingUrl) {
        if (siteDomain == null || siteDomain.isEmpty() ||
            listingUrl == null || listingUrl.isEmpty()) return false;
        try {
            URI url = new URI(listingUrl);
            if (!"https".equalsIgnoreCase(url.getScheme())) return false;
            String host = url.getHost() == null ? "" : url.getHost().toLowerCase();
            String domain = siteDomain.toLowerCase();
            return host.equals(domain) || host.endsWith("." + domain);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static int count(List<ListingEngine.Result> results,
                            String developerName, String domain) {
        int total = 0;
        for (ListingEngine.Result result : results) {
            if (result.state != ListingEngine.State.ACCEPTED) continue;
            ListingEngine.Listing listing = result.listing;
            if (sellerMatches(developerName, listing.seller) ||
                officialUrlMatches(domain, listing.url)) total++;
        }
        return total;
    }
}
