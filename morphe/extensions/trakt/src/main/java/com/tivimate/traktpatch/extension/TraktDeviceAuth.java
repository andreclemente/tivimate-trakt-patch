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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();

    private TraktDeviceAuth() { }

    public static boolean isConnected(Context context) {
        return new TokenStore(context).hasValidToken();
    }

    public static void open(final Context context) {
        android.util.Log.i("TiviMateTrakt", "device authorization entry");
        if (isConnected(context)) {
            final AuthDialog connected = new AuthDialog(context);
            connected.showDisconnect(new Runnable() {
                @Override public void run() {
                    new TokenStore(context).clear();
                    connected.dismiss();
                    Toast.makeText(context, "Trakt disconnected", Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
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
                            poll(context, dialog, deviceCode, intervalSeconds * 1000L, expiresAt);
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
                             final long delayMillis, final long expiresAt) {
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
                    new TokenStore(context).save(token);
                    MAIN.post(new Runnable() {
                        @Override public void run() {
                            if (dialog.isShowing()) dialog.dismiss();
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
                                poll(context, dialog, deviceCode, nextDelay, expiresAt);
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
        if (!(error instanceof DeviceAuthorizationException)) return false;
        String code = ((DeviceAuthorizationException) error).code;
        return "authorization_pending".equals(code) || "slow_down".equals(code);
    }

    private static JSONObject post(String endpoint, JSONObject payload) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
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
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String text = read(stream);
        if (status < 200 || status >= 300) {
            String code = DEVICE_TOKEN_URL.equals(endpoint) && status == 400 && text.length() == 0
                    ? "authorization_pending"
                    : new JSONObject(text.length() == 0 ? "{}" : text)
                            .optString("error", "HTTP " + status);
            throw new DeviceAuthorizationException(status, code);
        }
        return new JSONObject(text);
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) value.append(line);
        }
        return value.toString();
    }

    private static String message(Exception error) {
        String value = error.getMessage();
        return value == null || value.length() == 0 ? "network error" : value;
    }

    private static final class DeviceAuthorizationException extends Exception {
        final String code;

        DeviceAuthorizationException(int status, String code) {
            super("HTTP " + status + ": " + code);
            this.code = code;
        }
    }

    /** Custom content avoids TiviMate's protected theme breaking AlertDialog inflation. */
    private static final class AuthDialog {
        private final Dialog dialog;
        private final TextView text;
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
            action.setVisibility(android.view.View.GONE);
            action.setAllCaps(false);
            content.addView(text);
            content.addView(action, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            dialog.setContentView(content);
        }

        void show(String title, String message) {
            action.setVisibility(android.view.View.GONE);
            action.setOnClickListener(null);
            text.setText(title + "\n\n" + message + "\n\nPress Back to cancel");
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.rgb(32, 32, 32)));
                window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        }

        void showDisconnect(final Runnable disconnect) {
            text.setText("Trakt connected\n\nRemove this account from TiviMate?");
            action.setText("Disconnect Trakt");
            action.setVisibility(android.view.View.VISIBLE);
            action.setOnClickListener(v -> disconnect.run());
            dialog.show();
            action.requestFocus();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.rgb(32, 32, 32)));
                window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        }

        void setMessage(String message) { text.setText("Connect Trakt\n\n" + message + "\n\nPress Back to cancel"); }
        boolean isShowing() { return dialog.isShowing(); }
        void dismiss() { dialog.dismiss(); }
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

        void save(JSONObject token) throws Exception {
            JSONObject stored = new JSONObject();
            stored.put("access_token", token.getString("access_token"));
            stored.put("refresh_token", token.getString("refresh_token"));
            stored.put("expires_in", token.optLong("expires_in", 0));
            stored.put("created_at", token.optLong("created_at", 0));
            if (!preferences.edit().putString(TOKENS, encrypt(stored.toString())).commit()) {
                throw new IllegalStateException("token storage failed");
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
