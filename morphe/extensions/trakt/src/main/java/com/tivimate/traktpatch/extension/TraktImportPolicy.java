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
        String cleaned = value.replaceFirst("(?i)^\\s*(?:[a-z]{2,4}(?:/[a-z]{2,4})?|top|ar-subs)\\s*[-:]\\s*", "");
        String ascii = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.US)
                .replaceFirst("\\s*\\(?(?:19|20)[0-9]{2}\\)?\\s*$", "");
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

    /** Prefer native/provider duration only when plausible against Trakt's full runtime. */
    public static long selectWatchedDuration(long localDuration, long providerDuration,
                                             long traktDuration) {
        long trusted = traktDuration > 0L && traktDuration < 86_400_000L ? traktDuration : 0L;
        if (trusted == 0L) return localDuration > 0L ? localDuration : Math.max(0L, providerDuration);
        if (plausibleDuration(localDuration, trusted)) return localDuration;
        if (plausibleDuration(providerDuration, trusted)) return providerDuration;
        return trusted;
    }

    private static boolean plausibleDuration(long candidate, long trusted) {
        return candidate > 0L && candidate >= trusted / 2L && candidate <= trusted * 2L;
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
