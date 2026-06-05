#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path


CLIENT_PATH = Path(__file__).with_name("client.py")
spec = importlib.util.spec_from_file_location("termux_client", CLIENT_PATH)
client = importlib.util.module_from_spec(spec)
assert spec.loader is not None
sys.modules[spec.name] = client
spec.loader.exec_module(client)


class ImageUploadOptionsTest(unittest.TestCase):
    def test_build_multipart_uses_prepared_file_name_and_content_type(self) -> None:
        body = client.build_multipart_body(
            "boundary",
            {"prompt": "ocr"},
            file_field="image",
            file_path=Path("prepared.jpg"),
            file_bytes=b"image-bytes",
        )

        self.assertIn(b'filename="prepared.jpg"', body)
        self.assertIn(b"Content-Type: image/jpeg", body)
        self.assertIn(b"image-bytes", body)

    def test_no_resize_options_keep_original_image_bytes(self) -> None:
        payload = b"\x89PNG\r\noriginal"
        prepared = client.prepare_image_upload(Path("sample.png"), payload, max_edge=None, jpeg_quality=None)

        self.assertEqual(prepared.path, Path("sample.png"))
        self.assertEqual(prepared.bytes, payload)
        self.assertEqual(prepared.original_bytes, len(payload))
        self.assertEqual(prepared.upload_bytes, len(payload))
        self.assertEqual(prepared.preprocess_ms, 0)
        self.assertFalse(prepared.resized)

    def test_summarize_benchmark_runs_calculates_min_avg_max(self) -> None:
        runs = [
            {"timing": {"inference_ms": 100, "total_ms": 150}},
            {"timing": {"inference_ms": 200, "total_ms": 250}},
            {"timing": {"inference_ms": 300, "total_ms": 350}},
        ]

        summary = client.summarize_benchmark_runs(runs)

        self.assertEqual(summary["runs"], 3)
        self.assertEqual(summary["inference_ms"], {"min": 100, "avg": 200, "max": 300})
        self.assertEqual(summary["total_ms"], {"min": 150, "avg": 250, "max": 350})

    def test_speed_preset_applies_1280_jpeg_85(self) -> None:
        options = client.resolve_image_options("speed", max_edge=None, jpeg_quality=None)

        self.assertEqual(options.max_edge, 1280)
        self.assertEqual(options.jpeg_quality, 85)

    def test_accuracy_preset_keeps_original_image(self) -> None:
        options = client.resolve_image_options("accuracy", max_edge=None, jpeg_quality=None)

        self.assertIsNone(options.max_edge)
        self.assertIsNone(options.jpeg_quality)

    def test_explicit_options_override_preset(self) -> None:
        options = client.resolve_image_options("speed", max_edge=1600, jpeg_quality=90)

        self.assertEqual(options.max_edge, 1600)
        self.assertEqual(options.jpeg_quality, 90)


if __name__ == "__main__":
    unittest.main()
