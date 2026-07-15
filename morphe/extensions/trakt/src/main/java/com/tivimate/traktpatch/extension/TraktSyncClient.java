package com.tivimate.traktpatch.extension;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Sends already-resolved playback identity to Trakt through the credential-holding Worker. */
public final class TraktSyncClient {
    private static final String TAG = "TiviMateTraktSync";
    private static final String WORKER = "https://tivimate-trakt-oauth.andreclemente.workers.dev";
    private static volatile Context applicationContext;

    private TraktSyncClient() { }

    public static void initialize(Context context) {
        if (context != null) applicationContext = context.getApplicationContext();
    }

    public static void submitMovie(JSONObject info, JSONObject movie, long positionMs, long durationMs) {
        Context context = applicationContext;
        if (context == null || !TraktDeviceAuth.moviesEnabled(context) || durationMs <= 0L) return;
        String accessToken = TraktDeviceAuth.accessToken(context);
        if (accessToken == null) return;
        try {
            JSONObject ids = movieIds(info, movie);
            if (ids.length() == 0) {
                Log.w(TAG, "movie scrobble skipped reason=missing_stable_id");
                return;
            }
            double progress = TraktProgressMath.percent(positionMs, durationMs);
            JSONObject payload = new JSONObject();
            payload.put("movie", new JSONObject().put("ids", ids));
            payload.put("progress", progress);
            post(progress >= 80.0d ? "/v1/scrobble/stop" : "/v1/scrobble/pause",
                    payload, accessToken, "movie");
        } catch (Exception error) {
            Log.w(TAG, "movie scrobble failed type=" + error.getClass().getSimpleName());
        }
    }

    public static void submitEpisode(JSONObject info, int season, int number,
                                     long positionMs, long durationMs) {
        Context context = applicationContext;
        if (context == null || !TraktDeviceAuth.showsEnabled(context) || durationMs <= 0L
                || season <= 0 || number <= 0) return;
        String accessToken = TraktDeviceAuth.accessToken(context);
        if (accessToken == null) return;
        try {
            JSONObject ids = showIds(info);
            if (ids.length() == 0) {
                Log.w(TAG, "episode scrobble skipped reason=missing_stable_show_id");
                return;
            }
            double progress = TraktProgressMath.percent(positionMs, durationMs);
            JSONObject payload = new JSONObject();
            payload.put("show", new JSONObject().put("ids", ids));
            payload.put("episode", new JSONObject().put("season", season).put("number", number));
            payload.put("progress", progress);
            post(progress >= 80.0d ? "/v1/scrobble/stop" : "/v1/scrobble/pause",
                    payload, accessToken, "episode");
        } catch (Exception error) {
            Log.w(TAG, "episode scrobble failed type=" + error.getClass().getSimpleName());
        }
    }

    private static JSONObject showIds(JSONObject info) throws Exception {
        JSONObject ids = new JSONObject();
        String tmdb = first(info, null, "tmdb_id");
        if (tmdb.length() == 0) tmdb = first(info, null, "tmdb");
        String imdb = first(info, null, "imdb_id");
        if (imdb.length() == 0) imdb = first(info, null, "imdb");
        if (tmdb.matches("[0-9]+")) ids.put("tmdb", Long.parseLong(tmdb));
        if (imdb.matches("tt[0-9]+")) ids.put("imdb", imdb);
        return ids;
    }

    private static JSONObject movieIds(JSONObject info, JSONObject movie) throws Exception {
        JSONObject ids = new JSONObject();
        String tmdb = first(info, movie, "tmdb_id");
        String imdb = first(info, movie, "imdb_id");
        if (tmdb.matches("[0-9]+")) ids.put("tmdb", Long.parseLong(tmdb));
        if (imdb.matches("tt[0-9]+")) ids.put("imdb", imdb);
        return ids;
    }

    private static String first(JSONObject primary, JSONObject secondary, String key) {
        String value = primary == null ? "" : primary.optString(key, "").trim();
        if (value.length() == 0 || "0".equals(value) || "null".equalsIgnoreCase(value)) {
            value = secondary == null ? "" : secondary.optString(key, "").trim();
        }
        return value;
    }

    private static void post(String path, JSONObject payload, String accessToken, String mediaType) throws Exception {
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(WORKER + path).openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(15_000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setRequestProperty("User-Agent", "TiviMate-Trakt-Patch/1.0");
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
                int status = connection.getResponseCode();
                if (status >= 200 && status < 300) {
                    Log.i(TAG, mediaType + " scrobble accepted action="
                            + path.substring(path.lastIndexOf('/') + 1)
                            + " progress_bucket=" + ((int) payload.getDouble("progress") / 5) * 5);
                    return;
                }
                if (status == 401 || status == 403) {
                    Context context = applicationContext;
                    if (context != null) TraktDeviceAuth.invalidateAccessToken(context);
                    Log.w(TAG, mediaType + " scrobble authorization rejected");
                    return;
                }
                if (attempt == 0 && (status == 429 || status >= 500)) {
                    Thread.sleep(1000L);
                    continue;
                }
                Log.w(TAG, mediaType + " scrobble rejected status=" + status);
                return;
            } finally {
                connection.disconnect();
            }
        }
    }
}
