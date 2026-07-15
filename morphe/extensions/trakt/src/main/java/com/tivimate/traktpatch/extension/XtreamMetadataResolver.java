package com.tivimate.traktpatch.extension;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Resolves Xtream VOD identity away from TiviMate's database transaction thread. */
public final class XtreamMetadataResolver {
    private static final String TAG = "TiviMateTraktMeta";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int MAX_PENDING = 64;
    private static final LinkedHashMap<String, Runnable> PENDING = new LinkedHashMap<>();
    private static boolean drainScheduled;

    private XtreamMetadataResolver() { }

    public static void resolveAsync(final String playlistUrl, final String xcId,
                                    final long positionMs, final long durationMs) {
        enqueue("movie:" + xcId, new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                try {
                    String infoUrl = XtreamUrlBuilder.vodInfoUrl(playlistUrl, xcId);
                    connection = open(infoUrl);
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
                    TraktSyncClient.submitMovie(info, movie, positionMs, durationMs);
                } catch (IllegalArgumentException error) {
                    Log.w(TAG, "metadata URL rejected type=" + error.getClass().getSimpleName()
                            + " shape=" + XtreamUrlBuilder.diagnosticShape(playlistUrl));
                } catch (Exception error) {
                    // Never include exception messages: URL failures can contain credentials.
                    Log.w(TAG, "metadata resolution failed type=" + error.getClass().getSimpleName());
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    public static void resolveEpisodeAsync(final String playlistUrl, final String seriesXcId,
                                           final String episodeXcId, final long positionMs,
                                           final long durationMs) {
        enqueue("episode:" + seriesXcId + ":" + episodeXcId, new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                try {
                    String infoUrl = XtreamUrlBuilder.seriesInfoUrl(playlistUrl, seriesXcId);
                    connection = open(infoUrl);
                    int status = connection.getResponseCode();
                    String payload = readText(status >= 200 && status < 300
                            ? connection.getInputStream() : connection.getErrorStream());
                    if (status < 200 || status >= 300) {
                        Log.w(TAG, "series metadata request failed status=" + status);
                        return;
                    }
                    JSONObject root = new JSONObject(payload);
                    JSONObject info = root.optJSONObject("info");
                    JSONObject episode = findEpisode(root.optJSONObject("episodes"), episodeXcId);
                    if (episode == null) {
                        Log.w(TAG, "episode metadata missing provider_id_match=false");
                        return;
                    }
                    int season = episode.optInt("season", 0);
                    int number = episode.optInt("episode_num", episode.optInt("episode", 0));
                    Log.i(TAG, "resolved episode metadata tmdb=" + present(info, "tmdb_id")
                            + " season=" + (season > 0) + " number=" + (number > 0));
                    TraktSyncClient.submitEpisode(info, season, number, positionMs, durationMs);
                } catch (IllegalArgumentException error) {
                    Log.w(TAG, "series metadata URL rejected type=" + error.getClass().getSimpleName()
                            + " shape=" + XtreamUrlBuilder.diagnosticShape(playlistUrl));
                } catch (Exception error) {
                    Log.w(TAG, "episode metadata resolution failed type="
                            + error.getClass().getSimpleName());
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "TiviMate-Trakt-Patch/1.0");
        return connection;
    }

    private static void enqueue(String key, Runnable task) {
        synchronized (PENDING) {
            PENDING.remove(key);
            while (PENDING.size() >= MAX_PENDING) {
                Iterator<Map.Entry<String, Runnable>> oldest = PENDING.entrySet().iterator();
                if (!oldest.hasNext()) break;
                oldest.next();
                oldest.remove();
            }
            PENDING.put(key, task);
            if (drainScheduled) return;
            drainScheduled = true;
        }
        EXECUTOR.execute(new Runnable() {
            @Override public void run() { drainPending(); }
        });
    }

    private static void drainPending() {
        while (true) {
            Runnable task;
            synchronized (PENDING) {
                Iterator<Map.Entry<String, Runnable>> iterator = PENDING.entrySet().iterator();
                if (!iterator.hasNext()) {
                    drainScheduled = false;
                    return;
                }
                Map.Entry<String, Runnable> next = iterator.next();
                task = next.getValue();
                iterator.remove();
            }
            task.run();
        }
    }

    private static JSONObject findEpisode(JSONObject seasons, String episodeXcId) {
        if (seasons == null || episodeXcId == null) return null;
        java.util.Iterator<String> keys = seasons.keys();
        while (keys.hasNext()) {
            String seasonKey = keys.next();
            JSONArray episodes = seasons.optJSONArray(seasonKey);
            if (episodes == null) continue;
            for (int index = 0; index < episodes.length(); index++) {
                JSONObject episode = episodes.optJSONObject(index);
                if (episode != null && episodeXcId.equals(episode.optString("id", ""))) {
                    if (episode.optInt("season", 0) <= 0) {
                        try { episode.put("season", Integer.parseInt(seasonKey)); }
                        catch (Exception ignored) { /* submitter rejects unknown season. */ }
                    }
                    return episode;
                }
            }
        }
        return null;
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
