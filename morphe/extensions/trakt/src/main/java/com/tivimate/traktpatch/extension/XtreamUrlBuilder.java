package com.tivimate.traktpatch.extension;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds credential-preserving Xtream API URLs without logging or decoding secrets. */
public final class XtreamUrlBuilder {
    private static final Pattern WRAPPED_VALUE = Pattern.compile(
            "\\\"([hup])\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");

    private XtreamUrlBuilder() { }

    public static String vodInfoUrl(String playlistUrl, String xcId) {
        if (xcId == null || !xcId.matches("[0-9]+")) {
            throw new IllegalArgumentException("invalid Xtream VOD id");
        }
        if (playlistUrl != null && playlistUrl.startsWith("xc:")) {
            return wrappedVodInfoUrl(playlistUrl.substring(3), xcId);
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

    public static String seriesInfoUrl(String playlistUrl, String seriesId) {
        String vod = vodInfoUrl(playlistUrl, seriesId);
        String suffix = "action=get_vod_info&vod_id=" + seriesId;
        if (!vod.endsWith(suffix)) throw new IllegalArgumentException("invalid Xtream series URL");
        return vod.substring(0, vod.length() - suffix.length())
                + "action=get_series_info&series_id=" + seriesId;
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

    private static String wrappedVodInfoUrl(String json, String xcId) {
        String host = null;
        String username = null;
        String password = null;
        Matcher matcher = WRAPPED_VALUE.matcher(json == null ? "" : json);
        while (matcher.find()) {
            String value = unescapeJsonString(matcher.group(2));
            if ("h".equals(matcher.group(1))) host = value;
            if ("u".equals(matcher.group(1))) username = value;
            if ("p".equals(matcher.group(1))) password = value;
        }
        if (host == null || username == null || username.length() == 0
                || password == null || password.length() == 0) {
            throw new IllegalArgumentException("invalid Xtream wrapper");
        }

        URI base;
        try {
            base = URI.create(host);
        } catch (RuntimeException ignored) {
            throw new IllegalArgumentException("invalid Xtream host");
        }
        String scheme = base.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || base.getRawAuthority() == null || base.getRawUserInfo() != null
                || base.getRawQuery() != null || base.getRawFragment() != null) {
            throw new IllegalArgumentException("invalid Xtream host");
        }
        String path = base.getRawPath();
        if (path == null || path.length() == 0) path = "/";
        if (!path.endsWith("/")) path += "/";
        return scheme + "://" + base.getRawAuthority() + path + "player_api.php"
                + "?username=" + encode(username)
                + "&password=" + encode(password)
                + "&action=get_vod_info&vod_id=" + xcId;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception impossible) {
            throw new IllegalStateException("UTF-8 unavailable");
        }
    }

    private static String unescapeJsonString(String value) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\') {
                result.append(current);
                continue;
            }
            if (++index >= value.length()) throw new IllegalArgumentException("invalid Xtream wrapper");
            char escaped = value.charAt(index);
            if (escaped == '"' || escaped == '\\' || escaped == '/') result.append(escaped);
            else if (escaped == 'b') result.append('\b');
            else if (escaped == 'f') result.append('\f');
            else if (escaped == 'n') result.append('\n');
            else if (escaped == 'r') result.append('\r');
            else if (escaped == 't') result.append('\t');
            else if (escaped == 'u' && index + 4 < value.length()) {
                try {
                    result.append((char) Integer.parseInt(value.substring(index + 1, index + 5), 16));
                    index += 4;
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException("invalid Xtream wrapper");
                }
            } else {
                throw new IllegalArgumentException("invalid Xtream wrapper");
            }
        }
        return result.toString();
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
