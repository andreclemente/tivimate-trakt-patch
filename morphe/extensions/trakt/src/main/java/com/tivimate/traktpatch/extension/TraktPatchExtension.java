package com.tivimate.traktpatch.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Production settings bridge injected into TiviMate's launch activity.
 *
 * <p>TiviMate 5.1.6 uses AndroidX preference screens.  The app's protected
 * implementation does not expose stable source names, so this bridge uses the
 * public AndroidX preference API by reflection.  When the user opens the
 * Settings > Other preference screen, it adds a normal focusable Preference
 * row.  Therefore Android TV's existing Leanback/D-pad navigation owns focus
 * and click handling rather than an overlay.</p>
 */
@SuppressWarnings({"unused", "JavaReflectionMemberAccess"})
public final class TraktPatchExtension {
    private static final String KEY = "tivimate_trakt";
    private static final String TAG = "TiviMateTrakt";
    private static final String TITLE = "Trakt";
    private static final String SUMMARY = "Connect Trakt and sync watched progress";
    private static volatile boolean initialized;
    private static volatile View fallbackRow;

    private TraktPatchExtension() {}

    /** Called by Morphe's injected launch-activity hook. */
    public static synchronized void initialize(Context context) {
        Log.i(TAG, "initialize context=" + (context == null ? "null" : context.getClass().getName()));
        if (initialized || context == null) return;
        Application application = context instanceof Application
                ? (Application) context
                : (context.getApplicationContext() instanceof Application
                        ? (Application) context.getApplicationContext() : null);
        if (application == null) return;
        initialized = true;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}

            @Override public void onActivityResumed(final Activity activity) {
                View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
                if (decor == null) return;

                // Settings → Other is a fragment transaction inside the already
                // resumed MainActivity, so an onResume-only check misses it.
                // Recheck after every decor layout; addPreference is idempotent
                // because installIntoFragment first looks up KEY.
                decor.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                                         int oldLeft, int oldTop, int oldRight, int oldBottom) {
                        installOtherPreference(activity);
                    }
                });
                decor.postDelayed(new Runnable() {
                    @Override public void run() { installOtherPreference(activity); }
                }, 250L);
            }
        });
    }

    private static void installOtherPreference(Activity activity) {
        View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (decor == null) return;
        if (containsVisibleText(decor, "Other") && containsVisibleText(decor, "VOD")) {
            installFallbackRow(activity, decor);
        } else {
            removeFallbackRow();
        }
        try {
            Method managerMethod = activity.getClass().getMethod("getSupportFragmentManager");
            Object manager = managerMethod.invoke(activity);
            Method fragmentsMethod = manager.getClass().getMethod("getFragments");
            Object value = fragmentsMethod.invoke(manager);
            if (!(value instanceof List)) return;
            for (Object fragment : (List<?>) value) installIntoFragment(activity, fragment);
        } catch (ReflectiveOperationException ignored) {
            // Not TiviMate's AndroidX settings activity.
        }
    }

    private static boolean containsVisibleText(View view, String text) {
        if (view instanceof TextView && view.getVisibility() == View.VISIBLE
                && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsVisibleText(group.getChildAt(i), text)) return true;
            }
        }
        return false;
    }

    private static TextView findVisibleText(View view, String text) {
        if (view instanceof TextView && view.getVisibility() == View.VISIBLE
                && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView result = findVisibleText(group.getChildAt(i), text);
                if (result != null) return result;
            }
        }
        return null;
    }

    /**
     * Leanback settings in this protected build do not surface a public
     * PreferenceScreen. Add the row to the same settings pane only while its
     * visible Other/VOD content proves the screen is active.
     */
    private static void installFallbackRow(final Activity activity, View decor) {
        if (fallbackRow != null && fallbackRow.getParent() != null) return;
        if (!(decor instanceof FrameLayout)) return;
        TextView vod = findVisibleText(decor, "VOD");
        if (vod == null) return;
        int[] location = new int[2];
        vod.getLocationOnScreen(location);
        int panelWidth = Math.max(320, decor.getWidth() - location[0] - 48);
        int rowHeight = Math.max(72, vod.getHeight() * 2);
        TextView row = new TextView(activity);
        row.setTag(KEY);
        row.setText(TITLE + "\n" + SUMMARY);
        row.setTextSize(18f);
        row.setTextColor(0xffffffff);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(32, 0, 24, 0);
        row.setBackgroundColor(0xff424242);
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showStatus(activity); }
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(panelWidth, rowHeight,
                Gravity.TOP | Gravity.END);
        params.topMargin = location[1] + vod.getHeight();
        params.rightMargin = 48;
        ((FrameLayout) decor).addView(row, params);
        fallbackRow = row;
        Log.i(TAG, "added Other fallback row");
    }

    private static void removeFallbackRow() {
        View row = fallbackRow;
        if (row != null && row.getParent() instanceof ViewGroup) {
            ((ViewGroup) row.getParent()).removeView(row);
        }
        fallbackRow = null;
    }

    private static void installIntoFragment(final Activity activity, Object fragment) {
        if (fragment == null) return;
        try {
            Method childrenMethod = fragment.getClass().getMethod("getChildFragmentManager");
            Object childManager = childrenMethod.invoke(fragment);
            Method fragmentsMethod = childManager.getClass().getMethod("getFragments");
            Object children = fragmentsMethod.invoke(childManager);
            if (children instanceof List) {
                for (Object child : (List<?>) children) installIntoFragment(activity, child);
            }
        } catch (ReflectiveOperationException ignored) {
            // Leaf fragment.
        }

        try {
            Method screenMethod = fragment.getClass().getMethod("getPreferenceScreen");
            Object screen = screenMethod.invoke(fragment);
            if (screen == null) return;
            Method titleMethod = screen.getClass().getMethod("getTitle");
            Object title = titleMethod.invoke(screen);
            Log.i(TAG, "preference fragment=" + fragment.getClass().getName() + " screen="
                    + screen.getClass().getName() + " title=" + title);
            if (!isOtherScreen(screen)) return;
            Method findMethod = screen.getClass().getMethod("findPreference", CharSequence.class);
            if (findMethod.invoke(screen, KEY) != null) return;

            Class<?> preferenceClass = Class.forName("androidx.preference.Preference");
            Constructor<?> constructor = preferenceClass.getConstructor(Context.class, android.util.AttributeSet.class);
            Object preference = constructor.newInstance(activity, null);
            preferenceClass.getMethod("setKey", String.class).invoke(preference, KEY);
            preferenceClass.getMethod("setTitle", CharSequence.class).invoke(preference, TITLE);
            preferenceClass.getMethod("setSummary", CharSequence.class).invoke(preference, SUMMARY);
            Class<?> listenerClass = Class.forName("androidx.preference.Preference$OnPreferenceClickListener");
            Object listener = Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class<?>[] { listenerClass },
                    new InvocationHandler() {
                        @Override public Object invoke(Object proxy, Method method, Object[] args) {
                            if ("onPreferenceClick".equals(method.getName())) showStatus(activity);
                            return Boolean.TRUE;
                        }
                    });
            preferenceClass.getMethod("setOnPreferenceClickListener", listenerClass).invoke(preference, listener);
            screen.getClass().getMethod("addPreference", preferenceClass).invoke(screen, preference);
        } catch (ReflectiveOperationException ignored) {
            // Different TiviMate build; patch compatibility limits this to mapped 5.1.6.
        }
    }

    private static boolean isOtherScreen(Object screen) throws ReflectiveOperationException {
        Method titleMethod = screen.getClass().getMethod("getTitle");
        Object title = titleMethod.invoke(screen);
        if (TITLE.equals(title)) return false;
        if ("Other".contentEquals(title instanceof CharSequence ? (CharSequence) title : "")) return true;

        // 5.1.6 Other screen contains the recordings-folder preference. Keep a
        // fallback for builds where PreferenceScreen itself has no title.
        Method countMethod = screen.getClass().getMethod("getPreferenceCount");
        Method getMethod = screen.getClass().getMethod("getPreference", int.class);
        int count = ((Number) countMethod.invoke(screen)).intValue();
        for (int i = 0; i < count; i++) {
            Object item = getMethod.invoke(screen, i);
            Object itemTitle = item.getClass().getMethod("getTitle").invoke(item);
            if ("Recordings".contentEquals(itemTitle instanceof CharSequence ? (CharSequence) itemTitle : "")) return true;
        }
        return false;
    }

    private static void showStatus(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Trakt")
                .setMessage("Not connected. Device-code OAuth is next; watched/progress sync remains disabled.")
                .setPositiveButton("OK", null)
                .show();
    }
}
