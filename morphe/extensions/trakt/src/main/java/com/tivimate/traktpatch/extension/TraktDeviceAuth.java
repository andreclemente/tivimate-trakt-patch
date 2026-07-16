package com.tivimate.traktpatch.extension;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Device Authorization client. The Trakt secret is only held by the Worker. */
public final class TraktDeviceAuth {
    private static final String DEVICE_CODE_URL =
            "https://tivimate-trakt-oauth.andreclemente.workers.dev/v1/device/code";
    private static final String DEVICE_TOKEN_URL =
            "https://tivimate-trakt-oauth.andreclemente.workers.dev/v1/device/token";
    private static final String DEVICE_REFRESH_URL =
            "https://tivimate-trakt-oauth.andreclemente.workers.dev/v1/token";
    private static final String CLIENT_URL =
            "https://tivimate-trakt-oauth.andreclemente.workers.dev/v1/client";
    private static final String REVOKE_URL =
            "https://tivimate-trakt-oauth.andreclemente.workers.dev/v1/revoke";
    private static final int MAX_RESPONSE_CHARS = 1_000_000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static final Object AUTH_LOCK = new Object();
    private static final Object REFRESH_LOCK = new Object();
    private static long AUTH_GENERATION;

    private TraktDeviceAuth() { }

    public static boolean isConnected(Context context) {
        return new TokenStore(context).hasValidToken();
    }

    static String accessToken(Context context) {
        TokenStore store = new TokenStore(context);
        if (store.storedTokenStillValid()) return store.accessToken();
        return refreshAccessToken(store);
    }

    static String forceRefresh(Context context) {
        return refreshAccessToken(new TokenStore(context));
    }

    private static long generation() {
        synchronized (AUTH_LOCK) { return AUTH_GENERATION; }
    }

    /** Returns the public API key, migrating already-encrypted token records on first use. */
    static synchronized String clientId(Context context) {
        TokenStore store = new TokenStore(context);
        String clientId = store.clientId();
        if (clientId != null) return clientId;
        try {
            JSONObject config = requestWithRetry(CLIENT_URL, null);
            clientId = config.optString("client_id", "");
            if (clientId.length() == 0 || clientId.length() > 4096) return null;
            store.saveClientId(clientId);
            return clientId;
        } catch (Exception ignored) {
            return null;
        }
    }

    static void invalidateAccessToken(Context context) {
        // A resource-server 401 is not proof that the refresh token is invalid.
        new TokenStore(context).expireAccessToken();
    }

    private static String refreshAccessToken(TokenStore store) {
        synchronized (REFRESH_LOCK) {
            long generation = generation();
            String refreshToken = store.refreshToken();
            if (refreshToken == null) return null;
            try {
                JSONObject request = new JSONObject();
                request.put("refresh_token", refreshToken);
                JSONObject token = requestWithRetry(DEVICE_REFRESH_URL, request);
                if (!saveIfCurrent(store, token, generation)) return null;
                return store.accessToken();
            } catch (DeviceAuthorizationException error) {
                if (isAuthoritativeInvalidRefresh(error)) clearIfCurrent(store, generation);
                return null;
            } catch (Exception ignored) {
                // Keep both tokens for a later network attempt.
                return null;
            }
        }
    }

    private static boolean isAuthoritativeInvalidRefresh(DeviceAuthorizationException error) {
        return (error.status == 400 || error.status == 401)
                && ("invalid_grant".equals(error.code) || "invalid_token".equals(error.code));
    }

    private static boolean saveIfCurrent(TokenStore store, JSONObject token, long generation)
            throws Exception {
        synchronized (AUTH_LOCK) {
            if (generation == AUTH_GENERATION) {
                store.save(token);
                AUTH_GENERATION++;
                return true;
            }
            return false;
        }
    }

    private static void clearIfCurrent(TokenStore store, long generation) {
        synchronized (AUTH_LOCK) {
            if (generation == AUTH_GENERATION) {
                AUTH_GENERATION++;
                store.clear();
            }
        }
    }

    static boolean moviesEnabled(Context context) {
        return new SyncSettings(context).moviesEnabled();
    }

    static boolean showsEnabled(Context context) {
        return new SyncSettings(context).showsEnabled();
    }

    public static void open(final Context context) { open(context, null); }

    public static void open(final Context context, final Runnable stateChanged) {
        if (isConnected(context)) {
            final AuthDialog connected = new AuthDialog(context);
            connected.showConnected(context, new Runnable() {
                @Override public void run() {
                    connected.confirmDisconnect(new Runnable() {
                        @Override public void run() {
                            disconnect(context, connected, stateChanged);
                        }
                    });
                }
            });
            return;
        }
        final long generation = generation();
        final AuthDialog dialog = new AuthDialog(context);
        dialog.show("Connect Trakt", "Requesting a Trakt activation code…");
        NETWORK.execute(new Runnable() {
            @Override public void run() {
                try {
                    final JSONObject device = post(DEVICE_CODE_URL, new JSONObject());
                    final String deviceCode = device.getString("device_code");
                    final String userCode = device.getString("user_code");
                    final String verificationUrl = device.getString("verification_url");
                    final int intervalSeconds = Math.max(2, device.optInt("interval", 5));
                    final long expiresAt = System.currentTimeMillis()
                            + (Math.max(60, device.optInt("expires_in", 600)) * 1000L);
                    MAIN.post(new Runnable() {
                        @Override public void run() {
                            if (!dialog.isShowing()) return;
                            dialog.setMessage("On your phone or computer, open:\n\n"
                                    + verificationUrl + "\n\nand enter this code:\n\n" + userCode
                                    + "\n\nWaiting for approval…");
                            poll(context, dialog, deviceCode, intervalSeconds * 1000L, expiresAt, generation,
                                    stateChanged);
                        }
                    });
                } catch (final Exception error) {
                    MAIN.post(new Runnable() {
                        @Override public void run() {
                            // Preserve the dialog on transient DNS/Worker failures so the
                            // user can read the cause rather than seeing it flash and vanish.
                            if (dialog.isShowing()) {
                                dialog.setMessage("Unable to contact Trakt: " + message(error)
                                        + "\n\nPlease check your connection and select Trakt again.");
                            }
                        }
                    });
                }
            }
        });
    }

    private static void poll(final Context context, final AuthDialog dialog, final String deviceCode,
                             final long delayMillis, final long expiresAt, final long generation,
                             final Runnable stateChanged) {
        if (!dialog.isShowing() || System.currentTimeMillis() >= expiresAt) {
            if (dialog.isShowing()) dialog.dismiss();
            Toast.makeText(context, "Trakt activation code expired", Toast.LENGTH_LONG).show();
            return;
        }
        NETWORK.execute(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject request = new JSONObject();
                    request.put("code", deviceCode);
                    final JSONObject token = post(DEVICE_TOKEN_URL, request);
                    if (!saveIfCurrent(new TokenStore(context), token, generation)) return;
                    TraktImportCoordinator.initialize(context);
                    MAIN.post(new Runnable() {
                        @Override public void run() {
                            if (dialog.isShowing()) dialog.dismiss();
                            if (stateChanged != null) stateChanged.run();
                            Toast.makeText(context, "Trakt connected", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (final Exception error) {
                    if (isRetryableDeviceAuthorizationError(error)) {
                        final long nextDelay = error instanceof DeviceAuthorizationException
                                && "slow_down".equals(((DeviceAuthorizationException) error).code)
                                ? delayMillis + 5000L : delayMillis;
                        MAIN.postDelayed(new Runnable() {
                            @Override public void run() {
                                poll(context, dialog, deviceCode, nextDelay, expiresAt, generation,
                                        stateChanged);
                            }
                        }, nextDelay);
                        return;
                    }
                    MAIN.post(new Runnable() {
                        @Override public void run() {
                            if (dialog.isShowing()) dialog.dismiss();
                            Toast.makeText(context, "Trakt connection failed: " + message(error),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private static boolean isRetryableDeviceAuthorizationError(Exception error) {
        if (error instanceof java.io.IOException) return true;
        if (!(error instanceof DeviceAuthorizationException)) return false;
        DeviceAuthorizationException authorization = (DeviceAuthorizationException) error;
        String code = authorization.code;
        return "authorization_pending".equals(code)
                || "slow_down".equals(code)
                || isRetryableStatus(authorization.status);
    }

    private static void disconnect(final Context context, final AuthDialog dialog,
                                   final Runnable stateChanged) {
        final String refreshToken = disconnectLocally(context);
        dialog.dismiss();
        if (stateChanged != null) stateChanged.run();
        NETWORK.execute(new Runnable() {
            @Override public void run() {
                boolean revoked = false;
                try {
                    if (refreshToken != null) {
                        JSONObject request = new JSONObject();
                        request.put("token", refreshToken);
                        post(REVOKE_URL, request);
                    }
                    revoked = true;
                } catch (Exception ignored) {
                    // Local disconnect is authoritative even if remote revocation is unavailable.
                } finally {
                    final boolean remoteRevoked = revoked;
                    MAIN.post(new Runnable() {
                        @Override public void run() {
                            Toast.makeText(context, remoteRevoked ? "Trakt disconnected"
                                    : "Trakt disconnected locally; remote revoke will need retry",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private static String disconnectLocally(Context context) {
        synchronized (AUTH_LOCK) {
            AUTH_GENERATION++;
            TokenStore store = new TokenStore(context);
            String refreshToken = store.refreshToken();
            store.clear();
            new SyncSettings(context).clear();
            return refreshToken;
        }
    }

    private static JSONObject post(String endpoint, JSONObject payload) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "TiviMate-Trakt-Patch/1.0");
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int status = connection.getResponseCode();
            if (isRetryableStatus(status)) {
                throw new DeviceAuthorizationException(status, "HTTP " + status);
            }
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String text = read(stream);
            if (status < 200 || status >= 300) {
                throw new DeviceAuthorizationException(status, responseErrorCode(endpoint, status, text));
            }
            return new JSONObject(text);
        } finally {
            connection.disconnect();
        }
    }

    private static String responseErrorCode(String endpoint, int status, String text) {
        if (DEVICE_TOKEN_URL.equals(endpoint) && status == 400 && text.length() == 0) {
            return "authorization_pending";
        }
        String code = "HTTP " + status;
        if (text.length() > 0) {
            try {
                code = new JSONObject(text).optString("error", code);
            } catch (Exception ignored) {
                // The HTTP status still controls retry behavior when an error body is malformed.
            }
        }
        return code;
    }

    private static JSONObject get(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "TiviMate-Trakt-Patch/1.0");
            int status = connection.getResponseCode();
            if (isRetryableStatus(status)) {
                throw new DeviceAuthorizationException(status, "HTTP " + status);
            }
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String text = read(stream);
            if (status < 200 || status >= 300) {
                throw new DeviceAuthorizationException(status, "HTTP " + status);
            }
            return new JSONObject(text);
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder value = new StringBuilder();
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            char[] chunk = new char[8192];
            int length = 0;
            for (int count; (count = reader.read(chunk, 0, chunk.length)) != -1;) {
                if (length + count > MAX_RESPONSE_CHARS) {
                    throw new IllegalStateException("response too large");
                }
                value.append(chunk, 0, count);
                length += count;
            }
        }
        return value.toString();
    }

    private static JSONObject requestWithRetry(String endpoint, JSONObject payload) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return payload == null ? get(endpoint) : post(endpoint, payload);
            } catch (DeviceAuthorizationException error) {
                if (attempt == 0 && isRetryableStatus(error.status)) {
                    Thread.sleep(1000L);
                    continue;
                }
                throw error;
            }
        }
        throw new IllegalStateException("retry exhausted");
    }

    private static boolean isRetryableStatus(int status) {
        return status == 429 || status >= 500;
    }

    private static String message(Exception error) {
        String value = error.getMessage();
        return value == null || value.length() == 0 ? "network error" : value;
    }

    private static final class DeviceAuthorizationException extends Exception {
        final int status;
        final String code;

        DeviceAuthorizationException(int status, String code) {
            super("HTTP " + status + ": " + code);
            this.status = status;
            this.code = code;
        }
    }

    /** Custom content avoids TiviMate's protected theme breaking AlertDialog inflation. */
    private static final class AuthDialog {
        private final Dialog dialog;
        private final TextView text;
        private final LinearLayout actions;
        private final Button action;

        AuthDialog(Context context) {
            Context uiContext = new ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault);
            dialog = new Dialog(uiContext);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCanceledOnTouchOutside(true);
            LinearLayout content = new LinearLayout(uiContext);
            content.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (28 * context.getResources().getDisplayMetrics().density);
            content.setPadding(padding, padding, padding, padding);
            text = new TextView(uiContext);
            text.setTextColor(Color.WHITE);
            text.setTextSize(20);
            text.setGravity(Gravity.CENTER);
            text.setMinWidth((int) (620 * context.getResources().getDisplayMetrics().density));
            action = new Button(uiContext);
            action.setAllCaps(false);
            actions = new LinearLayout(uiContext);
            actions.setOrientation(LinearLayout.VERTICAL);
            content.addView(text);
            content.addView(actions, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            dialog.setContentView(content);
        }

        void show(String title, String message) {
            actions.removeAllViews();
            action.setOnClickListener(null);
            text.setText(title + "\n\n" + message + "\n\nPress Back to cancel");
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.rgb(32, 32, 32)));
                window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        }

        void confirmDisconnect(final Runnable disconnect) {
            actions.removeAllViews();
            text.setText("Disconnect Trakt?\n\nLocal credentials will be removed immediately.");
            action.setText("Confirm disconnect");
            action.setOnClickListener(v -> disconnect.run());
            actions.addView(action, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            action.requestFocus();
        }

        void showConnected(final Context context, final Runnable disconnect) {
            final SyncSettings settings = new SyncSettings(context);
            actions.removeAllViews();
            text.setText("Trakt connected\n\nSync scope: " + settings.label()
                    + "\n\nPlayback progress sync is active.");
            addScope(context, settings, "movies", "Movies only");
            addScope(context, settings, "shows", "TV shows only");
            addScope(context, settings, "both", "Movies and TV shows");
            action.setText("Disconnect Trakt");
            action.setOnClickListener(v -> disconnect.run());
            actions.addView(action, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            dialog.show();
            action.requestFocus();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.rgb(32, 32, 32)));
                window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        }

        private void addScope(Context context, final SyncSettings settings, final String scope,
                              String title) {
            Button button = new Button(action.getContext());
            button.setAllCaps(false);
            button.setText(title);
            button.setOnClickListener(v -> {
                settings.set(scope);
                TraktImportCoordinator.requestImport();
                text.setText("Trakt connected\n\nSync scope: " + settings.label()
                        + "\n\nPlayback progress sync is active.");
                Toast.makeText(context, "Trakt sync scope: " + settings.label(), Toast.LENGTH_SHORT).show();
            });
            actions.addView(button, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        void setMessage(String message) { text.setText("Connect Trakt\n\n" + message + "\n\nPress Back to cancel"); }
        boolean isShowing() { return dialog.isShowing(); }
        void dismiss() { dialog.dismiss(); }
    }

    /** Sync choice gates movie and episode runtime events locally. */
    private static final class SyncSettings {
        private static final String PREFS = "tivimate_trakt_sync";
        private static final String SCOPE = "scope";
        private final SharedPreferences preferences;

        SyncSettings(Context context) {
            preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        void set(String scope) { preferences.edit().putString(SCOPE, scope).apply(); }
        void clear() { preferences.edit().clear().apply(); }

        String label() {
            String scope = preferences.getString(SCOPE, "both");
            if ("movies".equals(scope)) return "Movies only";
            if ("shows".equals(scope)) return "TV shows only";
            return "Movies and TV shows";
        }

        boolean moviesEnabled() {
            return !"shows".equals(preferences.getString(SCOPE, "both"));
        }

        boolean showsEnabled() {
            return !"movies".equals(preferences.getString(SCOPE, "both"));
        }
    }

    /** App-private encrypted persistence; plaintext tokens never enter preferences. */
    private static final class TokenStore {
        private static final String PREFS = "tivimate_trakt_auth";
        private static final String TOKENS = "tokens";
        private static final String KEY_ALIAS = "tivimate_trakt_token_key";
        private final SharedPreferences preferences;

        TokenStore(Context context) {
            preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        boolean hasValidToken() {
            String encrypted = preferences.getString(TOKENS, null);
            if (encrypted == null || encrypted.length() == 0) return false;
            try {
                JSONObject stored = new JSONObject(decrypt(encrypted));
                return stored.getString("access_token").length() > 0
                        && stored.getString("refresh_token").length() > 0;
            } catch (Exception ignored) {
                // A partial/corrupt value cannot represent an authorized account.
                preferences.edit().remove(TOKENS).apply();
                return false;
            }
        }

        boolean storedTokenStillValid() {
            JSONObject stored = stored();
            if (stored == null) return false;
            long createdAt = stored.optLong("created_at", 0L);
            long expiresIn = stored.optLong("expires_in", 0L);
            long now = System.currentTimeMillis() / 1000L;
            return createdAt > 0L && expiresIn > 60L && now < createdAt + expiresIn - 60L;
        }

        String accessToken() {
            JSONObject stored = stored();
            if (stored == null) return null;
            String value = stored.optString("access_token", "");
            return value.length() == 0 ? null : value;
        }

        String refreshToken() {
            JSONObject stored = stored();
            if (stored == null) return null;
            String value = stored.optString("refresh_token", "");
            return value.length() == 0 ? null : value;
        }

        String clientId() {
            JSONObject stored = stored();
            if (stored == null) return null;
            String value = stored.optString("client_id", "");
            return value.length() == 0 ? null : value;
        }

        private JSONObject stored() {
            String encrypted = preferences.getString(TOKENS, null);
            if (encrypted == null || encrypted.length() == 0) return null;
            try {
                return new JSONObject(decrypt(encrypted));
            } catch (Exception ignored) {
                return null;
            }
        }

        void save(JSONObject token) throws Exception {
            JSONObject previous = stored();
            JSONObject stored = new JSONObject();
            stored.put("access_token", token.getString("access_token"));
            String refreshToken = token.optString("refresh_token", "");
            if (refreshToken.length() == 0 && previous != null) {
                refreshToken = previous.optString("refresh_token", "");
            }
            if (refreshToken.length() == 0) throw new IllegalArgumentException("refresh token missing");
            stored.put("refresh_token", refreshToken);
            stored.put("expires_in", token.optLong("expires_in", 0));
            stored.put("created_at", token.optLong("created_at", 0));
            String clientId = token.optString("client_id", "");
            if (clientId.length() == 0) {
                clientId = previous == null ? "" : previous.optString("client_id", "");
            }
            if (clientId.length() > 0) stored.put("client_id", clientId);
            if (!preferences.edit().putString(TOKENS, encrypt(stored.toString())).commit()) {
                throw new IllegalStateException("token storage failed");
            }
        }

        void saveClientId(String clientId) throws Exception {
            JSONObject stored = stored();
            if (stored == null) throw new IllegalStateException("token storage missing");
            stored.put("client_id", clientId);
            if (!preferences.edit().putString(TOKENS, encrypt(stored.toString())).commit()) {
                throw new IllegalStateException("token storage failed");
            }
        }

        void expireAccessToken() {
            JSONObject stored = stored();
            if (stored == null) return;
            try {
                stored.put("created_at", 0L);
                preferences.edit().putString(TOKENS, encrypt(stored.toString())).commit();
            } catch (Exception ignored) {
                // Preserve the encrypted refresh token if expiration bookkeeping fails.
            }
        }

        void clear() {
            preferences.edit().remove(TOKENS).commit();
            try {
                KeyStore store = KeyStore.getInstance("AndroidKeyStore");
                store.load(null);
                if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS);
            } catch (Exception ignored) {
                // The encrypted payload is already gone; alias cleanup is best effort.
            }
        }

        private static String encrypt(String plaintext) throws Exception {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "."
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        }

        private static String decrypt(String ciphertext) throws Exception {
            String[] parts = ciphertext.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException("invalid encrypted token");
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        }

        private static SecretKey getOrCreateKey() throws Exception {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias(KEY_ALIAS)) {
                return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
            }
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            return generator.generateKey();
        }
    }
}
