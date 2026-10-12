package fr.terrainscope.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prototype Android : consultation des sites dans une WebView et extraction
 * locale, déclenchée par l'utilisateur. Aucun backend, crédit ou stockage.
 * Le module des 18 lotisseurs existant reste disponible via TerrainScope Web.
 */
public final class MainActivity extends Activity {
    private static final String TERRAIN_SCOPE = "https://manuelbotelho37-commits.github.io/terrainscope/";
    private static final String[] SITES =
        {"Le Bon Coin", "SeLoger", "Bien’ici", "IAD", "Notaires"};
    private static final int GREEN = Color.rgb(30, 127, 83);
    private static final int PALE = Color.rgb(223, 244, 210);
    private final Map<String, String> postalCodes = new HashMap<>();
    // Codes de localité SeLoger vérifiés dans la version web existante.
    private final Map<String, String> selogerPaths = new HashMap<>();
    private final LinkedHashMap<String, ListingEngine.Listing> imported = new LinkedHashMap<>();
    private final LinkedHashSet<String> sourcesVisited = new LinkedHashSet<>();

    private AutoCompleteTextView cityInput;
    private final ArrayList<String> communeNames = new ArrayList<>();
    private Spinner siteSpinner;
    private WebView browser;
    private LinearLayout main;
    private LinearLayout cards;
    private ScrollView results;
    private TextView status;
    private boolean showingBrowser = true;
    private boolean showLotisseursOnLoad = false;
    private String activeCity = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        loadCommuneIndex();
        loadSeLogerPaths();
        buildScreen();
        chooseTab(filteredTab);
        displayListings(true);
        status.setText("Choisis une commune, ouvre un site puis importe sa page. Aucune annonce fictive.");
    }


    private static final int DARK = Color.rgb(11, 22, 32);
    private static final int LIGHT = Color.rgb(16, 29, 41);
    private static final int SOFT = Color.rgb(43, 54, 66);
    private static final int LIME = Color.rgb(212, 175, 55);
    private static final int TEXT = Color.rgb(242, 235, 218);
    private static final int MUTED = Color.rgb(175, 184, 195);
    private static final int GOLD = Color.rgb(212, 175, 55);
    private static final int BORDER = Color.rgb(72, 77, 78);
    private Button filteredTab;
    private Button allTab;
    private Button lotisseursTab;

    private GradientDrawable rounded(int fill, int radius, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (border != Color.TRANSPARENT) d.setStroke(dp(1), border);
        return d;
    }

    private void place(LinearLayout parent, View child, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(top), 0, dp(bottom));
        parent.addView(child, p);
    }

    private TextView heading(String content, int size, int color, boolean bold) {
        TextView v = text(content, size, bold);
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        return v;
    }

    private LinearLayout section(int fill, int radius, int border) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(14), dp(12), dp(14), dp(12));
        v.setBackground(rounded(fill, radius, border));
        return v;
    }

    private Button action(String title, boolean primary) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(primary ? DARK : TEXT);
        b.setBackground(rounded(primary ? GOLD : LIGHT, 11,
            primary ? Color.TRANSPARENT : BORDER));
        b.setElevation(0);
        b.setMinimumHeight(dp(48));
        b.setMinHeight(dp(48));
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }

    private void addControl(LinearLayout row, Button b, float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(47), weight);
        p.setMargins(dp(2), 0, dp(2), 0);
        row.addView(b, p);
    }

    private void chooseTab(Button selected) {
        for (Button b : new Button[]{filteredTab, allTab, lotisseursTab}) {
            if (b == null) continue;
            boolean active = b == selected;
            b.setTextColor(active ? DARK : TEXT);
            b.setBackground(rounded(active ? GOLD : LIGHT, 11,
                active ? Color.TRANSPARENT : BORDER));
        }
    }

    private void buildScreen() {
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);
        getWindow().getDecorView().setSystemUiVisibility(0);
        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(12), dp(9), dp(12), dp(8));
        main.setBackgroundColor(LIGHT);

        LinearLayout masthead = section(DARK, 18, Color.TRANSPARENT);
        LinearLayout brandLine = row();
        brandLine.setGravity(Gravity.CENTER_VERTICAL);
        ImageView brandIcon = new ImageView(this);
        brandIcon.setContentDescription("Logo TerraNova");
        brandIcon.setImageResource(R.drawable.terranova_app_icon);
        brandLine.addView(brandIcon, new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        TextView wordmark = heading("TerraNova", 27, GOLD, true);
        wordmark.setTypeface(Typeface.create("serif", Typeface.BOLD));
        brandText.addView(wordmark);
        brandText.addView(heading("Recherche de terrains", 12, MUTED, false));
        LinearLayout.LayoutParams wordmarkSize = new LinearLayout.LayoutParams(0,-2,1f);
        wordmarkSize.setMargins(dp(10),0,0,0);
        brandLine.addView(brandText, wordmarkSize);
        masthead.addView(brandLine);
        place(main, masthead, 0, 7);

        LinearLayout search = section(LIGHT, 16, BORDER);
        search.addView(heading("RECHERCHER UNE COMMUNE", 11, GOLD, true));
        LinearLayout fields = row();
        fields.setGravity(Gravity.CENTER_VERTICAL);
        cityInput = new AutoCompleteTextView(this);
        cityInput.setThreshold(2);
        cityInput.setAdapter(new ArrayAdapter<String>(this,
            android.R.layout.simple_dropdown_item_1line, communeNames));
        cityInput.setSingleLine(true);
        cityInput.setTextSize(15);
        cityInput.setTextColor(TEXT);
        cityInput.setHintTextColor(MUTED);
        cityInput.setHint("Commune · ex. Monnaie");
        cityInput.setPadding(dp(11), 0, dp(9), 0);
        cityInput.setBackground(rounded(SOFT, 10, BORDER));
        LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(0, dp(49), 1.45f);
        c.setMargins(0, 0, dp(7), 0);
        fields.addView(cityInput, c);
        siteSpinner = new Spinner(this);
        ArrayAdapter<String> choices = new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_dropdown_item, SITES) {
            @Override public View getView(int position, View old, ViewGroup parent) {
                TextView title = (TextView) super.getView(position, old, parent);
                title.setTextColor(TEXT);
                title.setTextSize(13);
                return title;
            }
        };
        siteSpinner.setAdapter(choices);
        siteSpinner.setPadding(dp(8), 0, dp(5), 0);
        siteSpinner.setBackground(rounded(SOFT, 10, BORDER));
        fields.addView(siteSpinner, new LinearLayout.LayoutParams(0, dp(49), 1f));
        place(search, fields, 7, 8);

        LinearLayout actions = row();
        Button openSite = action("Rechercher ↗", true);
        openSite.setOnClickListener(v -> openSelectedSite());
        addControl(actions, openSite, 1.15f);
        Button importSite = action("Importer la page", false);
        importSite.setOnClickListener(v -> importVisiblePage());
        addControl(actions, importSite, 1.1f);
        place(search, actions, 0, 6);

        LinearLayout secondary = row();
        Button nextSite = action("Site suivant →", true);
        nextSite.setTextSize(11);
        nextSite.setOnClickListener(v -> openNextSite());
        addControl(secondary, nextSite, 1f);
        Button reset = action("Effacer la recherche", false);
        reset.setTextSize(11);
        reset.setOnClickListener(v -> reset());
        addControl(secondary, reset, 1f);
        place(search, secondary, 0, 1);
        place(main, search, 0, 7);

        LinearLayout tabbar = row();
        tabbar.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabbar.setBackground(rounded(SOFT, 15, BORDER));
        filteredTab = action("Annonces triées", true);
        filteredTab.setTextSize(11);
        filteredTab.setOnClickListener(v -> {
            chooseTab(filteredTab);
            displayListings(true);
        });
        addControl(tabbar, filteredTab, 1f);
        allTab = action("Toutes les annonces", false);
        allTab.setTextSize(11);
        allTab.setOnClickListener(v -> {
            chooseTab(allTab);
            displayListings(false);
        });
        addControl(tabbar, allTab, 1.15f);
        lotisseursTab = action("Lotisseurs", false);
        lotisseursTab.setTextSize(11);
        lotisseursTab.setOnClickListener(v -> {
            chooseTab(lotisseursTab);
            showLotisseursOnLoad = true;
            browser.loadUrl(TERRAIN_SCOPE);
            showBrowser();
            status.setText("Ouverture du catalogue des 18 lotisseurs…");
        });
        addControl(tabbar, lotisseursTab, .8f);
        place(main, tabbar, 0, 7);

        status = heading("Prêt pour une recherche de terrains.", 12, TEXT, false);
        status.setPadding(dp(12), dp(9), dp(12), dp(9));
        status.setBackground(rounded(LIGHT, 12, BORDER));
        place(main, status, 0, 8);

        browser = new WebView(this);
        browser.setBackgroundColor(Color.WHITE);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (Exception ignored) {}
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (showLotisseursOnLoad && url.startsWith(TERRAIN_SCOPE)) {
                    showLotisseursOnLoad = false;
                    view.evaluateJavascript(
                        "(function(){var b=document.getElementById('lotisseursTop');" +
                        "var d=document.getElementById('developerAccordion');" +
                        "if(b && d){if(!d.open)b.click();" +
                        "b.scrollIntoView({block:'center'});return true;}return false;})()",
                        ignored -> status.setText("Catalogue des lotisseurs ouvert dans TerrainScope."));
                    return;
                }
                if (showingBrowser && activeCity.length() > 0)
                    status.setText("Site chargé pour " + activeCity +
                        ". Vérifie la catégorie Terrain et la commune avant d'importer.");
            }
        });
        browser.setBackground(rounded(LIGHT, 13, BORDER));
        main.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1f));

        results = new ScrollView(this);
        cards = new LinearLayout(this);
        cards.setPadding(dp(3), dp(10), dp(3), dp(10));
        cards.setOrientation(LinearLayout.VERTICAL);
        results.addView(cards);
        main.addView(results, new LinearLayout.LayoutParams(-1, 0, 1f));
        results.setVisibility(View.GONE);
        setContentView(main);
    }

    private void openSelectedSite() {
        String city = cleanCity();
        if (city.isEmpty()) {
            status.setText("Saisis d’abord la commune exacte.");
            cityInput.requestFocus();
            return;
        }
        String site = String.valueOf(siteSpinner.getSelectedItem());
        SearchUrls searches = new SearchUrls(postalCodes, selogerPaths);
        String url = searches.urlFor(site, city);
        if (url == null) {
            status.setText("Aucun accès direct à des terrains confirmé sur " + site + " à " +
                city + ". Je n'ouvre pas des milliers d'annonces nationales : " +
                "source indisponible tant que le filtrage géographique n'est pas vérifié.");
            return;
        }
        if (!activeCity.isEmpty() && !ListingEngine.normalize(activeCity)
                .equals(ListingEngine.normalize(city))) {
            imported.clear();
            sourcesVisited.clear();
        }
        activeCity = city;
        showLotisseursOnLoad = false;
        showBrowser();
        browser.loadUrl(url);
        status.setText("Recherche TERRAIN pour " + city + " (" +
            postalCodes.get(ListingEngine.normalize(city)) + ") sur " + site +
            ". Vérifie que le site affiche uniquement la commune et les terrains.");
    }

    private void openNextSite() {
        String city = cleanCity();
        if (city.isEmpty()) {
            status.setText("Sélectionne une commune avant de passer au site suivant.");
            return;
        }
        SearchUrls searches = new SearchUrls(postalCodes, selogerPaths);
        int start = siteSpinner.getSelectedItemPosition();
        for (int offset = 1; offset <= SITES.length; offset++) {
            int next = (start + offset) % SITES.length;
            if (searches.urlFor(SITES[next], city) != null) {
                siteSpinner.setSelection(next);
                openSelectedSite();
                return;
            }
        }
        status.setText("Aucune nouvelle source avec un lien Terrain confirmé pour cette commune.");
    }

    private void importVisiblePage() {
        String city = cleanCity();
        if (city.isEmpty() || !ListingEngine.normalize(city)
                .equals(ListingEngine.normalize(activeCity))) {
            status.setText("Commune changée : ouvre d’abord le bon site avec les nouveaux critères.");
            return;
        }
        Uri current = Uri.parse(browser.getUrl() == null ? "" : browser.getUrl());
        String host = current.getHost() == null ? "" : current.getHost().toLowerCase(Locale.ROOT);
        if (!approvedHost(host)) {
            status.setText("Ouvre d’abord Le Bon Coin, SeLoger, Bien’ici, iad ou les notaires." +
                " L’application ne peut pas importer cette page.");
            return;
        }
        try {
            String script = readAsset("extract-visible.js");
            // Pas de JavascriptInterface exposée aux sites tiers : seule la WebView
            // renvoie le JSON à l’application après cet appui explicite.
            String payload = "window.__terrainScopeRequestedCity=" + JSONObject.quote(city) + ";\n" +
                "window.__terrainScopeRequestedPostal=" +
                JSONObject.quote(postalCodes.getOrDefault(ListingEngine.normalize(city), "")) + ";\n" + script;
            status.setText("Lecture des annonces de la page actuellement affichée…");
            browser.evaluateJavascript(payload, reply -> receiveImport(reply, host));
        } catch (Exception error) {
            status.setText("Lecture indisponible : " + error.getMessage());
        }
    }

    private void receiveImport(String encoded, String sourceHost) {
        try {
            Object decoded = new JSONTokener(encoded).nextValue();
            if (!(decoded instanceof String)) throw new IllegalArgumentException("Pas de données retournées");
            JSONObject result = new JSONObject((String) decoded);
            if (!ListingEngine.normalize(result.optString("requestedCity"))
                .equals(ListingEngine.normalize(activeCity))) {
                throw new IllegalStateException("Cette importation concerne une autre commune ; ouvre le site de nouveau.");
            }
            JSONArray listings = result.optJSONArray("listings");
            if (listings == null) throw new IllegalArgumentException("Format de données non reconnu");
            int added = 0;
            for (int i = 0; i < listings.length(); i++) {
                JSONObject item = listings.optJSONObject(i);
                if (item == null) continue;
                String url = item.optString("url", "");
                Uri parsed = Uri.parse(url);
                if (!approvedHost(parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.ROOT))) continue;
                ListingEngine.Listing listing = new ListingEngine.Listing(
                    item.optString("source", sourceHost), url, item.optString("title", ""),
                    item.optString("city", cleanCity()), item.optString("seller", ""),
                    item.optString("description", ""), item.optString("address", ""),
                    item.optString("lot", ""), item.optLong("priceEuros", 0),
                    item.optDouble("areaM2", 0), item.optBoolean("cityConfirmed", false));
                String key = url.replaceFirst("#.*$", "").replaceFirst("/$", "");
                if (!listing.title.isEmpty() && !imported.containsKey(key)) {
                    imported.put(key, listing);
                    added++;
                }
            }
            String checkedSource = result.optString("source", sourceHost);
            sourcesVisited.add(checkedSource);
            int displayedCount = result.isNull("declaredCount") ? -1 :
                result.optInt("declaredCount", -1);
            int extractedCount = result.optInt("retrieved", 0);
            String total = displayedCount > 0
                ? " sur " + displayedCount + " annonce(s) affichée(s) par le site"
                : " détectée(s) sur cette page";
            String warning = displayedCount > extractedCount
                ? " · Import PARTIEL : ne pas confondre avec le total des terrains disponibles."
                : " · Vérifie le tri dans les onglets.";
            status.setText(extractedCount + " fiche(s) lue(s)" + total +
                ", " + added + " nouvelle(s), " + imported.size() +
                " cumulée(s). " + sourcesVisited.size() + " source(s) consultée(s)." + warning);
        } catch (Exception error) {
            status.setText("Cette page n’a pas pu être lue. Ce n’est PAS « zéro terrain disponible ». " +
                "Cause : " + error.getMessage());
        }
    }

    private void displayListings(boolean filtered) {
        List<ListingEngine.Result> classified =
            ListingEngine.classify(new ArrayList<>(imported.values()));
        List<ListingEngine.Result> current = filtered
            ? ListingEngine.filtered(classified) : classified;
        showResults();
        cards.removeAllViews();
        Map<ListingEngine.State, Integer> counts = ListingEngine.counts(classified);
        TextView summary = text((filtered ? "Annonces triées" : "Toutes les annonces") +
            " : " + current.size() + " affichée(s) / " + imported.size() +
            " récupérée(s)\n" + counts.get(ListingEngine.State.BUILDER) +
            " constructeur(s) écarté(s), " + counts.get(ListingEngine.State.DUPLICATE) +
            " doublon(s) regroupé(s), " + counts.get(ListingEngine.State.TO_VERIFY) +
            " annonce(s) à vérifier · priorité : Le Bon Coin → SeLoger → Bien’ici.", 13, true);
        summary.setTextColor(GOLD);
        cards.addView(summary);
        TextView coverage = text("Sources consultées : " +
            (sourcesVisited.isEmpty() ? "aucune importée" : String.join(", ", sourcesVisited)) +
            " · Les portails non consultés ne valent pas « zéro annonce ».", 12, false);
        coverage.setTextColor(MUTED);
        cards.addView(coverage);
        if (current.isEmpty()) {
            TextView empty = text(imported.isEmpty()
                ? "Aucune annonce importée. Ouvre un site, laisse les résultats charger puis " +
                  "appuie sur « Importer la page ». Ce n’est pas le nombre de terrains en vente."
                : "Aucune annonce retenue dans les pages importées. Ouvre « Toutes les annonces »" +
                  " pour contrôler les exclusions.", 13, false);
            cards.addView(empty);
            return;
        }
        for (ListingEngine.Result record : current) addCard(record);
    }

    private void addCard(ListingEngine.Result record) {
        ListingEngine.Listing item = record.listing;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(11), dp(12), dp(12));
        panel.setBackground(rounded(LIGHT, 14, BORDER));
        panel.setElevation(dp(2));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.setMargins(0, dp(8), 0, dp(4));
        cards.addView(panel, layout);
        String statusText = record.state == ListingEngine.State.BUILDER ? " · CONSTRUCTEUR" :
            record.state == ListingEngine.State.DUPLICATE ? " · DOUBLON" :
            record.state == ListingEngine.State.TO_VERIFY ? " · COMMUNE À VÉRIFIER" :
            ListingEngine.isLotisseur(item) ? " · LOTISSEUR" : "";
        TextView origin = text(item.source + statusText, 12, true);
        origin.setTextColor(record.state == ListingEngine.State.BUILDER ? Color.rgb(235, 136, 130) : GOLD);
        panel.addView(origin);
        panel.addView(text(item.title, 15, true));
        String price = item.priceEuros > 0 ? String.format(Locale.FRANCE,"%,d €",item.priceEuros) : "Prix à vérifier";
        String area = item.areaM2 > 0 ? String.format(Locale.FRANCE,"%,.0f m²",item.areaM2) : "Surface à vérifier";
        panel.addView(text(item.city + " · " + area, 13, false));
        panel.addView(heading(price, 20, GOLD, true));
        if (!item.seller.isEmpty()) panel.addView(text("Annonceur : " + item.seller, 12, false));
        if (!record.reason.isEmpty()) panel.addView(text(record.reason, 12, false));
        if (!record.otherSources.isEmpty())
            panel.addView(text("Autres sites : " + String.join(", ", record.otherSources), 12, false));
        Button open = action("Voir l’annonce d’origine ↗", true);
        open.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.url))); }
            catch (Exception exception) { status.setText("Lien inaccessible : " + item.url); }
        });
        panel.addView(open);
    }

    private void reset() {
        imported.clear();
        sourcesVisited.clear();
        activeCity = "";
        browser.loadUrl("about:blank");
        showBrowser();
        status.setText("Nouvelle recherche vide. Aucune annonce précédente conservée.");
    }

    private void showBrowser() {
        showingBrowser = true;
        browser.setVisibility(View.VISIBLE);
        results.setVisibility(View.GONE);
    }
    private void showResults() {
        showingBrowser = false;
        browser.setVisibility(View.GONE);
        results.setVisibility(View.VISIBLE);
    }
    private boolean approvedHost(String host) {
        for (String name : new String[]{"leboncoin.fr","seloger.com","bienici.com",
                "iadfrance.fr","immobilier.notaires.fr","immonot.com"}) {
            if (host.equals(name) || host.endsWith("." + name)) return true;
        }
        return false;
    }
    private String cleanCity() { return cityInput.getText().toString().trim(); }
    private static String slug(String city) {
        return ListingEngine.normalize(city).replace(' ', '-');
    }
    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }
    private void loadCommuneIndex() {
        try {
            JSONArray array = new JSONArray(readAsset("communes-37.json"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject city = array.getJSONObject(i);
                String name = city.optString("nom");
                if (!name.isEmpty()) communeNames.add(name);
                JSONArray postals = city.optJSONArray("codesPostaux");
                if (postals != null && postals.length() > 0)
                    postalCodes.put(ListingEngine.normalize(city.optString("nom")), postals.getString(0));
            }
        } catch (Exception ignored) {}
    }
    private void loadSeLogerPaths() {
        String[] mapping = {
            "notre dame d oe|notre-dame-d-oe-37390/ad08fr14697",
            "joue les tours|joue-les-tours-37300/ad08fr14648",
            "chambray les tours|chambray-les-tours-37170/ad08fr14576",
            "tours|tours-37000/ad08fr14783",
            "monnaie|monnaie-37380/ad08fr14679",
            "azay sur cher|azay-sur-cher-37270/ad08fr14542",
            "fondettes|fondettes-37230/ad08fr14635",
            "montlouis sur loire|montlouis-sur-loire-37270/ad08fr14682",
            "azay le rideau|azay-le-rideau-37190/ad08fr14541",
            "saint pierre des corps|saint-pierre-des-corps-37700/ad08fr14757",
            "montbazon|montbazon-37250/ad08fr14680",
            "chinon|chinon-37500/ad08fr14598",
            "savonnieres|savonnieres-37510/ad08fr14765",
            "saint cyr sur loire|saint-cyr-sur-loire-37540/ad08fr14739",
            "veigne|veigne-37250/ad08fr14788",
            "saint avertin|saint-avertin-37550/ad08fr14733",
            "loches|loches-37600/ad08fr14658",
            "amboise|amboise-37400/ad08fr14530",
            "ballan mire|ballan-mire-37510/ad08fr14544"
        };
        for (String entry : mapping) {
            String[] parts = entry.split("\\|", 2);
            selogerPaths.put(parts[0], parts[1]);
        }
    }
    private String readAsset(String path) throws Exception {
        try (InputStream in = getAssets().open(path); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) >= 0;) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }
    private void addButton(LinearLayout row, String label, Runnable run, float weight) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setText(label);
        b.setOnClickListener(v -> run.run());
        row.addView(b, new LinearLayout.LayoutParams(0, dp(48), weight));
    }
    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(TEXT);
        t.setPadding(dp(3), dp(3), dp(3), dp(5));
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
    @Override public void onBackPressed() {
        if (showingBrowser && browser.canGoBack()) browser.goBack();
        else if (!showingBrowser) showBrowser();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        browser.destroy();
        super.onDestroy();
    }
}
