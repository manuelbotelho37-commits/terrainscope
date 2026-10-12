package fr.terrainscope.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        loadCommuneIndex();
        loadSeLogerPaths();
        buildScreen();
        status.setText("Choisis une commune, ouvre un site, puis importe les annonces affichées.");
    }

    private void buildScreen() {
        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(10), dp(8), dp(10), dp(6));
        main.setBackgroundColor(Color.rgb(248, 251, 248));

        TextView heading = text("TerrainScope · Android expérimental", 19, true);
        heading.setTextColor(GREEN);
        main.addView(heading);
        status = text("", 12, false);
        main.addView(status);

        LinearLayout search = row();
        cityInput = new EditText(this);
        cityInput.setSingleLine(true);
        cityInput.setTextSize(14);
        cityInput.setHint("Commune (ex. Monnaie)");
        search.addView(cityInput, new LinearLayout.LayoutParams(0, dp(49), 3f));
        siteSpinner = new Spinner(this);
        ArrayAdapter<String> choices = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_dropdown_item, SITES);
        siteSpinner.setAdapter(choices);
        search.addView(siteSpinner, new LinearLayout.LayoutParams(0, dp(49), 2f));
        main.addView(search);

        LinearLayout nav = row();
        addButton(nav, "Ouvrir site", () -> openSelectedSite(), 1f);
        addButton(nav, "Importer la page", () -> importVisiblePage(), 1.4f);
        addButton(nav, "Nouvelle recherche", () -> reset(), 1.3f);
        main.addView(nav);

        LinearLayout tabs = row();
        addButton(tabs, "Annonces triées", () -> displayListings(true), 1f);
        addButton(tabs, "Toutes les annonces", () -> displayListings(false), 1f);
        addButton(tabs, "Lotisseurs", () -> {
            browser.loadUrl(TERRAIN_SCOPE);
            showBrowser();
            status.setText("Module des lotisseurs conservé dans TerrainScope Web.");
        }, 0.8f);
        main.addView(tabs);

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
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (showingBrowser) status.setText(
                    "Page ouverte. Attends l’affichage des annonces, puis appuie sur « Importer la page »." +
                    " Répète sur les pages suivantes et les autres sites.");
            }
        });
        main.addView(browser, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        results = new ScrollView(this);
        cards = new LinearLayout(this);
        cards.setPadding(dp(3), dp(10), dp(3), dp(10));
        cards.setOrientation(LinearLayout.VERTICAL);
        results.addView(cards);
        main.addView(results, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
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
        String code = postalCodes.getOrDefault(ListingEngine.normalize(city), "");
        String slug = slug(city);
        String url;
        switch (site) {
            case "Le Bon Coin":
                url = "https://www.leboncoin.fr/recherche?category=9&text=" + enc("terrain " + city);
                break;
            case "SeLoger":
                String path = selogerPaths.get(ListingEngine.normalize(city));
                url = path == null ? "https://www.seloger.com/" :
                    "https://www.seloger.com/recherche/achat/terrain/centre-val-de-loire/" + path;
                break;
            case "Bien’ici":
                url = code.isEmpty() ? "https://www.bienici.com/" :
                    "https://www.bienici.com/recherche/achat/" + slug + "-" + code + "/terrain";
                break;
            case "IAD":
                url = code.isEmpty() ? "https://www.iadfrance.fr/" :
                    "https://www.iadfrance.fr/annonces/" + slug + "-" + code + "/vente/terrain";
                break;
            case "Notaires":
                // Les URL construites pour les communes ont déjà été instables : ne pas les inventer.
                url = "https://www.immobilier.notaires.fr/fr/annonces-immobilieres";
                break;
            default:
                url = TERRAIN_SCOPE;
        }
        showBrowser();
        browser.loadUrl(url);
        status.setText("Ouverture de " + site + " pour " + city +
            ". Confirme la commune et la catégorie Terrain sur le site avant l’import.");
    }

    private void importVisiblePage() {
        String city = cleanCity();
        if (city.isEmpty()) {
            status.setText("Indique une commune avant d’importer les annonces.");
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
