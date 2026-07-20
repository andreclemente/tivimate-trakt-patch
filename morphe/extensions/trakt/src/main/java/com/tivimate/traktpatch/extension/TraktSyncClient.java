package com.tivimate.traktpatch.extension;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Sends already-resolved playback identity directly to Trakt. */
public final class TraktSyncClient {
    private static final String TAG = "TiviMateTraktSync";
    private static final String TRAKT_API = "https://api.trakt.tv";
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
            StableIds selected = movieIds(info, movie);
            if (selected.conflict) {
                Log.w(TAG, "movie scrobble skipped reason=conflicting_stable_id");
                return;
            }
            JSONObject ids = selected.ids;
            if (ids.length() == 0) {
                Log.w(TAG, "movie scrobble skipped reason=missing_stable_id");
                return;
            }
            double progress = TraktProgressMath.percent(positionMs, durationMs);
            if (progress < 1.0d) {
                Log.i(TAG, "movie scrobble skipped reason=progress_below_minimum");
                return;
            }
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
            StableIds selected = showIds(info);
            if (selected.conflict) {
                Log.w(TAG, "episode scrobble skipped reason=conflicting_stable_id");
                return;
            }
            JSONObject ids = selected.ids;
            if (ids.length() == 0) {
                Log.w(TAG, "episode scrobble skipped reason=missing_stable_show_id");
                return;
            }
            double progress = TraktProgressMath.percent(positionMs, durationMs);
            if (progress < 1.0d) {
                Log.i(TAG, "episode scrobble skipped reason=progress_below_minimum");
                return;
            }
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

    private static StableIds showIds(JSONObject info) throws Exception {
        return stableIds(info);
    }

    private static StableIds movieIds(JSONObject info, JSONObject movie) throws Exception {
        return stableIds(info, movie);
    }

    private static StableIds stableIds(JSONObject... sources) throws Exception {
        String tmdb = collectTmdb(sources);
        String imdb = collectImdb(sources);
        if (tmdb == null || imdb == null) return new StableIds(new JSONObject(), true);
        JSONObject ids = new JSONObject();
        if (!tmdb.isEmpty()) ids.put("tmdb", Long.parseLong(tmdb));
        if (!imdb.isEmpty()) ids.put("imdb", imdb);
        return new StableIds(ids, false);
    }

    private static String collectTmdb(JSONObject... sources) {
        Set<String> values = new HashSet<>();
        for (JSONObject source : sources) {
            if (source == null) continue;
            for (String key : new String[]{"tmdb_id", "tmdb"}) {
                String value = TraktImportPolicy.stableTmdb(source.opt(key));
                if (!value.isEmpty()) values.add(value);
            }
        }
        if (values.size() > 1) return null;
        return values.isEmpty() ? "" : values.iterator().next();
    }

    private static String collectImdb(JSONObject... sources) {
        Set<String> values = new HashSet<>();
        for (JSONObject source : sources) {
            if (source == null) continue;
            for (String key : new String[]{"imdb_id", "imdb"}) {
                String value = TraktImportPolicy.stableImdb(source.opt(key));
                if (!value.isEmpty()) values.add(value);
            }
        }
        if (values.size() > 1) return null;
        return values.isEmpty() ? "" : values.iterator().next();
    }

    private static final class StableIds {
        final JSONObject ids;
        final boolean conflict;

        StableIds(JSONObject ids, boolean conflict) {
            this.ids = ids;
            this.conflict = conflict;
        }
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
                        + " progress_bucket=" + progressBucket
                        + " reason=" + rejectionReason(status));
                return;
            } finally {
                connection.disconnect();
            }
        }
    }

    private static String rejectionReason(int status) {
        // Never parse or log upstream bodies; values can be user-controlled or secret.
        if (status == 400) return "bad_request";
        if (status == 404) return "not_found";
        if (status == 409) return "conflict";
        if (status == 422) return "unprocessable";
        if (status == 429) return "rate_limited";
        if (status >= 400 && status < 500) return "client_error";
        if (status >= 500 && status < 600) return "server_error";
        return "unexpected_status";
    }
}
