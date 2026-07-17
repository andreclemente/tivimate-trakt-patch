package com.tivimate.traktpatch.extension;

/** Native detail-open boundary. Provider credentials stay inside TiviMate. */
public final class TraktOnDemandBridge {
    private TraktOnDemandBridge() { }

    public static void onVodInfoRequested(Object params, int xcId) {
        if (params == null || xcId <= 0) return;
        long playlistId = playlistId(params);
        if (playlistId <= 0L) {
            TraktImportCoordinator.requestImport();
            return;
        }
        TraktImportCoordinator.requestOpenedMovie(playlistId, xcId);
    }

    public static void onSeriesInfoRequested(Object params, int xcId) {
        if (params == null || xcId <= 0) return;
        long playlistId = playlistId(params);
        if (playlistId <= 0L) {
            TraktImportCoordinator.requestImport();
            return;
        }
        TraktImportCoordinator.requestOpenedSeries(playlistId, xcId);
    }

    /** Params has one transient primitive long: TiviMate's database playlist id. */
    private static long playlistId(Object params) {
        long result = 0L;
        int candidateCount = 0;
        for (java.lang.reflect.Field field : params.getClass().getDeclaredFields()) {
            if (field.getType() == long.class
                    && !java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && java.lang.reflect.Modifier.isTransient(field.getModifiers())) {
                if (++candidateCount > 1) return 0L;
                try {
                    field.setAccessible(true);
                    result = field.getLong(params);
                } catch (ReflectiveOperationException | SecurityException ignored) {
                    return 0L;
                }
            }
        }
        return candidateCount == 1 && result > 0L ? result : 0L;
    }
}
