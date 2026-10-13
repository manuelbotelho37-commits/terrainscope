package fr.terrainscope.app;

import java.util.Map;

/**
 * URLs de recherche. Génère UNIQUEMENT des liens réellement ciblés
 * lorsque la commune et ses paramètres sont connus. Pas de secours générique.
 */
public final class SearchUrls {
    private final Map<String, String> postals;
    private final Map<String, String> seLogerPaths;

    public SearchUrls(Map<String, String> postals, Map<String, String> seLogerPaths) {
        this.postals = postals;
        this.seLogerPaths = seLogerPaths;
    }

    /** Liens de PROGRAMMES officiels vérifiés pour une commune précise.
     * Ne jamais faire croire qu'une page nationale est filtrée par ville. */
    public String urlForDeveloper(String developer, String city) {
        String company = ListingEngine.normalize(developer);
        String cityKey = ListingEngine.normalize(city);
        if (company.equals("cm cic afedim") && cityKey.equals("azay sur cher")) {
            return "https://www.afedim.fr/fr/achat-immobilier/programme/terrain/" +
                "azay-sur-cher-37/015753/Fiche";
        }
        return null; // Aucun lien direct pour cette paire lotisseur/commune vérifié.
    }

    public String urlFor(String source, String city) {
        String key = ListingEngine.normalize(city);
        String code = postals.get(key);
        if (key.isEmpty() || code == null || !code.matches("\\d{5}")) return null;
        String slug = key.replace(' ', '-');
        switch (source) {
            case "Le Bon Coin":
                // Filtre géographique natif + type immobilier Terrain (3).
                // /recherche?text=terrain ouvre au contraire les maisons, les
                // appartements et parfois toute la France. Ne jamais l'utiliser.
                return "https://www.leboncoin.fr/cl/ventes_immobilieres/cp_"
                    + slug + "_" + code + "/real_estate_type%3A3";
            case "SeLoger":
                String path = seLogerPaths.get(key);
                return path == null ? null :
                    "https://www.seloger.com/recherche/achat/terrain/centre-val-de-loire/" + path;
            case "Bien’ici":
                return "https://www.bienici.com/recherche/achat/" + slug + "-" + code + "/terrain";
            case "IAD":
                return "https://www.iadfrance.fr/annonces/" + slug + "-" + code + "/vente/terrain";
            case "Notaires":
                // URLs précises du portail notarial vérifiées pour ces villes.
                // Pour les autres communes, éviter la page nationale (35 000+ biens).
                switch (key) {
                    case "monts": return "https://www.immobilier.notaires.fr/fr/annonces-immobilieres/vente/terrain/monts-37";
                    case "chinon": return "https://www.immobilier.notaires.fr/fr/annonces-immobilieres/vente/terrain/chinon-37";
                    case "veigne": return "https://www.immobilier.notaires.fr/fr/annonces-immobilieres/vente/terrain/veigne-37";
                    case "montlouis sur loire":
                        return "https://www.immobilier.notaires.fr/fr/annonces-immobilieres/vente/terrain/montlouis-sur-loire-37";
                    default: return null;
                }
            default:
                return null;
        }
    }
}
