package com.tivimate.traktpatch.extension;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Resolves Xtream VOD identity away from TiviMate's database transaction thread. */
public final class XtreamMetadataResolver {
    private static final String TAG = "TiviMateTraktMeta";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private XtreamMetadataResolver() { }

    public static void resolveAsync(final String playlistUrl, final String xcId) {
        EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                try {
                    String infoUrl = XtreamUrlBuilder.vodInfoUrl(playlistUrl, xcId);
                    connection = (HttpURLConnection) new URL(infoUrl).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10_000);
                    connection.setReadTimeout(15_000);
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("User-Agent", "TiviMate-Trakt-Patch/1.0");
                    int status = connection.getResponseCode();
                    InputStream stream = status >= 200 && status < 300
                            ? connection.getInputStream() : connection.getErrorStream();
                    String payload = readText(stream);
                    if (status < 200 || status >= 300) {
                        Log.w(TAG, "metadata request failed status=" + status);
                        return;
                    }
                    JSONObject root = new JSONObject(payload);
                    JSONObject info = root.optJSONObject("info");
                    JSONObject movie = root.optJSONObject("movie_data");
                    boolean tmdb = present(info, "tmdb_id") || present(movie, "tmdb_id");
                    boolean imdb = present(info, "imdb_id") || present(movie, "imdb_id");
                    boolean title = present(info, "name") || present(movie, "name");
                    boolean year = present(info, "releasedate") || present(info, "releaseDate")
                            || present(info, "release_date") || present(movie, "year");
                    Log.i(TAG, "resolved movie metadata tmdb=" + tmdb + " imdb=" + imdb
                            + " title=" + title + " year=" + year);
                } catch (Exception error) {
                    // Never include exception messages: URL failures can contain credentials.
                    Log.w(TAG, "metadata resolution failed type=" + error.getClass().getSimpleName());
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    private static boolean present(JSONObject value, String key) {
        if (value == null || !value.has(key) || value.isNull(key)) return false;
        String text = value.optString(key, "").trim();
        return text.length() > 0 && !"0".equals(text) && !"null".equalsIgnoreCase(text);
    }

    private static String readText(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        } finally {
            reader.close();
        }
        return result.toString();
    }
}
