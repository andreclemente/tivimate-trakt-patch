import subprocess
import tempfile
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension/TraktProgressMath.java"


class TraktProgressMathTest(unittest.TestCase):
    def calculate(self, position: str, duration: str):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            harness = tmp_path / "Harness.java"
            harness.write_text(
                "import com.tivimate.traktpatch.extension.TraktProgressMath;\n"
                "public final class Harness { public static void main(String[] a) {\n"
                "System.out.print(TraktProgressMath.percent(Long.parseLong(a[0]), Long.parseLong(a[1]))); }}\n"
            )
            compiled = subprocess.run(
                ["javac", "-d", str(tmp_path), str(SOURCE), str(harness)],
                text=True, capture_output=True,
            )
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            return subprocess.run(
                ["java", "-cp", str(tmp_path), "Harness", position, duration],
                text=True, capture_output=True,
            )

    def test_calculates_percent_from_milliseconds(self):
        result = self.calculate("150000", "600000")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "25.0")

    def test_clamps_position_to_valid_scrobble_range(self):
        result = self.calculate("700000", "600000")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "100.0")

    def test_rejects_unknown_duration(self):
        result = self.calculate("1000", "0")
        self.assertNotEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
