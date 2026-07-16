import subprocess
import tempfile
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension/TraktImportPolicy.java"


class TraktImportPolicyTest(unittest.TestCase):
    def run_policy(self, method, *args):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            harness = root / "Harness.java"
            harness.write_text("""
import com.tivimate.traktpatch.extension.TraktImportPolicy;
import java.util.ArrayList;
import java.util.List;
public final class Harness {
  public static void main(String[] a) {
    if (a[0].equals("ids")) System.out.print(TraktImportPolicy.sameStableId(a[1],a[2],a[3],a[4]));
    if (a[0].equals("shortlist")) System.out.print(TraktImportPolicy.shortlist(a[1], Integer.parseInt(a[2]), a[3], Integer.parseInt(a[4])));
    if (a[0].equals("shortlistSeries")) System.out.print(TraktImportPolicy.shortlistSeries(a[1], Integer.parseInt(a[2]), a[3], Integer.parseInt(a[4])));
    if (a[0].equals("confirmedSibling")) System.out.print(TraktImportPolicy.confirmedSibling(a[1], Integer.parseInt(a[2]), a[3], Integer.parseInt(a[4])));
    if (a[0].equals("merge")) System.out.print(TraktImportPolicy.mergePosition(Long.parseLong(a[1]),Long.parseLong(a[2]),Double.parseDouble(a[3]),Boolean.parseBoolean(a[4])));
    if (a[0].equals("clock")) System.out.print(TraktImportPolicy.parseClockDurationMs(a[1]));
    if (a[0].equals("watchedDuration")) System.out.print(TraktImportPolicy.selectWatchedDuration(
        Long.parseLong(a[1]), Long.parseLong(a[2]), Long.parseLong(a[3])));
    if (a[0].equals("index") || a[0].equals("indexSeries")) {
      List<String> titles = new ArrayList<>();
      List<Integer> years = new ArrayList<>();
      for (int i = 3; i < a.length; i += 2) {
        titles.add(a[i]);
        years.add(Integer.parseInt(a[i + 1]));
      }
      TraktImportPolicy.ShortlistIndex index = TraktImportPolicy.shortlistIndex(titles, years,
          a[0].equals("indexSeries"));
      System.out.print(index.contains(a[1], Integer.parseInt(a[2])));
    }
  }
}
""")
            built = subprocess.run(["javac", "-d", str(root), str(SOURCE), str(harness)], text=True, capture_output=True)
            self.assertEqual(built.returncode, 0, built.stderr)
            return subprocess.run(["java", "-cp", str(root), "Harness", method, *args], text=True, capture_output=True)

    def test_only_stable_provider_ids_confirm_identity(self):
        self.assertEqual(self.run_policy("ids", "42", "", "42", "").stdout, "true")
        self.assertEqual(self.run_policy("ids", "", "tt00123", "", "TT00123").stdout, "true")
        self.assertEqual(self.run_policy("ids", "42", "tt00123", "43", "tt00123").stdout, "false")
        self.assertEqual(self.run_policy("ids", "42", "tt00123", "42", "tt00999").stdout, "false")
        self.assertEqual(self.run_policy("ids", "0", "title", "0", "title").stdout, "false")

    def test_title_and_year_only_shortlist(self):
        self.assertEqual(self.run_policy("shortlist", "Amélie (2001)", "0", "Amelie 2001", "0").stdout, "true")
        self.assertEqual(self.run_policy("shortlist", "Alien", "1979", "Alien", "2024").stdout, "false")
        self.assertEqual(self.run_policy("shortlistSeries", "\u200eIR | Among Us (2026) (US)", "2026", "Among Us", "2026").stdout, "true")

    def test_confirmed_sibling_requires_exact_normalized_title_and_known_equal_year(self):
        self.assertEqual(self.run_policy("confirmedSibling", "AR-DUB - Despicable Me 4 (2024)", "2024", "Despicable Me 4", "2024").stdout, "true")
        self.assertEqual(self.run_policy("confirmedSibling", "4K-AR - Despicable Me 4 (2024)", "2024", "Despicable Me 4", "2024").stdout, "true")
        self.assertEqual(self.run_policy("confirmedSibling", "GR - Despicable Me 4 GR Audio (2024)", "2024", "Despicable Me 4", "2024").stdout, "true")
        self.assertEqual(self.run_policy("confirmedSibling", "IR - Despicable Me 4 (2024) من نفرت انگیز ۴", "2024", "Despicable Me 4", "2024").stdout, "true")
        self.assertEqual(self.run_policy("confirmedSibling", "\u200eIR | Among Us (2026) (US)", "2026", "Among Us", "2026").stdout, "true")
        self.assertEqual(self.run_policy("confirmedSibling", "Among Us (2023)", "2023", "Among Us", "2026").stdout, "false")
        self.assertEqual(self.run_policy("confirmedSibling", "Among Us", "0", "Among Us", "2026").stdout, "false")
        self.assertEqual(self.run_policy("confirmedSibling", "Among Us: Aftershow (2026)", "2026", "Among Us", "2026").stdout, "false")

    def test_indexed_shortlist_preserves_title_and_year_semantics(self):
        targets = ("Amélie (2001)", "2001", "Alien", "1979", "Arrival", "0")
        cases = (
            (("Amelie 2001", "2001"), "true"),
            (("Amelie", "0"), "true"),
            (("Alien", "2024"), "false"),
            (("Alien", "0"), "true"),
            (("Arrival", "2024"), "true"),
            (("Missing", "0"), "false"),
        )
        for local, expected in cases:
            with self.subTest(local=local):
                result = self.run_policy("index", *local, *targets)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(result.stdout, expected)

        result = self.run_policy("indexSeries", "Among Us", "2026", "Among Us", "2023")
        self.assertEqual(result.stdout, "true")

    def test_clock_duration_is_bounded_and_overflow_safe(self):
        self.assertEqual(self.run_policy("clock", "01:30:00").stdout, "5400000")
        for invalid in ("24:00:00", "01:60:00", "01:00:60", "-1:00:00", "999999999999999999:00:00", "1:2"):
            self.assertEqual(self.run_policy("clock", invalid).stdout, "0")

    def test_monotonic_merge_and_native_watched_marker(self):
        self.assertEqual(self.run_policy("merge", "60000", "100000", "25", "false").stdout, "60000")
        self.assertEqual(self.run_policy("merge", "10000", "100000", "25", "false").stdout, "25000")
        # TiviMate hides its native progress marker at exact position == duration.
        # One millisecond below duration renders a visually full native watched line.
        self.assertEqual(self.run_policy("merge", "10000", "100000", "2", "true").stdout, "99999")
        self.assertEqual(self.run_policy("merge", "100000", "100000", "100", "true").stdout, "99999")
        self.assertEqual(self.run_policy("merge", "10000", "0", "100", "true").stdout, "10000")
        self.assertEqual(self.run_policy("merge", "120000", "100000", "25", "false").stdout, "120000")
        self.assertEqual(self.run_policy("merge", "120000", "100000", "2", "true").stdout, "99999")

    def test_watched_duration_prefers_bounded_provider_stream_duration(self):
        self.assertEqual(self.run_policy("watchedDuration", "110000", "110000", "6300000").stdout, "6300000")
        self.assertEqual(self.run_policy("watchedDuration", "6000000", "5640000", "6300000").stdout, "5640000")
        self.assertEqual(self.run_policy("watchedDuration", "1398000", "784000", "1380000").stdout, "784000")
        self.assertEqual(self.run_policy("watchedDuration", "0", "5640000", "6300000").stdout, "5640000")
        self.assertEqual(self.run_policy("watchedDuration", "0", "0", "6300000").stdout, "6300000")


if __name__ == "__main__":
    unittest.main(verbosity=2)
