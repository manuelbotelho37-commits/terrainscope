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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

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
    private static final int GREEN = Color.rgb(40, 91, 66);
    private static final int PALE = Color.rgb(223, 244, 210);
    private final Map<String, String> postalCodes = new HashMap<>();
    // Codes de localité SeLoger vérifiés dans la version web existante.
    private final Map<String, String> selogerPaths = new HashMap<>();
    private final LinkedHashMap<String, ListingEngine.Listing> imported = new LinkedHashMap<>();

    private EditText cityInput;
    private Spinner siteSpinner;
    private WebView browser;
    private LinearLayout main;
    private LinearLayout cards;
    private ScrollView results;
    private TextView status;
    private boolean showingBrowser = true;
    private String activeCity = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        loadCommuneIndex();
        loadSeLogerPaths();
        buildScreen();
        status.setText("Choisis une commune, ouvre un site, puis importe les annonces affichées.");
    }


    private static final int DARK = Color.rgb(18, 55, 46);
    private static final int LIGHT = Color.rgb(246, 249, 246);
    private static final int SOFT = Color.rgb(231, 240, 232);
    private static final int LIME = Color.rgb(211, 243, 163);
    private static final int TEXT = Color.rgb(34, 60, 50);
    private static final int MUTED = Color.rgb(91, 117, 105);
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
        b.setTextColor(primary ? DARK : GREEN);
        b.setBackground(rounded(primary ? LIME : Color.WHITE, 11,
            primary ? Color.TRANSPARENT : Color.rgb(208, 225, 212)));
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
            b.setTextColor(active ? DARK : MUTED);
            b.setBackground(rounded(active ? LIME : SOFT, 11, Color.TRANSPARENT));
        }
    }

    private void buildScreen() {
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);
        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(12), dp(9), dp(12), dp(8));
        main.setBackgroundColor(LIGHT);

        LinearLayout masthead = section(DARK, 18, Color.TRANSPARENT);
        masthead.addView(heading("TerrainScope", 24, Color.WHITE, true));
        TextView kicker = heading("RECHERCHE DE TERRAINS  ·  INDRE-ET-LOIRE", 10, LIME, true);
        place(masthead, kicker, 1, 2);
        masthead.addView(heading("Application Android · version d'essai", 11,
            Color.rgb(203, 220, 211), false));
        place(main, masthead, 0, 7);

        LinearLayout search = section(Color.WHITE, 16, Color.rgb(223, 235, 224));
        search.addView(heading("RECHERCHER DES TERRAINS", 11, GREEN, true));
        LinearLayout fields = row();
        fields.setGravity(Gravity.CENTER_VERTICAL);
        cityInput = new EditText(this);
        cityInput.setSingleLine(true);
        cityInput.setTextSize(15);
        cityInput.setTextColor(TEXT);
        cityInput.setHintTextColor(MUTED);
        cityInput.setHint("Commune · ex. Monnaie");
        cityInput.setPadding(dp(11), 0, dp(9), 0);
        cityInput.setBackground(rounded(LIGHT, 10, Color.rgb(207, 225, 211)));
        LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(0, dp(49), 1.45f);
        c.setMargins(0, 0, dp(7), 0);
        fields.addView(cityInput, c);
        siteSpinner = new Spinner(this);
        ArrayAdapter<String> choices = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_dropdown_item, SITES);
        siteSpinner.setAdapter(choices);
        siteSpinner.setPadding(dp(8), 0, dp(5), 0);
        siteSpinner.setBackground(rounded(LIGHT, 10, Color.rgb(207, 225, 211)));
        fields.addView(siteSpinner, new LinearLayout.LayoutParams(0, dp(49), 1f));
        place(search, fields, 7, 8);

        LinearLayout actions = row();
        Button openSite = action("Rechercher sur le site ↗", true);
        openSite.setOnClickListener(v -> openSelectedSite());
        addControl(actions, openSite, 1.15f);
        Button importSite = action("Récupérer les annonces", false);
        importSite.setOnClickListener(v -> importVisiblePage());
        addControl(actions, importSite, 1.1f);
        place(search, actions, 0, 6);

        Button reset = action("Nouvelle commune / vider la recherche", false);
        reset.setTextSize(11);
        reset.setMinimumHeight(dp(39));
        reset.setOnClickListener(v -> reset());
        place(search, reset, 0, 1);
        place(main, search, 0, 7);

        LinearLayout tabbar = row();
        tabbar.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabbar.setBackground(rounded(SOFT, 15, Color.TRANSPARENT));
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
            browser.loadUrl(TERRAIN_SCOPE);
            showBrowser();
            status.setText("Les lotisseurs restent accessibles dans TerrainScope existant.");
        });
        addControl(tabbar, lotisseursTab, .8f);
        place(main, tabbar, 0, 7);

        status = heading("Prêt pour une recherche de terrains.", 12, TEXT, false);
        status.setPadding(dp(12), dp(9), dp(12), dp(9));
        status.setBackground(rounded(Color.rgb(237, 247, 233), 12,
            Color.rgb(212, 230, 209)));
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
                if (showingBrowser && activeCity.length() > 0)
                    status.setText("Site chargé pour " + activeCity +
                        ". Vérifie la catégorie Terrain et la commune avant d'importer.");
            }
        });
        browser.setBackground(rounded(Color.WHITE, 13, Color.rgb(225, 233, 225)));
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
        }
        activeCity = city;
        showBrowser();
        browser.loadUrl(url);
        status.setText("Recherche TERRAIN pour " + city + " (" +
            postalCodes.get(ListingEngine.normalize(city)) + ") sur " + site +
            ". Vérifie que le site affiche uniquement la commune et les terrains.");
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
            String payload = "window.__terrainScopeRequestedCity=" + JSONObject.quote(city) + ";\n" + script;
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
            status.setText(added + " annonce(s) ajoutée(s), " + imported.size() +
                " annonces récupérées au total. " + result.optInt("retrieved", 0) +
                " fiches détectées sur la page, " + result.optInt("scannedCards", 0) +
                " cartes examinées. Si des annonces manquent, essaie la page suivante.");
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
            " doublon(s) regroupé(s) · priorité : Le Bon Coin → SeLoger → Bien’ici.", 13, true);
        summary.setTextColor(GREEN);
        cards.addView(summary);
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
        panel.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.setMargins(0, dp(8), 0, dp(4));
        cards.addView(panel, layout);
        String statusText = record.state == ListingEngine.State.BUILDER ? " · CONSTRUCTEUR" :
            record.state == ListingEngine.State.DUPLICATE ? " · DOUBLON" :
            ListingEngine.isLotisseur(item) ? " · LOTISSEUR" : "";
        TextView origin = text(item.source + statusText, 12, true);
        origin.setTextColor(record.state == ListingEngine.State.BUILDER ? Color.rgb(162, 51, 51) : GREEN);
        panel.addView(origin);
        panel.addView(text(item.title, 15, true));
        String price = item.priceEuros > 0 ? item.priceEuros + " €" : "Prix à vérifier";
        String area = item.areaM2 > 0 ? item.areaM2 + " m²" : "Surface à vérifier";
        panel.addView(text(item.city + " · " + area + " · " + price, 13, false));
        if (!item.seller.isEmpty()) panel.addView(text("Annonceur : " + item.seller, 12, false));
        if (!record.reason.isEmpty()) panel.addView(text(record.reason, 12, false));
        if (!record.otherSources.isEmpty())
            panel.addView(text("Autres sites : " + String.join(", ", record.otherSources), 12, false));
        Button open = new Button(this);
        open.setAllCaps(false);
        open.setText("Voir l’annonce d’origine ↗");
        open.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.url))); }
            catch (Exception exception) { status.setText("Lien inaccessible : " + item.url); }
        });
        panel.addView(open);
    }

    private void reset() {
        imported.clear();
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
        t.setTextColor(Color.rgb(37, 61, 48));
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
