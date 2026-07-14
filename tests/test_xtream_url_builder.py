import subprocess
import tempfile
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension/XtreamUrlBuilder.java"


class XtreamUrlBuilderTest(unittest.TestCase):
    def run_builder(self, playlist_url: str, xc_id: str):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            harness = tmp_path / "Harness.java"
            harness.write_text(
                "import com.tivimate.traktpatch.extension.XtreamUrlBuilder;\n"
                "public final class Harness {\n"
                "  public static void main(String[] args) {\n"
                "    System.out.print(XtreamUrlBuilder.vodInfoUrl(args[0], args[1]));\n"
                "  }\n"
                "}\n"
            )
            compiled = subprocess.run(
                ["javac", "-d", str(tmp_path), str(SOURCE), str(harness)],
                text=True,
                capture_output=True,
            )
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            return subprocess.run(
                ["java", "-cp", str(tmp_path), "Harness", playlist_url, xc_id],
                text=True,
                capture_output=True,
            )

    def test_builds_vod_info_url_without_decoding_credentials(self):
        result = self.run_builder(
            "https://provider.example/iptv/get.php?username=a%40b&password=p%2Bq&type=m3u_plus&output=ts",
            "12345",
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(
            result.stdout,
            "https://provider.example/iptv/player_api.php?username=a%40b&password=p%2Bq&action=get_vod_info&vod_id=12345",
        )

    def test_rejects_missing_credentials(self):
        result = self.run_builder("https://provider.example/get.php?username=user", "123")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("missing Xtream credentials", result.stderr)

    def test_rejects_non_numeric_vod_id(self):
        result = self.run_builder(
            "https://provider.example/get.php?username=user&password=pass",
            "12&action=delete",
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("invalid Xtream VOD id", result.stderr)

    def test_diagnostic_shape_exposes_structure_but_not_host_or_credentials(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            harness = tmp_path / "Harness.java"
            harness.write_text(
                "import com.tivimate.traktpatch.extension.XtreamUrlBuilder;\n"
                "public final class Harness { public static void main(String[] args) {\n"
                "System.out.print(XtreamUrlBuilder.diagnosticShape(args[0])); }}\n"
            )
            compiled = subprocess.run(
                ["javac", "-d", str(tmp_path), str(SOURCE), str(harness)],
                text=True, capture_output=True,
            )
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            result = subprocess.run(
                ["java", "-cp", str(tmp_path), "Harness",
                 "https://secret.example/iptv/get.php?username=andre&password=hunter2&type=m3u_plus"],
                text=True, capture_output=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("scheme=https", result.stdout)
            self.assertIn("leaf=get.php", result.stdout)
            self.assertIn("username", result.stdout)
            self.assertIn("password", result.stdout)
            self.assertNotIn("secret.example", result.stdout)
            self.assertNotIn("andre", result.stdout)
            self.assertNotIn("hunter2", result.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
