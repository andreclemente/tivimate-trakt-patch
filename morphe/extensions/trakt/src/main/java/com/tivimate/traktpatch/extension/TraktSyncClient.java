package com.tivimate.traktpatch.extension;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/** Sends already-resolved playback identity directly to Trakt. */
public final class TraktSyncClient {
    private static final String TAG = "TiviMateTraktSync";
    private static final String TRAKT_API = "https://api.trakt.tv";
    private static final int MAX_ERROR_RESPONSE_CHARS = 4096;
    private static final Object OUTBOUND_LOCK = new Object();
    private static volatile Context applicationContext;

    private TraktSyncClient() { }

    static void invalidateAuthenticationState() {
        // Disconnect advances the auth generation before waiting here. Work already
        // inside the critical section finishes before local disconnect returns; work
        // entering afterward observes the new generation and fails closed.
        synchronized (OUTBOUND_LOCK) { }
    }

    public static void initialize(Context context) {
        if (context != null) applicationContext = context.getApplicationContext();
    }

    public static void submitMovie(JSONObject info, JSONObject movie, long positionMs, long durationMs) {
        synchronized (OUTBOUND_LOCK) {
            submitMovieLocked(info, movie, positionMs, durationMs);
        }
    }

    private static void submitMovieLocked(JSONObject info, JSONObject movie,
                                          long positionMs, long durationMs) {
        Context context = applicationContext;
        if (context == null || !TraktDeviceAuth.moviesEnabled(context) || durationMs <= 0L) return;
        String accessToken = TraktDeviceAuth.accessToken(context);
        if (accessToken == null) return;
        String clientId = TraktDeviceAuth.clientId(context);
        if (clientId == null) return;
        long authorizationGeneration = TraktDeviceAuth.generation();
        if (!TraktDeviceAuth.isCurrentAccessToken(
                context, authorizationGeneration, accessToken)) return;
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
            post(progress >= 80.0d ? "/scrobble/stop" : "/scrobble/pause",
                    payload, accessToken, clientId, "movie", context, authorizationGeneration);
        } catch (Exception error) {
            Log.w(TAG, "movie scrobble failed type=" + error.getClass().getSimpleName());
        }
    }

    public static void submitEpisode(JSONObject info, int season, int number,
                                     long positionMs, long durationMs) {
        synchronized (OUTBOUND_LOCK) {
            submitEpisodeLocked(info, season, number, positionMs, durationMs);
        }
    }

    private static void submitEpisodeLocked(JSONObject info, int season, int number,
                                            long positionMs, long durationMs) {
        Context context = applicationContext;
        if (context == null || !TraktDeviceAuth.showsEnabled(context) || durationMs <= 0L
                || season <= 0 || number <= 0) return;
        String accessToken = TraktDeviceAuth.accessToken(context);
        if (accessToken == null) return;
        String clientId = TraktDeviceAuth.clientId(context);
        if (clientId == null) return;
        long authorizationGeneration = TraktDeviceAuth.generation();
        if (!TraktDeviceAuth.isCurrentAccessToken(
                context, authorizationGeneration, accessToken)) return;
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
            post(progress >= 80.0d ? "/scrobble/stop" : "/scrobble/pause",
                    payload, accessToken, clientId, "episode", context, authorizationGeneration);
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

    private static void post(String path, JSONObject payload, String accessToken, String clientId,
                             String mediaType, Context context,
                             long authorizationGeneration) throws Exception {
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        String action = path.substring(path.lastIndexOf('/') + 1);
        int progressBucket = ((int) payload.getDouble("progress") / 5) * 5;
        for (int attempt = 0; attempt < 2; attempt++) {
            if (!TraktDeviceAuth.isCurrentAccessToken(
                    context, authorizationGeneration, accessToken)) return;
            HttpURLConnection connection = (HttpURLConnection) new URL(TRAKT_API + path).openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(15_000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setRequestProperty("trakt-api-key", clientId);
                connection.setRequestProperty("trakt-api-version", "2");
                connection.setRequestProperty("User-Agent", "TiviMate-Trakt-Patch/1.0");
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
                int status = connection.getResponseCode();
                if (!TraktDeviceAuth.isCurrentAccessToken(
                        context, authorizationGeneration, accessToken)) return;
                if (status >= 200 && status < 300) {
                    Log.i(TAG, mediaType + " scrobble accepted action=" + action
                            + " progress_bucket=" + progressBucket);
                    return;
                }
                if (status == 401 && attempt == 0) {
                    accessToken = TraktDeviceAuth.forceRefresh(context);
                    authorizationGeneration = TraktDeviceAuth.generation();
                    if (accessToken != null && TraktDeviceAuth.isCurrentAccessToken(
                            context, authorizationGeneration, accessToken)) continue;
                    Log.w(TAG, mediaType + " scrobble refresh rejected");
                    return;
                }
                if (status == 403) {
                    Log.w(TAG, mediaType + " scrobble forbidden");
                    return;
                }
                if (attempt == 0 && (status == 429 || status >= 500)) {
                    Thread.sleep(1000L);
                    continue;
                }
                Log.w(TAG, mediaType + " scrobble rejected action=" + action
                        + " progress_bucket=" + progressBucket + " status=" + status
                        + " reason=" + errorSummary(connection));
                return;
            } finally {
                connection.disconnect();
            }
        }
    }

    private static String errorSummary(HttpURLConnection connection) {
        InputStream stream = connection.getErrorStream();
        if (stream == null) return "unavailable";
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[512];
            while (text.length() < MAX_ERROR_RESPONSE_CHARS) {
                int count = reader.read(buffer, 0,
                        Math.min(buffer.length, MAX_ERROR_RESPONSE_CHARS - text.length()));
                if (count < 0) break;
                text.append(buffer, 0, count);
            }
            JSONObject response = new JSONObject(text.toString());
            String value = response.optString("error_description",
                    response.optString("error", "unspecified"));
            JSONObject errors = response.optJSONObject("errors");
            if ("unspecified".equals(value) && errors != null) {
                Iterator<String> keys = errors.keys();
                if (keys.hasNext()) {
                    String key = keys.next();
                    Object detail = errors.opt(key);
                    if (detail instanceof JSONArray && ((JSONArray) detail).length() > 0) {
                        detail = ((JSONArray) detail).opt(0);
                    }
                    value = key + ":" + String.valueOf(detail);
                }
            }
            return value.substring(0, Math.min(value.length(), 256))
                    .replaceAll("[^A-Za-z0-9_.:-]+", "_");
        } catch (Exception ignored) {
            return "unparseable";
        }
    }
}
