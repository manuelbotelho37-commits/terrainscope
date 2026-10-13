package fr.terrainscope.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Affiche uniquement la véritable photographie disponible sur l'annonce.
 * Jamais de stock photo ni de téléchargement sur le fil de l'interface. */
final class PhotoLoader {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024) {
        @Override protected int sizeOf(String url, Bitmap bitmap) {
            return Math.max(1, bitmap.getByteCount() / 1024);
        }
    };
    private PhotoLoader() {}

    static void show(ImageView image, String url) {
        show(image, url, null);
    }

    /** Source secondaire utilisée pour le favicon officiel des Notaires. */
    static void show(ImageView image, String url, String fallbackUrl) {
        if (url == null || !url.startsWith("https://")) {
            image.setVisibility(View.GONE);
            return;
        }
        image.setTag(url);
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            image.setVisibility(View.VISIBLE);
            image.setImageBitmap(cached);
            return;
        }
        POOL.execute(() -> {
            Bitmap primary = download(url);
            Bitmap bitmap = primary;
            if (bitmap == null && fallbackUrl != null && fallbackUrl.startsWith("https://")) {
                bitmap = download(fallbackUrl);
            }
            if (bitmap != null) CACHE.put(url, bitmap);
            final Bitmap loaded = bitmap;
            MAIN.post(() -> {
                if (!url.equals(image.getTag())) return;
                if (loaded == null) image.setVisibility(View.GONE);
                else {
                    image.setVisibility(View.VISIBLE);
                    image.setImageBitmap(loaded);
                }
            });
        });
    }

    private static Bitmap download(String source) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(source);
            if (!"https".equalsIgnoreCase(url.getProtocol())) return null;
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(7000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/132.0 Mobile Safari/537.36");
            conn.setRequestProperty("Accept", "image/avif,image/webp,image/jpeg,image/png,*/*");
            if (conn.getResponseCode() != 200) return null;
            String type = conn.getContentType();
            if (type != null && !type.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) return null;
            if (conn.getContentLength() > 5_000_000) return null;
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    if (bytes.size() + len > 5_000_000) return null;
                    bytes.write(buffer, 0, len);
                }
                byte[] data = bytes.toByteArray();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 1;
                while (bounds.outWidth / opts.inSampleSize > 900 ||
                    bounds.outHeight / opts.inSampleSize > 900) opts.inSampleSize *= 2;
                return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
