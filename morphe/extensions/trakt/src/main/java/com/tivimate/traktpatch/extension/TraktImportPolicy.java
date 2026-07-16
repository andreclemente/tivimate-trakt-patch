package com.tivimate.traktpatch.extension;

import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        String cleaned = value.replaceAll("\\p{Cf}+", "")
                // Catalog/language labels such as IN-TU, AR-DUB, AR-SUBS and 4K-AR are
                // not part of the media title. Keep this deliberately limited to short
                // code segments before an explicit catalog separator.
                .replaceFirst("(?i)^\\s*(?:(?:[a-z0-9]{1,4})(?:[-/][a-z0-9]{1,4})*|top)\\s*[-:|–—]\\s*", "")
                .replaceFirst("(?i)\\s+[a-z]{2,3}\\s+audio(?=\\s*\\((?:19|20)[0-9]{2}\\))", "")
                // A parenthesized catalog year is a boundary; providers often append a
                // country code, localized script, or display alias after it.
                .replaceFirst("\\s*\\((?:19|20)[0-9]{2}\\).*$", "");
        String ascii = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.US)
                .replaceFirst("\\s*(?:19|20)[0-9]{2}(?:\\s*\\([a-z]{2,3}\\))?\\s*$", "");
        return ascii.replaceAll("[^a-z0-9]+", " ").trim().replaceAll(" +", " ");
    }

    public static boolean shortlist(String localTitle, int localYear,
                                    String remoteTitle, int remoteYear) {
        if (!normalizedTitle(localTitle).equals(normalizedTitle(remoteTitle))) return false;
        return localYear <= 0 || remoteYear <= 0 || localYear == remoteYear;
    }

    public static boolean shortlistSeries(String localTitle, int localYear,
                                          String remoteTitle, int remoteYear) {
        // Series year labels are frequently announcement/catalog years. Stable provider
        // identity remains mandatory before any write.
        return normalizedTitle(localTitle).equals(normalizedTitle(remoteTitle));
    }

    /**
     * A stable-ID-confirmed catalog entry authorizes provider siblings only when both
     * carry the same normalized title and the same explicit year. This deliberately
     * fails closed for unknown years so remakes and same-title series cannot leak state.
     */
    public static boolean confirmedSibling(String localTitle, int localYear,
                                           String remoteTitle, int remoteYear) {
        return localYear > 0 && remoteYear > 0 && localYear == remoteYear
                && normalizedTitle(localTitle).equals(normalizedTitle(remoteTitle));
    }

    /** Immutable normalized-title lookup with the same matching rules as {@link #shortlist}. */
    public static final class ShortlistIndex {
        private final Map<String, YearRule> titles;
        private final boolean ignoreYear;

        private ShortlistIndex(Map<String, YearRule> titles, boolean ignoreYear) {
            this.titles = titles;
            this.ignoreYear = ignoreYear;
        }

        public boolean contains(String localTitle, int localYear) {
            YearRule rule = titles.get(normalizedTitle(localTitle));
            return rule != null && (ignoreYear || localYear <= 0 || rule.wildcard
                    || rule.years.contains(localYear));
        }
    }

    private static final class YearRule {
        final boolean wildcard;
        final Set<Integer> years;

        YearRule(boolean wildcard, Set<Integer> years) {
            this.wildcard = wildcard;
            this.years = Collections.unmodifiableSet(new HashSet<>(years));
        }
    }

    public static ShortlistIndex shortlistIndex(List<String> remoteTitles,
                                                List<Integer> remoteYears) {
        return shortlistIndex(remoteTitles, remoteYears, false);
    }

    public static ShortlistIndex shortlistIndex(List<String> remoteTitles,
                                                List<Integer> remoteYears,
                                                boolean ignoreYear) {
        if (remoteTitles.size() != remoteYears.size()) {
            throw new IllegalArgumentException("title/year size mismatch");
        }
        Map<String, Set<Integer>> knownYears = new HashMap<>();
        Set<String> wildcardTitles = new HashSet<>();
        for (int i = 0; i < remoteTitles.size(); i++) {
            String title = normalizedTitle(remoteTitles.get(i));
            int year = remoteYears.get(i);
            if (year <= 0) wildcardTitles.add(title);
            else {
                Set<Integer> years = knownYears.get(title);
                if (years == null) {
                    years = new HashSet<>();
                    knownYears.put(title, years);
                }
                years.add(year);
            }
        }
        Map<String, YearRule> rules = new HashMap<>();
        Set<String> allTitles = new HashSet<>(knownYears.keySet());
        allTitles.addAll(wildcardTitles);
        for (String title : allTitles) {
            Set<Integer> years = knownYears.get(title);
            rules.put(title, new YearRule(wildcardTitles.contains(title),
                    years == null ? Collections.<Integer>emptySet() : years));
        }
        return new ShortlistIndex(Collections.unmodifiableMap(rules), ignoreYear);
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

    /** Prefer the provider's bounded stream duration; Trakt runtimes are often nominal. */
    public static long selectWatchedDuration(long localDuration, long providerDuration,
                                             long traktDuration) {
        long trusted = traktDuration > 0L && traktDuration < 86_400_000L ? traktDuration : 0L;
        // Xtream duration_secs describes the actual provider asset. A provider may carry
        // a shortened cut (for example 13 minutes versus Trakt's nominal 23), and TiviMate
        // only renders native completion against that real stream duration.
        if (providerDuration >= 300_000L && providerDuration < 86_400_000L) {
            return providerDuration;
        }
        if (trusted == 0L) return localDuration > 0L ? localDuration : Math.max(0L, providerDuration);
        if (plausibleDuration(providerDuration, trusted)) return providerDuration;
        if (plausibleDuration(localDuration, trusted)) return localDuration;
        return trusted;
    }

    private static boolean plausibleDuration(long candidate, long trusted) {
        return candidate > 0L && candidate >= trusted / 2L && candidate <= trusted * 2L;
    }

    /** Unwatched progress is monotonic; watched uses TiviMate's visible native marker state. */
    public static long mergePosition(long localPosition, long duration,
                                     double remotePercent, boolean watched) {
        long safeLocal = Math.max(0L, localPosition);
        long safeDuration = Math.max(0L, duration);
        if (safeDuration == 0L) return safeLocal;
        // At exact equality TiviMate suppresses the card's native progress line. Keeping
        // the position one millisecond below duration renders a visually full watched
        // marker while remaining >99.99% complete for Trakt/native completion semantics.
        if (watched) return safeDuration > 1L ? safeDuration - 1L : safeDuration;
        double bounded = Math.max(0.0d, Math.min(100.0d, remotePercent));
        long remote = Math.round(safeDuration * bounded / 100.0d);
        return Math.max(safeLocal, remote);
    }
}
