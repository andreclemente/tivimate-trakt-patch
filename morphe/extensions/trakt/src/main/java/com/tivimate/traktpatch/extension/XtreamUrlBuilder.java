package com.tivimate.traktpatch.extension;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Builds credential-preserving Xtream API URLs without logging or decoding secrets. */
public final class XtreamUrlBuilder {
    private XtreamUrlBuilder() { }

    public static String vodInfoUrl(String playlistUrl, String xcId) {
        if (xcId == null || !xcId.matches("[0-9]+")) {
            throw new IllegalArgumentException("invalid Xtream VOD id");
        }

        URI playlist = URI.create(playlistUrl == null ? "" : playlistUrl);
        String scheme = playlist.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || playlist.getRawAuthority() == null) {
            throw new IllegalArgumentException("invalid Xtream playlist URL");
        }

        String username = rawQueryValue(playlist.getRawQuery(), "username");
        String password = rawQueryValue(playlist.getRawQuery(), "password");
        if (username == null || username.length() == 0 || password == null || password.length() == 0) {
            throw new IllegalArgumentException("missing Xtream credentials");
        }

        String path = playlist.getRawPath();
        int slash = path == null ? -1 : path.lastIndexOf('/');
        String directory = slash < 0 ? "/" : path.substring(0, slash + 1);
        return scheme + "://" + playlist.getRawAuthority() + directory + "player_api.php"
                + "?username=" + username
                + "&password=" + password
                + "&action=get_vod_info&vod_id=" + xcId;
    }

    public static String diagnosticShape(String playlistUrl) {
        try {
            URI value = URI.create(playlistUrl == null ? "" : playlistUrl);
            String path = value.getRawPath();
            int slash = path == null ? -1 : path.lastIndexOf('/');
            String leaf = slash < 0 ? path : path.substring(slash + 1);
            leaf = safeName(leaf);
            List<String> keys = new ArrayList<>();
            String query = value.getRawQuery();
            if (query != null) {
                for (String item : query.split("&")) {
                    int equals = item.indexOf('=');
                    String key = equals < 0 ? item : item.substring(0, equals);
                    keys.add(safeName(key));
                }
            }
            return "scheme=" + safeName(value.getScheme()) + " leaf=" + leaf
                    + " query_keys=" + keys + " userinfo=" + (value.getRawUserInfo() != null);
        } catch (RuntimeException ignored) {
            return "invalid_uri";
        }
    }

    private static String safeName(String value) {
        if (value == null) return "none";
        String cleaned = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return cleaned.length() <= 64 ? cleaned : cleaned.substring(0, 64);
    }

    private static String rawQueryValue(String query, String wanted) {
        if (query == null) return null;
        for (String item : query.split("&")) {
            int equals = item.indexOf('=');
            String key = equals < 0 ? item : item.substring(0, equals);
            if (wanted.equals(key)) return equals < 0 ? "" : item.substring(equals + 1);
        }
        return null;
    }
}
