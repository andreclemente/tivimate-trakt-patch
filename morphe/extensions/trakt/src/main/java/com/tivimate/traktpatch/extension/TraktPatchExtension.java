package com.tivimate.traktpatch.extension;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Activity polledActivity;
    private static volatile boolean windowPollingStarted;
    private static volatile boolean windowScanLogged;
    private static volatile boolean adapterLogged;
    private static volatile boolean nativePreferenceInstalled;

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
        startWindowPolling();
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}

            @Override public void onActivityResumed(final Activity activity) {
                Log.i(TAG, "activity resumed=" + activity.getClass().getName());
                View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
                if (decor == null) return;

                scheduleOtherScreenPolling(activity);
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

    private static synchronized void startWindowPolling() {
        if (windowPollingStarted) return;
        windowPollingStarted = true;
        MAIN.post(new Runnable() {
            @Override public void run() {
                scanApplicationWindows();
                MAIN.postDelayed(this, 300L);
            }
        });
    }

    private static void scanApplicationWindows() {
        try {
            Class<?> globalClass = Class.forName("android.view.WindowManagerGlobal");
            Object global = globalClass.getMethod("getInstance").invoke(null);
            Field viewsField = globalClass.getDeclaredField("mViews");
            viewsField.setAccessible(true);
            Object views = viewsField.get(global);
            if (!(views instanceof List)) return;
            for (Object item : (List<?>) views) {
                if (!(item instanceof View)) continue;
                View decor = (View) item;
                installOtherRow(decor.getContext(), decor);
                Activity activity = findActivity(decor.getContext());
                if (activity != null) {
                    if (!windowScanLogged) {
                        windowScanLogged = true;
                        Log.i(TAG, "window poll activity=" + activity.getClass().getName());
                    }
                    installOtherPreference(activity);
                }
            }
        } catch (Throwable error) {
            if (!windowScanLogged) Log.w(TAG, "window poll unavailable", error);
        }
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            Context next = ((ContextWrapper) current).getBaseContext();
            if (next == current) break;
            current = next;
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    private static void scheduleOtherScreenPolling(final Activity activity) {
        if (polledActivity == activity) return;
        polledActivity = activity;
        MAIN.post(new Runnable() {
            @Override public void run() {
                if (polledActivity != activity || activity.isFinishing()) return;
                installOtherPreference(activity);
                MAIN.postDelayed(this, 300L);
            }
        });
    }

    private static void installOtherPreference(Activity activity) {
        View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (decor == null) return;
        installOtherRow(activity, decor);
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

    private static void installOtherRow(Context context, View decor) {
        if (!containsVisibleText(decor, "Other") || !containsVisibleText(decor, "VOD")) return;
        installNativePreference(context, decor);
    }

    /**
     * TiviMate renders this pane using Leanback's PreferenceGroupAdapter.  Its
     * adapter keeps the real PreferenceScreen as a field; adding the Preference
     * there lets Leanback update its own list, focus, and click handling.
     */
    private static void installNativePreference(final Context context, View root) {
        if (nativePreferenceInstalled) return;
        Object adapter = findPreferenceAdapter(root);
        if (adapter == null) return;
        Object screen = findPreferenceScreen(adapter);
        if (screen == null) return;
        if (!adapterLogged) {
            adapterLogged = true;
            logPreferenceApi(adapter, screen);
            return;
        }
        try {
            Class<?> preferenceClass = Class.forName("androidx.preference.Preference");
            Object preference = Class.forName("com.tivimate.traktpatch.extension.NativeTraktPreference")
                    .getConstructor(Context.class).newInstance(context);
            Object vodPreference = findPreferenceByTitle(screen, "VOD");
            if (vodPreference == null) return;
            copyPreferencePresentation(vodPreference, preference);
            // Decrypt once: a single saved token state must drive both labels.
            // This prevents a damaged/rotated keystore entry from yielding a
            // contradictory connected title with a disconnected summary.
            final boolean connected = TraktDeviceAuth.isConnected(context);
            setPreferenceField(preferenceClass, preference, "ˑﾞ", KEY);
            setPreferenceField(preferenceClass, preference, "ـˆ", connected
                    ? "Trakt (Connected)" : TITLE);
            setPreferenceField(preferenceClass, preference, "ᴵʼ", connected
                    ? "Account connected — watched-progress sync coming next" : SUMMARY);
            Method add = screen.getClass().getSuperclass().getMethod("ᵢʿ", preferenceClass);
            add.invoke(screen, preference);
            nativePreferenceInstalled = true;
            Log.i(TAG, "added native preference to Leanback PreferenceScreen");
        } catch (ReflectiveOperationException error) {
            Log.w(TAG, "native PreferenceScreen insertion failed", error);
        }
    }

    private static Object findPreferenceByTitle(Object screen, String wantedTitle) {
        for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(screen);
                    if (!(value instanceof List)) continue;
                    for (Object candidate : (List<?>) value) {
                        if (candidate != null && hasPreferenceTitle(candidate, wantedTitle)) return candidate;
                    }
                } catch (IllegalAccessException ignored) { }
            }
        }
        return null;
    }

    private static boolean hasPreferenceTitle(Object preference, String wantedTitle) {
        for (Class<?> type = preference.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getParameterTypes().length != 0
                        || !CharSequence.class.isAssignableFrom(method.getReturnType())) continue;
                try {
                    method.setAccessible(true);
                    Object value = method.invoke(preference);
                    if (wantedTitle.equals(value)) return true;
                } catch (ReflectiveOperationException ignored) { }
            }
        }
        return false;
    }

    /**
     * TiviMate assigns its selected-state drawable through the VOD row's layout
     * resource. Copy only resource-id fields; identity, order and title remain
     * unique to Trakt.
     */
    private static void copyPreferencePresentation(Object source, Object target)
            throws IllegalAccessException {
        for (Class<?> type = source.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != Integer.TYPE) continue;
                if (!field.getDeclaringClass().isInstance(target)) continue;
                field.setAccessible(true);
                int value = field.getInt(source);
                if (value < 0x01000000) continue;
                try {
                    Field targetField = field.getDeclaringClass().getDeclaredField(field.getName());
                    targetField.setAccessible(true);
                    targetField.setInt(target, value);
                } catch (NoSuchFieldException ignored) {
                    // Concrete VOD-only fields are not part of Preference.
                }
            }
        }
    }

    private static void setPreferenceField(Class<?> preferenceClass, Object preference, String name, Object value)
            throws ReflectiveOperationException {
        Field field = preferenceClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(preference, value);
    }

    private static void logPreferenceApi(Object adapter, Object screen) {
        StringBuilder log = new StringBuilder("Native preference API screen=")
                .append(screen.getClass().getName());
        for (Method method : screen.getClass().getDeclaredMethods()) {
            log.append("\n screen ").append(method.getName()).append('(');
            for (Class<?> parameter : method.getParameterTypes()) log.append(parameter.getName()).append(',');
            log.append(") -> ").append(method.getReturnType().getName());
        }
        for (Field field : adapter.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(adapter);
                if (value instanceof List) log.append("\n adapter-list ").append(field.getName())
                        .append(" size=").append(((List<?>) value).size());
            } catch (IllegalAccessException ignored) { }
        }
        try {
            Class<?> preference = Class.forName("androidx.preference.Preference");
            for (Method method : preference.getDeclaredMethods()) {
                log.append("\n pref ").append(method.getName()).append('(');
                for (Class<?> parameter : method.getParameterTypes()) log.append(parameter.getName()).append(',');
                log.append(") -> ").append(method.getReturnType().getName());
            }
        } catch (ClassNotFoundException ignored) { }
        Log.i(TAG, log.toString());
    }

    private static Object findPreferenceAdapter(View view) {
        try {
            Method adapterMethod = view.getClass().getMethod("getAdapter");
            Object adapter = adapterMethod.invoke(view);
            if (adapter != null && findPreferenceScreen(adapter) != null) return adapter;
        } catch (ReflectiveOperationException ignored) { }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Object adapter = findPreferenceAdapter(group.getChildAt(i));
                if (adapter != null) return adapter;
            }
        }
        return null;
    }

    private static Object findPreferenceScreen(Object adapter) {
        for (Class<?> type = adapter.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!"androidx.preference.PreferenceScreen".equals(field.getType().getName())) continue;
                try {
                    field.setAccessible(true);
                    return field.get(adapter);
                } catch (IllegalAccessException ignored) { }
            }
        }
        return null;
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

    private static void showStatus(Context context) {
        Toast.makeText(context, "Trakt: not connected", Toast.LENGTH_LONG).show();
    }
}
