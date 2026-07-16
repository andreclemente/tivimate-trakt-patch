package com.tivimate.traktpatch.extension;

/** Native detail-open boundary. Provider credentials stay inside TiviMate. */
public final class TraktOnDemandBridge {
    private TraktOnDemandBridge() { }

    public static void onVodInfoRequested(Object params, int xcId) {
        if (params == null || xcId <= 0) return;
        TraktImportCoordinator.requestOpenedMovie(xcId);
    }

    public static void onSeriesInfoRequested(Object params, int xcId) {
        if (params == null || xcId <= 0) return;
        TraktImportCoordinator.requestOpenedSeries(xcId);
    }
}
