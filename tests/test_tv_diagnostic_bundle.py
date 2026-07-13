"""Static guardrails for the unrooted Android-TV runtime discovery bundle.

These tests deliberately inspect the source bundle before an APK is produced. The
actual launch/Frida attach test remains a device gate on the user's TV AVD.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
EXTENSION = ROOT / "morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension/TraktPatchExtension.java"
BUILD_FILE = ROOT / "morphe/extensions/trakt/build.gradle.kts"
PATCH = ROOT / "morphe/patches/src/main/kotlin/com/tivimate/traktpatch/patches/TraktRuntimeSyncPatch.kt"
PATCH_GADGET = ROOT / "morphe/patches/src/main/resources/diagnostic/x86_64/libfrida-gadget.so"
GADGET_CONFIG = ROOT / "morphe/patches/src/main/resources/diagnostic/x86_64/libfrida-gadget.config.so"


class TvDiagnosticBundleTest(unittest.TestCase):
    def test_diagnostic_extension_loads_the_gadget_once(self):
        source = EXTENSION.read_text()
        self.assertIn('System.loadLibrary("frida-gadget")', source)
        self.assertIn("initializeDiagnosticGadget", source)

    def test_gadget_payload_is_a_nonempty_x86_64_elf(self):
        self.assertGreater(PATCH_GADGET.stat().st_size, 1_000_000)
        self.assertEqual(PATCH_GADGET.read_bytes()[:4], b"\x7fELF")

    def test_gadget_configuration_is_loopback_only_and_not_loaded_by_default(self):
        config = GADGET_CONFIG.read_text()
        self.assertIn('"interaction"', config)
        self.assertIn('"type": "listen"', config)
        self.assertIn('"address": "127.0.0.1"', config)
        self.assertIn('"port": 27042', config)
        assert '"on_load": "resume"' in config

    def test_runtime_patch_merges_extension_and_calls_diagnostic_initializer(self):
        source = PATCH.read_text()
        self.assertIn("bytecodePatch", source)
        self.assertIn('extendWith("trakt.mpe")', source)
        self.assertIn("DIAGNOSTIC_EXTENSION_CLASS", source)
        self.assertIn("activityOnCreateExtensionHook", source)

    def test_extension_uses_legacy_native_packaging_for_the_gadget_config(self):
        build_file = BUILD_FILE.read_text()
        self.assertIn("useLegacyPackaging = true", build_file)


if __name__ == "__main__":
    unittest.main(verbosity=2)
