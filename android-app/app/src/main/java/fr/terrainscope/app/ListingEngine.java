package fr.terrainscope.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Moteur de tri local. Aucune requête réseau, aucun crédit.
 * Les annonces récupérées restent uniquement en mémoire pendant la recherche.
 */
public final class ListingEngine {
    private ListingEngine() {}

    public enum State { ACCEPTED, BUILDER, DUPLICATE, TO_VERIFY }

    public static final class Listing {
        public final String source, url, title, city, seller, description, address, lot;
        public final long priceEuros;
        public final double areaM2;
        public final boolean cityConfirmed;

        public Listing(String source, String url, String title, String city, String seller,
                       String description, String address, String lot, long priceEuros,
                       double areaM2, boolean cityConfirmed) {
            this.source = clean(source);
            this.url = clean(url);
            this.title = clean(title);
            this.city = clean(city);
            this.seller = clean(seller);
            this.description = clean(description);
            this.address = clean(address);
            this.lot = clean(lot);
            this.priceEuros = priceEuros;
            this.areaM2 = areaM2;
            this.cityConfirmed = cityConfirmed;
        }
    }

    public static final class Result {
        public final Listing listing;
        public State state = State.ACCEPTED;
        public String reason = "";
        public String preferredSource = "";
        public final List<String> otherSources = new ArrayList<>();
        private Result(Listing listing) { this.listing = listing; }
    }

    private static final String[] BRANDS = {
        "mikit", "ericlor", "natilia", "tradibudget", "villadeale", "primea",
        "constructions ideale demeure", "cid", "maisons de stephanie",
        "maison de stephanie", "alysia", "alicya", "france confort",
        "pierre et terre", "le masson", "maison laure", "maisons laure",
        "mb habitat", "maisons i tours", "maison i tours",
        "evolve construction", "imoter", "df constructions",
        "logis avenir", "maisons castor", "maison phénix",
        "maison phenix", "les maisons de l avenir", "maisons d en france"
    };
    private static final String[] LOTISSEURS = {
        "villadim", "groupe set", "afedim", "val touraine habitat", "viabilis",
        "acanthe", "arpent", "foncier amenagement", "nexity",
        "entre terrains", "terravia", "lodgim", "quatro promotion",
        "touraine logement", "francelot", "khor", "lelievre immobilier",
        "exeo promotion", "immo nature et construction",
        "credit mutuel amenagement foncier", "cm cic amenagement foncier",
        "cmcic amenagement foncier", "credit mutuel immobilier"
    };
    private static final Pattern OFFER = Pattern.compile(
        "\\b(?:maison\\s+(?:et\\s+)?terrain|terrain\\s+(?:et\\s+)?maison|" +
        "ccmi|contrat de construction de maison individuelle|" +
        "projet de maison avec terrain)\\b"
    );

    public static String normalize(String value) {
        String v = Normalizer.normalize(clean(value), Normalizer.Form.NFD);
        return v.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replace('’', ' ').replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private static boolean containsPhrase(String text, String phrase) {
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    public static boolean isBuilder(Listing item) {
        String seller = normalize(item.seller);
        String headline = normalize(item.title);
        String full = normalize(item.title + " " + item.seller + " " + item.description);
        // CPR est conservé si c'est bien l'annonceur ou le nom de l'offre :
        // une simple référence à CPR dans un texte concurrent n'y donne pas droit.
        if (containsPhrase(seller, "maisons cpr") ||
                containsPhrase(seller, "maison cpr") ||
                containsPhrase(headline, "maisons cpr") ||
                containsPhrase(headline, "maison cpr")) return false;
        String details = normalize(item.description);
        boolean sellerUnknown = seller.isEmpty() || seller.equals("pro") ||
            seller.equals("professionnel") || seller.equals("immobilier neuf") ||
            seller.equals("annonceur professionnel");
        boolean constructionPitch =
            containsPhrase(details, "vous propose de realiser votre projet de construction") ||
            containsPhrase(details, "vous propose de construire") ||
            containsPhrase(details, "propose de construire votre maison") ||
            containsPhrase(details, "maison neuve avec toutes les prestations") ||
            containsPhrase(details, "constructeur de maisons individuelles");
        for (String brand : BRANDS) {
            // « près du lotissement Logis Avenir » dans une annonce d'agence
            // ne transforme PAS cette agence en constructeur.
            if (containsPhrase(seller, brand) || containsPhrase(headline, brand)) return true;
            if (sellerUnknown && constructionPitch && containsPhrase(details, brand))
                return true;
        }
        // Un montage maison + terrain (CCMI, projet maison sur parcelle)
        // reste une offre constructeur même si le champ vendeur manque.
        // « terrain libre de constructeur » ne satisfait pas OFFER.
        return OFFER.matcher(full).find();
    }

    public static boolean requiresBuildabilityCheck(Listing item) {
        String title = normalize(item.title);
        String description = normalize(item.description);
        // Un terrain de loisir / agricole n'est pas un terrain à bâtir.
        // Le conserver dans « Toutes » mais pas parmi les opportunités client.
        return containsPhrase(title,"terrain de loisirs") ||
            containsPhrase(title,"terrain loisirs") ||
            containsPhrase(title,"terrain agricole") ||
            containsPhrase(title,"terrain non constructible") ||
            containsPhrase(description,"terrain non constructible") ||
            containsPhrase(description,"terrain a usage de loisirs");
    }

    public static boolean isLotisseur(Listing item) {
        String text = normalize(item.seller + " " + item.title);
        for (String name : LOTISSEURS) if (containsPhrase(text, name)) return true;
        return false;
    }

    public static int priority(String source) {
        String s = normalize(source);
        if (s.contains("leboncoin") || s.contains("le bon coin")) return 0;
        if (s.contains("seloger")) return 1;
        if (s.contains("bienici") || s.contains("bien ici")) return 2;
        if (s.contains("notaire") || s.contains("immonot")) return 3;
        if (s.equals("iad") || s.contains("iadfrance")) return 4;
        return 5;
    }

    private static String urlKey(String url) {
        // Même fiche relue lors du défilement ou d'une deuxième capture.
        return clean(url).replaceFirst("#.*$", "").replaceFirst("/$", "");
    }

    private static boolean conflict(String a, String b) {
        return !normalize(a).isEmpty() && !normalize(b).isEmpty()
                && !normalize(a).equals(normalize(b));
    }

    private static boolean meaningfulSellerMatch(String first, String second) {
        String a = normalize(first), b = normalize(second);
        if (a.isEmpty() || b.isEmpty()) return false;
        // « agence », « particulier » et « professionnel » sont des étiquettes,
        // pas une identité de vendeur : même prix/surface ne suffit pas.
        for (String generic : new String[]{"agence", "agence immobiliere",
                "particulier", "professionnel", "pro", "notaire", "iad"}) {
            if (a.equals(generic) || b.equals(generic)) return false;
        }
        if (a.equals(b)) return true;
        // « Sophie Viale BSK Immobilier » et « BSK Immobilier » sont compatibles.
        String shorter = a.length() <= b.length() ? a : b;
        String longer = a.length() > b.length() ? a : b;
        return shorter.length() >= 10 && containsPhrase(longer, shorter);
    }

    private static boolean duplicate(Listing a, Listing b) {
        if (!urlKey(a.url).isEmpty() && urlKey(a.url).equals(urlKey(b.url))) return true;
        // Même prix/surface sur un même portail peut correspondre à deux lots distincts.
        if (normalize(a.source).equals(normalize(b.source))) return false;
        if (normalize(a.city).isEmpty() || !normalize(a.city).equals(normalize(b.city))) return false;
        if (a.priceEuros <= 0 || a.priceEuros != b.priceEuros) return false;
        if (a.areaM2 <= 0 || Math.abs(a.areaM2 - b.areaM2) > 0.1) return false;
        // Même surface/prix dans un lotissement : plusieurs lots peuvent coexister.
        // Il faut un indice d'identité supplémentaire : adresse exacte,
        // numéro de lot ET emplacement, ou annonceur réellement identique.
        if (conflict(a.lot, b.lot) || conflict(a.address, b.address)) return false;
        boolean samePreciseAddress = !normalize(a.address).isEmpty() &&
            normalize(a.address).equals(normalize(b.address)) &&
            normalize(a.address).length() >= 12;
        boolean sameSeller = meaningfulSellerMatch(a.seller, b.seller);
        return samePreciseAddress || sameSeller;
    }

    public static List<Result> classify(List<Listing> input) {
        ArrayList<Result> output = new ArrayList<>();
        for (Listing listing : input) output.add(new Result(listing));
        // Les sources prioritaires sont traitées en premier sans réordonner l'écran « Toutes ».
        ArrayList<Result> ordered = new ArrayList<>(output);
        ordered.sort(Comparator.comparingInt(r -> priority(r.listing.source)));
        ArrayList<Result> retained = new ArrayList<>();
        for (Result r : ordered) {
            if (isBuilder(r.listing)) {
                r.state = State.BUILDER;
                r.reason = "Constructeur identifié ou offre maison + terrain";
                continue;
            }
            // Un portail peut proposer des villes voisines sous la rubrique
            // « Ces biens pourraient vous intéresser ». Ne jamais présenter
            // ces annonces comme appartenant à la commune demandée.
            if (!r.listing.cityConfirmed) {
                r.state = State.TO_VERIFY;
                r.reason = "Commune non confirmée sur la fiche : vérifier avant de présenter";
                continue;
            }
            if (requiresBuildabilityCheck(r.listing)) {
                r.state = State.TO_VERIFY;
                r.reason = "Terrain non constructible, agricole ou de loisirs : vérifier avant de proposer";
                continue;
            }
            Result original = null;
            for (Result previous : retained) {
                if (duplicate(r.listing, previous.listing)) {
                    original = previous;
                    break;
                }
            }
            if (original != null) {
                r.state = State.DUPLICATE;
                r.preferredSource = original.listing.source;
                r.reason = "Même commune, prix, surface et annonceur/adresse : référence " +
                    original.listing.source;
                if (!original.otherSources.contains(r.listing.source)
                        && !original.listing.source.equals(r.listing.source)) {
                    original.otherSources.add(r.listing.source);
                }
            } else {
                retained.add(r);
                r.state = State.ACCEPTED;
                if (!r.listing.cityConfirmed) r.reason = "Commune à confirmer sur l'annonce";
                else if (r.listing.seller.isEmpty()) r.reason = "Vendeur à vérifier";
                else if (isLotisseur(r.listing)) r.reason = "Lotisseur : terrain ou lot à vérifier";
            }
        }
        return output;
    }

    public static List<Result> filtered(List<Result> all) {
        ArrayList<Result> results = new ArrayList<>();
        for (Result r : all) if (r.state == State.ACCEPTED) results.add(r);
        results.sort(Comparator.comparingInt(r -> priority(r.listing.source)));
        return results;
    }

    public static Map<State, Integer> counts(List<Result> all) {
        Map<State, Integer> counts = new LinkedHashMap<>();
        for (State state : State.values()) counts.put(state, 0);
        for (Result r : all) counts.put(r.state, counts.get(r.state) + 1);
        return counts;
    }
}
