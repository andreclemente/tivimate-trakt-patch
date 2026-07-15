package com.tivimate.traktpatch.extension;

import java.text.Normalizer;
import java.util.Locale;

/** Pure import matching and monotonic-merge rules, kept Android-independent for tests. */
public final class TraktImportPolicy {
    private TraktImportPolicy() { }

    public static String stableTmdb(Object value) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.matches("[1-9][0-9]*") ? text : "";
    }

    public static String stableImdb(Object value) {
        String text = value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.US);
        return text.matches("tt[0-9]+") ? text : "";
    }

    public static boolean sameStableId(String leftTmdb, String leftImdb,
                                       String rightTmdb, String rightImdb) {
        String aTmdb = stableTmdb(leftTmdb);
        String bTmdb = stableTmdb(rightTmdb);
        String aImdb = stableImdb(leftImdb);
        String bImdb = stableImdb(rightImdb);
        if (!aTmdb.isEmpty() && !bTmdb.isEmpty() && !aTmdb.equals(bTmdb)) return false;
        if (!aImdb.isEmpty() && !bImdb.isEmpty() && !aImdb.equals(bImdb)) return false;
        return (!aTmdb.isEmpty() && !bTmdb.isEmpty())
                || (!aImdb.isEmpty() && !bImdb.isEmpty());
    }

    public static String normalizedTitle(String value) {
        if (value == null) return "";
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.US)
                .replaceFirst("\\s*\\(?(?:19|20)[0-9]{2}\\)?\\s*$", "");
        return ascii.replaceAll("[^a-z0-9]+", " ").trim().replaceAll(" +", " ");
    }

    public static boolean shortlist(String localTitle, int localYear,
                                    String remoteTitle, int remoteYear) {
        if (!normalizedTitle(localTitle).equals(normalizedTitle(remoteTitle))) return false;
        return localYear <= 0 || remoteYear <= 0 || localYear == remoteYear;
    }

    public static long parseClockDurationMs(String value) {
        if (value == null) return 0L;
        String[] parts = value.trim().split(":", -1);
        if (parts.length != 3) return 0L;
        for (String part : parts) {
            if (!part.matches("[0-9]+")) return 0L;
        }
        try {
            long hours = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            long seconds = Long.parseLong(parts[2]);
            if (hours > 23L || minutes > 59L || seconds > 59L) return 0L;
            long totalSeconds = hours * 3600L + minutes * 60L + seconds;
            return totalSeconds > 0L ? totalSeconds * 1000L : 0L;
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    /** Watched dominates; otherwise progress can only move forward. */
    public static long mergePosition(long localPosition, long duration,
                                     double remotePercent, boolean watched) {
        long safeLocal = Math.max(0L, localPosition);
        long safeDuration = Math.max(0L, duration);
        if (safeDuration == 0L) return safeLocal;
        if (watched) return Math.max(safeLocal, safeDuration);
        double bounded = Math.max(0.0d, Math.min(100.0d, remotePercent));
        long remote = Math.round(safeDuration * bounded / 100.0d);
        return Math.max(safeLocal, remote);
    }
}
