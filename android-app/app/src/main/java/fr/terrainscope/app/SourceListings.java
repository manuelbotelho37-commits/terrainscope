package fr.terrainscope.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Navigation par portail : ne dépend ni de la WebView, ni du réseau.
 * "Toutes" conserve chaque fiche collectée, même constructeur ou à vérifier.
 */
public final class SourceListings {
    private SourceListings() {}

    public static List<ListingEngine.Result> select(
            List<ListingEngine.Result> classified, String site, boolean acceptedOnly) {
        List<ListingEngine.Result> chosen = acceptedOnly
            ? ListingEngine.filtered(classified) : new ArrayList<>(classified);
        String key = ListingEngine.normalize(site);
        if (!key.isEmpty()) {
            chosen.removeIf(result -> !ListingEngine.normalize(result.listing.source).equals(key));
        }
        // Même tri sur les terrains sélectionnés, Toutes et chaque portail.
        chosen.sort(ListingEngine.BY_ASCENDING_PRICE);
        return chosen;
    }

    public static int count(List<ListingEngine.Result> classified, String site) {
        return select(classified, site, false).size();
    }
}
