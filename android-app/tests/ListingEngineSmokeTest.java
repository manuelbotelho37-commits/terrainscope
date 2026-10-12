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
            "Agence Touraine","Monnaie",85000,500,"A1","https://www.leboncoin.fr/ad/vents/123");
        ListingEngine.Listing seloger = listing("SeLoger","Terrain à bâtir",
            "Agence Touraine","Monnaie",85000,500,"A1","https://www.seloger.com/annonce/456");
        ListingEngine.Listing bienici = listing("Bien’ici","Parcelle à bâtir",
            "Agence Touraine","Monnaie",85000,500,"A1","https://www.bienici.com/annonce/vente/789");

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
        ListingEngine.Listing nearby = new ListingEngine.Listing(
            "IAD", "https://www.iadfrance.fr/annonce/terrain-vente/999",
            "Terrain à Vernou-sur-Brenne (37210)", "Monnaie", "Agent IAD", "",
            "", "", 35000, 700, false);
        List<ListingEngine.Result> nearbyResults = ListingEngine.classify(Arrays.asList(lbc, nearby));
        check(status(nearbyResults, "IAD")==ListingEngine.State.TO_VERIFY,
            "Annonce de commune voisine retenue pour vérification seulement");
        check(ListingEngine.filtered(nearbyResults).size()==1,
            "Les communes voisines ne contaminent pas les terrains triés");

        // Cas Monnaie observés sur les portails : agences, constructeurs,
        // aménageurs, même surface/prix répliqués sur plusieurs sites.
        ListingEngine.Listing bsk = listing("SeLoger","Terrain constructible",
            "SOPHIE VIALE BSK IMMOBILIER","Monnaie",145000,838,"B19",
            "https://www.seloger.com/annonce/838-bsk");
        check(!ListingEngine.isBuilder(bsk),"Agence BSK reste visible");
        ListingEngine.Listing logis = listing("SeLoger","Terrain viabilisé",
            "LOGIS AVENIR","Monnaie",55600,215,"",
            "https://www.seloger.com/annonce/logis-215");
        check(ListingEngine.isBuilder(logis),"Logis Avenir reconnu comme constructeur");
        ListingEngine.Listing villadim = listing("Le Bon Coin","Terrain à bâtir",
            "Villadim Aménagement et Promotion","Monnaie",78300,406,"",
            "https://www.leboncoin.fr/ad/ventes_immobilieres/123");
        check(!ListingEngine.isBuilder(villadim),"Villadim reste dans les terrains");
        check(ListingEngine.isLotisseur(villadim),"Villadim reconnu comme lotisseur");
        ListingEngine.Listing cmcic = listing("SeLoger","Terrain viabilisé",
            "CREDIT MUTUEL AMENAGEMENT FONCIER TOURS","Monnaie",79000,360,"",
            "https://www.seloger.com/annonce/cmcic");
        check(ListingEngine.isLotisseur(cmcic),"Crédit Mutuel Aménagement reste lotisseur");
        ListingEngine.Listing bskRepeat = listing("Bien’ici","Terrain constructible",
            "BSK IMMOBILIER","Monnaie",145000,838,"B19",
            "https://www.bienici.com/annonce/vente/838-bsk");
        List<ListingEngine.Result> afterTri = ListingEngine.classify(
            Arrays.asList(bskRepeat, logis, villadim, bsk));
        check(ListingEngine.filtered(afterTri).size()==2,
            "Un terrain BSK et un terrain Villadim, sans Logis Avenir ni doublon");
        check(status(afterTri,"Bien’ici")==ListingEngine.State.DUPLICATE,
            "Doublon agence BSK repéré sur Bien'ici");
        check(afterTri.stream().filter(r->r.listing==bsk).findFirst().get().state ==
              ListingEngine.State.ACCEPTED,
            "SeLoger conserve BSK après tri");
        check(afterTri.stream().filter(r->r.listing==logis).findFirst().get().state ==
              ListingEngine.State.BUILDER,
            "SeLoger écarte Logis Avenir après tri");

        ListingEngine.Listing leisure = listing("IAD","Terrain de loisirs à Monnaie",
            "Conseiller iad","Monnaie",25000,1400,"","https://www.iadfrance.fr/annonce/leisure");
        check(ListingEngine.requiresBuildabilityCheck(leisure),
            "Un terrain de loisirs n'est pas un terrain à bâtir");
        check(ListingEngine.filtered(ListingEngine.classify(Arrays.asList(leisure))).isEmpty(),
            "Terrain de loisirs exclu des offres présentées aux clients");
        ListingEngine.Listing nonBuildable = new ListingEngine.Listing(
            "Notaires", "https://www.immobilier.notaires.fr/fr/annonce/123",
            "Terrain à vendre", "Monnaie", "Notaire", "Terrain non constructible",
            "", "", 35000, 1000, true);
        check(ListingEngine.requiresBuildabilityCheck(nonBuildable),
            "Non constructible marqué à vérifier");
        ListingEngine.Listing genuineLand = new ListingEngine.Listing(
            "IAD","https://www.iadfrance.fr/annonce/terrain-vente-monnaie-1328m2/r1976940",
            "Terrain à bâtir à Monnaie","Monnaie","Alice Ravinet",
            "Constructible, possibilité de réaliser une maison", "", "",
            138500,1328,true);
        check(!ListingEngine.requiresBuildabilityCheck(genuineLand),
            "Terrain constructible proposé par iad conservé");

        // Deux terrains distincts peuvent partager le même prix et la même
        // surface sur deux portails : éviter l'effacement automatique.
        ListingEngine.Listing lotAlpha = listing("SeLoger","Terrain A",
            "Agence Bellevue","Monnaie",99000,520,"",
            "https://www.seloger.com/annonce/lot-alpha");
        ListingEngine.Listing lotBeta = listing("Bien’ici","Terrain B",
            "Agence de la Gare","Monnaie",99000,520,"",
            "https://www.bienici.com/annonce/lot-beta");
        check(ListingEngine.filtered(ListingEngine.classify(Arrays.asList(lotAlpha,lotBeta))).size()==2,
            "Même prix et surface, annonceurs différents : deux terrains conservés");
        ListingEngine.Listing unknownSeller = listing("Bien’ici","Terrain sans vendeur",
            "","Monnaie",99000,520,"",
            "https://www.bienici.com/annonce/terrain-non-attribue");
        List<ListingEngine.Result> missingSeller =
            ListingEngine.classify(Arrays.asList(lotAlpha, unknownSeller));
        check(missingSeller.size()==2 &&
              missingSeller.stream().anyMatch(r -> r.listing == unknownSeller &&
                  r.state == ListingEngine.State.TO_VERIFY),
            "Vendeur manquant : conserver la fiche dans Toutes, sans proposer avant contrôle");
        ListingEngine.Listing genericSeller = listing("Bien’ici","Terrain de lotissement",
            "Agence","Monnaie",99000,520,"",
            "https://www.bienici.com/annonce/terrain-generique");
        check(ListingEngine.filtered(ListingEngine.classify(Arrays.asList(lotAlpha,genericSeller))).size()==2,
            "Simple étiquette Agence : pas une preuve de doublon");
        ListingEngine.Listing sharedAddressA = new ListingEngine.Listing(
            "SeLoger","https://www.seloger.com/annonce/parcelle-rue-nantes",
            "Terrain constructible","Monnaie","Agence Bellevue","",
            "14 rue de Nantes, Monnaie","",99000,520,true);
        ListingEngine.Listing sharedAddressB = new ListingEngine.Listing(
            "Bien’ici","https://www.bienici.com/annonce/rue-nantes",
            "Terrain à bâtir","Monnaie","Autre agence immobilière","",
            "14 rue de Nantes, Monnaie","",99000,520,true);
        check(ListingEngine.filtered(ListingEngine.classify(Arrays.asList(sharedAddressA,sharedAddressB))).size()==1,
            "Adresse précise identique : rapprochement malgré deux agences");

        ListingEngine.Listing independentAgency = new ListingEngine.Listing(
            "SeLoger","https://www.seloger.com/annonce/agence-proche-logis",
            "Terrain constructible", "Monnaie", "Agence Touraine",
            "À proximité d'un ancien programme Logis Avenir, terrain libre de constructeur",
            "", "",90000,540,true);
        check(!ListingEngine.isBuilder(independentAgency),
            "La mention d'un constructeur voisin ne doit pas supprimer une annonce d'agence");
        ListingEngine.Listing competitorUsingCPR = new ListingEngine.Listing(
            "SeLoger","https://www.seloger.com/annonce/competitor-cpr",
            "Projet terrain constructible", "Monnaie", "LOGIS AVENIR",
            "Comparez vos plans aux projets Maisons CPR",
            "", "",85000,500,true);
        check(ListingEngine.isBuilder(competitorUsingCPR),
            "Un constructeur concurrent reste exclu même si sa description mentionne CPR");
        ListingEngine.Listing actualCPR = new ListingEngine.Listing(
            "Le Bon Coin","https://www.leboncoin.fr/ad/ventes_immobilieres/constructeur-cpr",
            "Terrain constructible", "Monnaie", "Maisons CPR",
            "Parcelle située près d'un terrain Logis Avenir", "", "",110000,650,true);
        check(!ListingEngine.isBuilder(actualCPR),
            "Maisons CPR est conservé même avec la mention d'un concurrent à proximité");

        ListingEngine.Listing genericBuilder = new ListingEngine.Listing(
            "SeLoger", "https://www.seloger.com/annonce/maison-terrains-123",
            "Terrain constructible à vendre", "Monnaie", "Immobilier neuf",
            "Sur ce terrain LOGIS AVENIR vous propose de réaliser votre projet de construction " +
                "de maison individuelle avec les prestations comprises.",
            "", "", 64900, 283, true);
        check(ListingEngine.isBuilder(genericBuilder),
            "Annonceur générique mais argumentaire de Logis Avenir : constructeur exclu");
        ListingEngine.Listing unnamedAgency = new ListingEngine.Listing(
            "SeLoger", "https://www.seloger.com/annonce/terrain-libre-124",
            "Terrain constructible à vendre", "Monnaie", "",
            "Terrain situé près d'une ancienne opération Logis Avenir, libre de constructeur.",
            "", "", 64900, 283, true);
        check(!ListingEngine.isBuilder(unnamedAgency),
            "Vendeur absent et concurrent cité sans offre de construction : terrain conservé");
        ListingEngine.Listing knownAgency = new ListingEngine.Listing(
            "SeLoger", "https://www.seloger.com/annonce/terrain-libre-125",
            "Terrain constructible à vendre", "Monnaie", "BSK IMMOBILIER",
            "Un constructeur Logis Avenir vous propose de construire sur un terrain voisin.",
            "", "", 64900, 283, true);
        check(!ListingEngine.isBuilder(knownAgency),
            "Agence identifiée : la publicité du voisin ne doit pas la masquer");

        ListingEngine.Listing samePromoterLot1 = listing(
            "SeLoger","Terrain lotissement libre de constructeur","Villadim",
            "Monnaie",90000,410,"",
            "https://www.seloger.com/annonce/villadim-lot1");
        ListingEngine.Listing samePromoterLot2 = listing(
            "Bien’ici","Terrain lotissement libre de constructeur","Villadim",
            "Monnaie",90000,410,"",
            "https://www.bienici.com/annonce/vente/villadim-lot2");
        check(ListingEngine.filtered(ListingEngine.classify(
            Arrays.asList(samePromoterLot1,samePromoterLot2))).size()==2,
            "Même promoteur, surface et prix : préserver les deux lots sans numéro connu");
        ListingEngine.Listing onlyTownA = new ListingEngine.Listing(
            "SeLoger","https://www.seloger.com/annonce/ville",
            "Terrain Monnaie", "Monnaie", "Agence Bellevue", "",
            "Monnaie 37380","",112000,600,true);
        ListingEngine.Listing onlyTownB = new ListingEngine.Listing(
            "Bien’ici","https://www.bienici.com/annonce/vente/ville",
            "Terrain Monnaie", "Monnaie", "Agence de la Gare", "",
            "Monnaie 37380","",112000,600,true);
        check(ListingEngine.filtered(ListingEngine.classify(
            Arrays.asList(onlyTownA,onlyTownB))).size()==2,
            "Même commune comme adresse non spécifique : deux terrains distincts");

        // Cas remonté avec capture écran : Bien'ici, Monnaie, 374 m², 71 000 €,
        // annonce de CONSTRUCTIONS IDÉALE DEMEURE (CID).
        ListingEngine.Listing cidMonnaie = new ListingEngine.Listing(
            "Bien’ici", "https://www.bienici.com/annonce/vente/terrain-cid-374",
            "Achat terrain 374 m²", "Monnaie", "CONSTRUCTIONS IDEALE DEMEURE",
            "374 m² de terrain à Monnaie", "", "", 71000, 374, true,
            "https://cdn.example.com/monnaie-374-terrain.jpg");
        check(ListingEngine.isBuilder(cidMonnaie),
            "CID Constructions Idéale Demeure est un constructeur à exclure");
        List<ListingEngine.Result> cidChecked =
            ListingEngine.classify(Arrays.asList(cidMonnaie));
        check(cidChecked.get(0).state == ListingEngine.State.BUILDER,
            "CID figure en constructeur exclu");
        check(ListingEngine.filtered(cidChecked).isEmpty(),
            "CID ne doit jamais apparaître dans Annonces triées");
        check(cidChecked.get(0).listing.imageUrl.endsWith("terrain.jpg"),
            "Photo de la fiche conservée même si le constructeur est exclu");
        ListingEngine.Listing cidInText = new ListingEngine.Listing(
            "Bien’ici", "https://www.bienici.com/annonce/vente/terrain-cid-374b",
            "Achat terrain 374 m²", "Monnaie", "",
            "CONSTRUCTIONS IDEALE DEMEURE · Saint-Cyr-sur-Loire",
            "", "", 71000, 374, true);
        check(ListingEngine.isBuilder(cidInText),
            "CID exclu lorsque seul le descriptif contient sa raison sociale");
        ListingEngine.Listing missingAdvertiser = new ListingEngine.Listing(
            "Bien’ici", "https://www.bienici.com/annonce/vente/terrain-sans-vendeur",
            "Terrain constructible", "Monnaie", "", "",
            "", "", 71000, 374, true);
        check(ListingEngine.classify(Arrays.asList(missingAdvertiser)).get(0).state ==
              ListingEngine.State.TO_VERIFY,
            "Annonceur absent : à vérifier et visible dans Toutes les annonces");

        System.out.println("TerrainScope : " + assertions + " vérifications du moteur réussies.");
    }
}
