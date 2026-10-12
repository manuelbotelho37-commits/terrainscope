package fr.terrainscope.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.MultiAutoCompleteTextView;
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
    private final LinkedHashSet<String> sourcesOpened = new LinkedHashSet<>();

    private MultiAutoCompleteTextView cityInput;
    private final ArrayList<String> communeNames = new ArrayList<>();
    private Spinner siteSpinner;
    private WebView browser;
    private LinearLayout main;
    private LinearLayout cards;
    private ScrollView results;
    private TextView status;
    private boolean showingBrowser = true;
    private boolean pageLoading = false;
    private boolean webError = false;
    private String activeCity = "";
    private String batchSignature = "";
    private final ArrayList<String> activeCities = new ArrayList<>();
    private int cityPosition = 0;
    private final Handler scanHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<String[]> scanTasks = new ArrayList<>();
    private final LinkedHashSet<String> scanPageUrls = new LinkedHashSet<>();
    private int scanGeneration = 0;
    private int scanTaskIndex = 0;
    private int scanPageNumber = 0;
    private int scanLimited = 0;
    private int scanBlocked = 0;
    private boolean scanning = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        loadCommuneIndex();
        loadSeLogerPaths();
        buildScreen();
        chooseTab(filteredTab);
        displayListings(true);
        status.setText("Entrez vos communes et lancez une recherche multi-sites.");
    }


    private static final int DARK = Color.rgb(11, 22, 32);
    private static final int LIGHT = Color.rgb(16, 29, 41);
    private static final int SOFT = Color.rgb(43, 54, 66);
    private static final int LIME = Color.rgb(212, 175, 55);
    private static final int TEXT = Color.rgb(242, 235, 218);
    private static final int MUTED = Color.rgb(175, 184, 195);
    private static final int GOLD = Color.rgb(212, 175, 55);
    private static final int BORDER = Color.rgb(72, 77, 78);
    private static final int GOLD_SOFT = Color.rgb(148, 117, 47);
    private static final int PANEL = Color.rgb(20, 34, 47);
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
        main.setBackgroundColor(DARK);

        LinearLayout masthead = section(PANEL, 20, GOLD_SOFT);
        GradientDrawable heroBackground = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(29, 44, 58), DARK});
        heroBackground.setCornerRadius(dp(20));
        heroBackground.setStroke(dp(1), GOLD_SOFT);
        masthead.setBackground(heroBackground);
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
        brandText.addView(heading("L'EXCELLENCE FONCIÈRE  ·  TOURAINE", 10, MUTED, true));
        brandText.addView(heading("Le bon terrain, avant les autres.", 12, TEXT, false));
        LinearLayout.LayoutParams wordmarkSize = new LinearLayout.LayoutParams(0,-2,1f);
        wordmarkSize.setMargins(dp(10),0,0,0);
        brandLine.addView(brandText, wordmarkSize);
        masthead.addView(brandLine);
        place(main, masthead, 0, 7);

        LinearLayout search = section(PANEL, 18, BORDER);
        search.addView(heading("OÙ SOUHAITEZ-VOUS CONSTRUIRE ?", 12, GOLD, true));
        search.addView(heading("Recherchez une ou plusieurs communes du 37.", 11, MUTED, false));
        LinearLayout fields = row();
        fields.setGravity(Gravity.CENTER_VERTICAL);
        cityInput = new MultiAutoCompleteTextView(this);
        cityInput.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
        cityInput.setThreshold(2);
        cityInput.setAdapter(new ArrayAdapter<String>(this,
            android.R.layout.simple_dropdown_item_1line, communeNames));
        cityInput.setSingleLine(true);
        cityInput.setTextSize(15);
        cityInput.setTextColor(TEXT);
        cityInput.setHintTextColor(MUTED);
        cityInput.setHint("Monnaie, Tours, Chambray-lès-Tours");
        cityInput.setPadding(dp(11), 0, dp(9), 0);
        cityInput.setBackground(rounded(SOFT, 10, BORDER));
        LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(0, dp(53), 1f);
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
        siteSpinner.setVisibility(View.GONE);
        fields.addView(siteSpinner, new LinearLayout.LayoutParams(0, dp(1), 0f));
        place(search, fields, 7, 8);

        Button searchAll = action("⌕  RECHERCHER LES TERRAINS", true);
        searchAll.setTextSize(14);
        searchAll.setOnClickListener(v -> startAutomatedSearch());
        place(search, searchAll, 0, 4);
        LinearLayout helper = row();
        TextView sources = heading("LE BON COIN  ·  SELOGER  ·  BIEN'ICI  ·  IAD  ·  NOTAIRES",
            10, MUTED, true);
        helper.addView(sources, new LinearLayout.LayoutParams(0, -2, 1f));
        Button reset = action("Effacer", false);
        reset.setTextSize(10);
        reset.setMinimumHeight(dp(35));
        reset.setOnClickListener(v -> reset());
        helper.addView(reset, new LinearLayout.LayoutParams(dp(84), dp(36)));
        place(search, helper, 0, 1);
        place(main, search, 0, 7);

        LinearLayout tabbar = row();
        tabbar.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabbar.setBackground(rounded(PANEL, 15, GOLD_SOFT));
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
        lotisseursTab = action("🏘 18 lotisseurs", false);
        lotisseursTab.setTextSize(11);
        lotisseursTab.setOnClickListener(v -> {
            chooseTab(lotisseursTab);
            displayDevelopers();
        });
        addControl(tabbar, lotisseursTab, 1.12f);
        place(main, tabbar, 0, 7);

        status = heading("Prêt pour une recherche de terrains.", 12, TEXT, false);
        status.setPadding(dp(12), dp(9), dp(12), dp(9));
        status.setBackground(rounded(PANEL, 12, BORDER));
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
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                pageLoading = true;
                webError = false;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (Exception ignored) {}
                return true;
            }
            @Override public void onReceivedError(WebView view,
                    WebResourceRequest request, WebResourceError error) {
                if (!request.isForMainFrame()) return;
                pageLoading = false;
                webError = true;
                status.setText("Ce site n'a pas pu charger la page. Vérifie la connexion, " +
                    "ou essaie le site suivant. Aucun résultat ne peut être déduit.");
            }
            @Override public void onReceivedHttpError(WebView view,
                    WebResourceRequest request, WebResourceResponse response) {
                if (!request.isForMainFrame() || response.getStatusCode() < 400) return;
                webError = true;
                pageLoading = false;
                status.setText("Accès au site refusé ou indisponible (HTTP " +
                    response.getStatusCode() + "). Cela ne signifie PAS zéro terrain.");
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageLoading = false;
                if (webError) {
                    if (scanning) scanPageLoaded();
                    return;
                }
                if (scanning) {
                    scanPageLoaded();
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

    private boolean prepareBatch() {
        String raw = cityInput.getText().toString().trim();
        if (!raw.isEmpty() && raw.equals(batchSignature) && !activeCities.isEmpty()) return true;
        if (raw.isEmpty()) {
            status.setText("Saisis une ou plusieurs communes, séparées par une virgule.");
            return false;
        }
        ArrayList<String> cities = new ArrayList<>();
        LinkedHashSet<String> known = new LinkedHashSet<>();
        for (String token : raw.split("[,;]")) {
            String key = ListingEngine.normalize(token);
            if (key.isEmpty()) continue;
            if (!postalCodes.containsKey(key)) {
                status.setText("Commune inconnue dans le 37 : « " + token.trim() +
                    " ». Vérifie son orthographe.");
                return false;
            }
            if (known.add(key)) {
                String official = token.trim();
                for (String candidate : communeNames) {
                    if (ListingEngine.normalize(candidate).equals(key)) {
                        official = candidate;
                        break;
                    }
                }
                cities.add(official);
            }
        }
        if (cities.isEmpty()) {
            status.setText("Ajoute au moins une commune de l'Indre-et-Loire.");
            return false;
        }
        // Seule une nouvelle saisie explicite efface la recherche précédente.
        // Le passage d'une commune à l'autre dans le même lot ne l'efface pas.
        imported.clear();
        sourcesVisited.clear();
        sourcesOpened.clear();
        activeCities.clear();
        activeCities.addAll(cities);
        cityPosition = 0;
        activeCity = cities.get(0);
        batchSignature = raw;
        return true;
    }

    /** Recherche tous les portails compatibles sans clic d'import.
     * Aucun contournement d'anti-robot : un site bloqué est signalé.
     * Les pages supplémentaires sont parcourues dans une limite contrôlée.
     */
    private void startAutomatedSearch() {
        stopScan();
        batchSignature = "";
        if (!prepareBatch()) return;
        scanTasks.clear();
        scanPageUrls.clear();
        scanTaskIndex = 0;
        scanPageNumber = 0;
        scanLimited = 0;
        scanBlocked = 0;
        SearchUrls urls = new SearchUrls(postalCodes, selogerPaths);
        for (String city : activeCities) {
            for (String site : SITES) {
                String url = urls.urlFor(site, city);
                if (url != null) scanTasks.add(new String[]{site, city, url});
                else sourcesVisited.add(site + " · " + city + " (non vérifiable)");
            }
        }
        chooseTab(filteredTab);
        displayListings(true);
        if (scanTasks.isEmpty()) {
            status.setText("Aucune source avec URL précise disponible. Rien n'a été présenté comme zéro terrain.");
            return;
        }
        scanning = true;
        int run = scanGeneration;
        status.setText("Recherche automatique : 0/" + scanTasks.size() +
            " portails. Les résultats arrivent au fil de la consultation.");
        startNextScanTask(run);
    }

    private void stopScan() {
        scanning = false;
        scanGeneration++;
        scanHandler.removeCallbacksAndMessages(null);
    }

    private void startNextScanTask(int run) {
        if (!scanning || run != scanGeneration) return;
        if (scanTaskIndex >= scanTasks.size()) {
            scanning = false;
            boolean found = !ListingEngine.filtered(ListingEngine.classify(
                new ArrayList<>(imported.values()))).isEmpty();
            chooseTab(found ? filteredTab : allTab);
            displayListings(found);
            status.setText("Recherche terminée : " + imported.size() + " annonces récupérées sur " +
                scanTasks.size() + " recherches ciblées ; " + scanBlocked +
                " source(s) en échec, " + scanLimited +
                " parcours limité(s). Les sites peuvent afficher d'autres annonces.");
            return;
        }
        String[] task = scanTasks.get(scanTaskIndex);
        activeCity = task[1];
        scanPageNumber = 1;
        scanPageUrls.clear();
        sourcesOpened.add(task[0] + " · " + task[1]);
        scanPageUrls.add(task[2]);
        status.setText("Recherche " + (scanTaskIndex+1) + "/" + scanTasks.size() +
            " · " + task[0] + " · " + task[1] + " · " + imported.size() + " annonces reçues");
        pageLoading = true;
        webError = false;
        browser.loadUrl(task[2]);
    }

    private void scanPageLoaded() {
        final int run = scanGeneration;
        final int taskIndex = scanTaskIndex;
        scanHandler.postDelayed(() -> {
            if (!scanning || run != scanGeneration || taskIndex != scanTaskIndex) return;
            if (webError) {
                scanBlocked++;
                scanTaskIndex++;
                startNextScanTask(run);
                return;
            }
            // Amorcer le chargement paresseux des cartes avant de lire le DOM.
            browser.evaluateJavascript(
                "(function(){window.scrollTo(0, document.body.scrollHeight);return true})()",
                ignored -> scanHandler.postDelayed(() -> collectScanPage(run, taskIndex), 850));
        }, 1200);
    }

    private void collectScanPage(int run, int taskIndex) {
        if (!scanning || run != scanGeneration || taskIndex != scanTaskIndex) return;
        if (webError || browser.getProgress() < 90) {
            scanBlocked++;
            scanTaskIndex++;
            startNextScanTask(run);
            return;
        }
        String host = Uri.parse(browser.getUrl() == null ? "" : browser.getUrl()).getHost();
        if (host == null || !approvedHost(host.toLowerCase(Locale.ROOT))) {
            scanBlocked++;
            scanTaskIndex++;
            startNextScanTask(run);
            return;
        }
        try {
            String payload = "window.__terrainScopeRequestedCity=" + JSONObject.quote(activeCity) + ";\n" +
                "window.__terrainScopeRequestedPostal=" +
                JSONObject.quote(postalCodes.getOrDefault(ListingEngine.normalize(activeCity), "")) + ";\n" +
                "window.__terrainScopeKnownCities=" + new JSONArray(communeNames).toString() +
                ";\n" + readAsset("extract-visible.js");
            browser.evaluateJavascript(payload, reply -> {
                if (!scanning || run != scanGeneration || taskIndex != scanTaskIndex) return;
                String nextUrl = "";
                try {
                    Object decoded = new JSONTokener(reply).nextValue();
                    JSONObject json = new JSONObject((String) decoded);
                    if (json.optBoolean("accessBlocked")) scanBlocked++;
                    else {
                        receiveImport(reply, host.toLowerCase(Locale.ROOT), true);
                        nextUrl = json.optString("nextPageUrl", "");
                    }
                } catch (Exception failed) {
                    scanBlocked++;
                }
                String currentHost = Uri.parse(browser.getUrl() == null ? "" : browser.getUrl()).getHost();
                Uri next = Uri.parse(nextUrl);
                boolean sameHost = currentHost != null && currentHost.equalsIgnoreCase(next.getHost());
                if (sameHost && scanPageNumber < 4 && !scanPageUrls.contains(nextUrl)) {
                    scanPageUrls.add(nextUrl);
                    scanPageNumber++;
                    pageLoading = true;
                    browser.loadUrl(nextUrl);
                    return;
                }
                if (sameHost && scanPageNumber >= 4) scanLimited++;
                scanTaskIndex++;
                startNextScanTask(run);
            });
        } catch (Exception e) {
            scanBlocked++;
            scanTaskIndex++;
            startNextScanTask(run);
        }
    }

    private void openSelectedSite() {
        if (!prepareBatch()) return;
        String city = activeCity;
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
        activeCity = city;
        sourcesOpened.add(site + " · " + city);
        pageLoading = true;
        webError = false;
        showBrowser();
        browser.loadUrl(url);
        status.setText("Recherche TERRAIN pour " + city + " (" +
            postalCodes.get(ListingEngine.normalize(city)) + ") sur " + site +
            ". Vérifie que le site affiche uniquement la commune et les terrains.");
    }

    private void openNextSite() {
        if (!prepareBatch()) return;
        SearchUrls searches = new SearchUrls(postalCodes, selogerPaths);
        int start = siteSpinner.getSelectedItemPosition();
        for (int offset = 1; offset < SITES.length; offset++) {
            int next = (start + offset) % SITES.length;
            String source = SITES[next];
            if (searches.urlFor(source, activeCity) != null &&
                    !sourcesOpened.contains(source + " · " + activeCity)) {
                siteSpinner.setSelection(next);
                openSelectedSite();
                return;
            }
        }
        if (cityPosition + 1 < activeCities.size()) {
            cityPosition++;
            activeCity = activeCities.get(cityPosition);
            siteSpinner.setSelection(0);
            openSelectedSite();
            return;
        }
        status.setText("Fin du parcours : " + activeCities.size() +
            " commune(s), " + sourcesVisited.size() +
            " page(s) importée(s). Les pages non importées restent non vérifiées.");
        boolean hasSelected = !ListingEngine.filtered(
            ListingEngine.classify(new ArrayList<>(imported.values()))).isEmpty();
        chooseTab(hasSelected ? filteredTab : allTab);
        displayListings(hasSelected);
    }

    private void importVisiblePage() {
        if (!cityInput.getText().toString().trim().equals(batchSignature)) {
            status.setText("Communes modifiées : relance Rechercher avant d'importer.");
            return;
        }
        String city = activeCity;
        if (city.isEmpty()) {
            status.setText("Commune non sélectionnée : démarre une recherche.");
            return;
        }
        if (pageLoading || browser.getProgress() < 100) {
            status.setText("Attends que la page se charge complètement avant d'importer.");
            return;
        }
        if (webError) {
            status.setText("La page est inaccessible. Impossible d'importer : ce n'est pas zéro terrain.");
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
                JSONObject.quote(postalCodes.getOrDefault(ListingEngine.normalize(city), "")) + ";\n" +
                "window.__terrainScopeKnownCities=" + new JSONArray(communeNames).toString() + ";\n" + script;
            status.setText("Lecture des annonces de la page actuellement affichée…");
            browser.evaluateJavascript(payload, reply -> receiveImport(reply, host, false));
        } catch (Exception error) {
            status.setText("Lecture indisponible : " + error.getMessage());
        }
    }

    private void receiveImport(String encoded, String sourceHost, boolean automated) {
        try {
            Object decoded = new JSONTokener(encoded).nextValue();
            if (!(decoded instanceof String)) throw new IllegalArgumentException("Pas de données retournées");
            JSONObject result = new JSONObject((String) decoded);
            if (!ListingEngine.normalize(result.optString("requestedCity"))
                .equals(ListingEngine.normalize(activeCity))) {
                throw new IllegalStateException("Cette importation concerne une autre commune ; ouvre le site de nouveau.");
            }
            if (result.optBoolean("accessBlocked", false)) {
                status.setText("Ce site demande une vérification humaine (CAPTCHA) ou refuse l'accès. " +
                    "Aucune annonce n'a pu être récupérée : cela ne signifie PAS zéro terrain. " +
                    "Complète le contrôle sur le site puis réessaie, ou passe au suivant.");
                if (!automated) showBrowser();
                return;
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
                if (listing.title.isEmpty()) continue;
                ListingEngine.Listing previous = imported.get(key);
                if (previous == null) {
                    imported.put(key, listing);
                    added++;
                } else {
                    // Relecture après défilement : la fiche DOM peut maintenant
                    // préciser l'annonceur, la surface ou le prix. Ne pas perdre
                    // ces détails sous prétexte que l'URL est déjà enregistrée.
                    imported.put(key, new ListingEngine.Listing(
                        previous.source, previous.url,
                        listing.title.length() > previous.title.length() ? listing.title : previous.title,
                        previous.city,
                        previous.seller.isEmpty() ? listing.seller : previous.seller,
                        listing.description.length() > previous.description.length()
                            ? listing.description : previous.description,
                        previous.address.isEmpty() ? listing.address : previous.address,
                        previous.lot.isEmpty() ? listing.lot : previous.lot,
                        previous.priceEuros > 0 ? previous.priceEuros : listing.priceEuros,
                        previous.areaM2 > 0 ? previous.areaM2 : listing.areaM2,
                        listing.cityConfirmed
                    ));
                }
            }
            String checkedSource = result.optString("source", sourceHost);
            sourcesVisited.add(checkedSource + " · " + activeCity);
            int displayedCount = result.isNull("declaredCount") ? -1 :
                result.optInt("declaredCount", -1);
            int extractedCount = result.optInt("retrieved", 0);
            String total = displayedCount > 0
                ? " sur " + displayedCount + " annonce(s) affichée(s) par le site"
                : " détectée(s) sur cette page";
            String warning = displayedCount > extractedCount
                ? " · Import PARTIEL : ne pas confondre avec le total des terrains disponibles."
                : " · Vérifie le tri dans les onglets.";
            String report = extractedCount + " fiche(s) lue(s)" + total +
                ", " + added + " nouvelle(s), " + imported.size() +
                " cumulée(s). " + sourcesVisited.size() + " source(s) consultée(s)." + warning;
            if (!imported.isEmpty()) {
                List<ListingEngine.Result> classified =
                    ListingEngine.classify(new ArrayList<>(imported.values()));
                boolean hasSelected = !ListingEngine.filtered(classified).isEmpty();
                chooseTab(hasSelected ? filteredTab : allTab);
                displayListings(hasSelected);
                if (!hasSelected) report += " · Aucune fiche validée : contrôle dans Toutes les annonces.";
            }
            if (!automated) status.setText(report);
        } catch (Exception error) {
            status.setText("Cette page n’a pas pu être lue. Ce n’est PAS « zéro terrain disponible ». " +
                "Cause : " + error.getMessage());
        }
    }

    private void displayDevelopers() {
        showResults();
        cards.removeAllViews();
        TextView title = heading("18 lotisseurs · programmes et terrains", 19, GOLD, true);
        place(cards, title, 0, 8);
        TextView intro = text(
            "Catalogue accessible directement dans TerraNova, conservé depuis TerrainScope V58. " +
            "Ouvre le site de chaque lotisseur pour vérifier ses programmes, ses prix et la disponibilité réelle des lots.",
            12, false);
        intro.setTextColor(MUTED);
        place(cards, intro, 0, 8);
        try {
            JSONObject catalog = new JSONObject(readAsset("lotisseurs-37.json"));
            JSONArray developers = catalog.getJSONArray("developers");
            for (int i = 0; i < developers.length(); i++) {
                JSONObject developer = developers.getJSONObject(i);
                String name = developer.getString("name");
                String site = developer.getString("url");
                String domain = developer.optString("domain", "");
                String notice = developer.optString("notice", "");
                boolean catalogueAvailable = developer.optBoolean("catalogue", true);
                LinearLayout card = section(LIGHT, 13, BORDER);
                place(card, heading(name, 16, TEXT, true), 0, 3);
                TextView website = heading(domain, 12, GOLD, false);
                place(card, website, 0, 4);
                if (!notice.isEmpty()) {
                    TextView info = text(notice, 12, false);
                    info.setTextColor(MUTED);
                    place(card, info, 0, 5);
                }
                Button go = action(catalogueAvailable ?
                    "Voir les terrains / programmes ↗" : "Voir le site officiel ↗", true);
                go.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(site)));
                    } catch (Exception error) {
                        status.setText("Impossible d'ouvrir le site de " + name + ".");
                    }
                });
                place(card, go, 4, 0);
                place(cards, card, 0, 9);
            }
            status.setText("18 lotisseurs accessibles. Liens conservés de TerrainScope ; disponibilités à vérifier sur les sites officiels.");
        } catch (Exception error) {
            status.setText("Impossible de lire le catalogue embarqué des lotisseurs : " + error.getMessage());
            cards.addView(text("Le catalogue n'a pas pu être chargé. Réessaie ou vérifie l'installation.", 13, false));
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
        Button backToSite = action("← Retour au site · charger d'autres annonces", false);
        backToSite.setTextSize(12);
        backToSite.setOnClickListener(v -> showBrowser());
        place(cards, backToSite, 10, 5);
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
            record.state == ListingEngine.State.TO_VERIFY ? " · À VÉRIFIER" :
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
        stopScan();
        imported.clear();
        sourcesVisited.clear();
        sourcesOpened.clear();
        activeCities.clear();
        cityPosition = 0;
        batchSignature = "";
        activeCity = "";
        cityInput.setText("");
        browser.loadUrl("about:blank");
        showBrowser();
        status.setText("Nouvelle recherche vide. Aucune annonce précédente conservée.");
    }

    private void showBrowser() {
        showingBrowser = true;
        browser.setVisibility(View.VISIBLE);
        browser.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        results.setVisibility(View.GONE);
    }
    private void showResults() {
        showingBrowser = false;
        // Garder la WebView attachée et active pour la collecte en arrière-plan
        // même lorsque les résultats occupent l'écran.
        browser.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams hidden = new LinearLayout.LayoutParams(-1, dp(1));
        browser.setLayoutParams(hidden);
        results.setVisibility(View.VISIBLE);
    }
    private boolean approvedHost(String host) {
        for (String name : new String[]{"leboncoin.fr","seloger.com","bienici.com",
                "iadfrance.fr","immobilier.notaires.fr","immonot.com"}) {
            if (host.equals(name) || host.endsWith("." + name)) return true;
        }
        return false;
    }
    private String cleanCity() { return activeCity; }
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
        stopScan();
        browser.destroy();
        super.onDestroy();
    }
}
