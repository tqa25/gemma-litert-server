#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import mimetypes
import secrets
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from io import BytesIO
from pathlib import Path

DEFAULT_BASE_URL = "http://127.0.0.1:8765"
DEFAULT_BENCHMARK_LOG = Path("termux-bridge/benchmark.jsonl")
PRESET_SPEED = "speed"
PRESET_ACCURACY = "accuracy"


@dataclass(frozen=True)
class ImageOptions:
    max_edge: int | None
    jpeg_quality: int | None


@dataclass(frozen=True)
class PreparedImageUpload:
    path: Path
    bytes: bytes
    original_bytes: int
    upload_bytes: int
    preprocess_ms: int
    resized: bool


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Termux client for the Android Gemma backend API.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help="Android backend base URL.")
    parser.add_argument("--benchmark-log", default=str(DEFAULT_BENCHMARK_LOG), help="JSONL benchmark log path.")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("health", help="Call GET /health.")

    text = sub.add_parser("generate-text", help="Call POST /generate with text only.")
    text.add_argument("--prompt", required=True)
    text.add_argument("--max-tokens", type=int, default=128)
    text.add_argument("--temperature", type=float, default=0.2)

    image = sub.add_parser("generate-image", help="Call POST /generate with prompt + image file.")
    add_image_args(image)

    benchmark = sub.add_parser("benchmark-image", help="Run repeated image requests and summarize timings.")
    add_image_args(benchmark)
    benchmark.add_argument("--runs", type=int, default=5, help="Number of repeated image requests.")

    args = parser.parse_args(argv)
    try:
        if args.command == "health":
            result = call_json("GET", f"{args.base_url}/health")
            print_json(result)
            return 0
        if args.command == "generate-text":
            payload = {
                "prompt": args.prompt,
                "max_tokens": args.max_tokens,
                "temperature": args.temperature,
            }
            return generate_json(args, payload, has_image=False, image_path=None)
        if args.command == "generate-image":
            outcome = perform_generate_image(args)
            return finish_generate(
                args,
                outcome["result"],
                has_image=True,
                image_path=outcome["image_path"],
                prompt_chars=len(args.prompt),
                client_total_ms=outcome["client_total_ms"],
                image_upload=outcome["image_upload"],
            )
        if args.command == "benchmark-image":
            return benchmark_image(args)
    except FileNotFoundError as exc:
        print(f"ERROR: file not found: {exc.filename}", file=sys.stderr)
        return 2
    except urllib.error.URLError as exc:
        print(f"ERROR: cannot reach backend: {exc}", file=sys.stderr)
        return 1
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    return 2


def add_image_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("-i", "--image", required=True, help="Path to image file on Termux/Android storage.")
    parser.add_argument("--prompt", default="Extract visible text from this image. Return concise text.")
    parser.add_argument("--max-tokens", type=int, default=256)
    parser.add_argument("--temperature", type=float, default=0.1)
    parser.add_argument(
        "--preset",
        choices=[PRESET_SPEED, PRESET_ACCURACY],
        help="Image preprocessing preset: speed uses 1280/JPEG85, accuracy uploads the original image.",
    )
    parser.add_argument("--resize-max-edge", type=int, help="Resize image so its longest edge is at most this many pixels.")
    parser.add_argument("--jpeg-quality", type=int, help="Upload as JPEG at this quality, from 1 to 100.")


def resolve_image_options(preset: str | None, *, max_edge: int | None, jpeg_quality: int | None) -> ImageOptions:
    resolved_max_edge = max_edge
    resolved_jpeg_quality = jpeg_quality
    if preset == PRESET_SPEED:
        if resolved_max_edge is None:
            resolved_max_edge = 1280
        if resolved_jpeg_quality is None:
            resolved_jpeg_quality = 85
    elif preset == PRESET_ACCURACY or preset is None:
        pass
    else:
        raise ValueError(f"unknown preset: {preset}")
    return ImageOptions(max_edge=resolved_max_edge, jpeg_quality=resolved_jpeg_quality)


def generate_json(args: argparse.Namespace, payload: dict, *, has_image: bool, image_path: str | None) -> int:
    started = time.perf_counter()
    result = call_json("POST", f"{args.base_url}/generate", payload)
    client_total_ms = round((time.perf_counter() - started) * 1000)
    return finish_generate(
        args,
        result,
        has_image=has_image,
        image_path=image_path,
        prompt_chars=len(payload.get("prompt", "")),
        client_total_ms=client_total_ms,
    )


def perform_generate_image(args: argparse.Namespace) -> dict:
    image_path = Path(args.image)
    image_bytes = image_path.read_bytes()
    options = resolve_image_options(args.preset, max_edge=args.resize_max_edge, jpeg_quality=args.jpeg_quality)
    prepared_image = prepare_image_upload(
        image_path,
        image_bytes,
        max_edge=options.max_edge,
        jpeg_quality=options.jpeg_quality,
    )
    fields = {
        "prompt": args.prompt,
        "max_tokens": str(args.max_tokens),
        "temperature": str(args.temperature),
    }
    started = time.perf_counter()
    result = call_multipart_json(
        f"{args.base_url}/generate",
        fields,
        file_field="image",
        file_path=prepared_image.path,
        file_bytes=prepared_image.bytes,
    )
    client_total_ms = round((time.perf_counter() - started) * 1000)
    return {
        "result": result,
        "image_path": str(image_path),
        "image_upload": prepared_image,
        "image_options": options,
        "client_total_ms": client_total_ms,
    }


def benchmark_image(args: argparse.Namespace) -> int:
    if args.runs < 1:
        raise ValueError("--runs must be at least 1")
    runs = []
    last_upload = None
    last_options = None
    for index in range(args.runs):
        outcome = perform_generate_image(args)
        result = outcome["result"]
        last_upload = outcome["image_upload"]
        last_options = outcome["image_options"]
        finish_generate(
            args,
            result,
            has_image=True,
            image_path=outcome["image_path"],
            prompt_chars=len(args.prompt),
            client_total_ms=outcome["client_total_ms"],
            image_upload=last_upload,
            print_response=False,
        )
        timing = result.get("timing", {})
        meta = result.get("meta", {})
        run = {
            "run": index + 1,
            "request_id": result.get("request_id"),
            "timing": timing,
            "client_total_ms": outcome["client_total_ms"],
            "engine": meta.get("engine"),
            "image_bytes": meta.get("image_bytes"),
            "response_chars": len(result.get("response", "")),
        }
        runs.append(run)
        print(
            "run {}/{}: total_ms={} inference_ms={} image_bytes={}".format(
                index + 1,
                args.runs,
                timing.get("total_ms"),
                timing.get("inference_ms"),
                meta.get("image_bytes"),
            ),
            file=sys.stderr,
        )
    summary = summarize_benchmark_runs(runs)
    summary.update({
        "image_path": args.image,
        "preset": args.preset,
        "resize_max_edge": last_options.max_edge if last_options else args.resize_max_edge,
        "jpeg_quality": last_options.jpeg_quality if last_options else args.jpeg_quality,
        "image_original_bytes": last_upload.original_bytes if last_upload else None,
        "image_upload_bytes": last_upload.upload_bytes if last_upload else None,
        "image_preprocess_ms_last": last_upload.preprocess_ms if last_upload else None,
        "runs_detail": runs,
    })
    print_json(summary)
    return 0


def summarize_benchmark_runs(runs: list[dict]) -> dict:
    return {
        "runs": len(runs),
        "inference_ms": summarize_numbers([run.get("timing", {}).get("inference_ms") for run in runs]),
        "total_ms": summarize_numbers([run.get("timing", {}).get("total_ms") for run in runs]),
        "client_total_ms": summarize_numbers([run.get("client_total_ms") for run in runs]),
    }


def summarize_numbers(values: list[int | None]) -> dict[str, int | None]:
    numbers = [int(value) for value in values if value is not None]
    if not numbers:
        return {"min": None, "avg": None, "max": None}
    return {"min": min(numbers), "avg": round(sum(numbers) / len(numbers)), "max": max(numbers)}


def finish_generate(
    args: argparse.Namespace,
    result: dict,
    *,
    has_image: bool,
    image_path: str | None,
    prompt_chars: int,
    client_total_ms: int,
    image_upload: PreparedImageUpload | None = None,
    print_response: bool = True,
) -> int:
    if print_response:
        print_json(result)
    append_benchmark(
        Path(args.benchmark_log),
        {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "base_url": args.base_url,
            "has_image": has_image,
            "server_has_image": result.get("meta", {}).get("has_image"),
            "image_path": image_path,
            "image_original_bytes": image_upload.original_bytes if image_upload else None,
            "image_upload_bytes": image_upload.upload_bytes if image_upload else None,
            "image_preprocess_ms": image_upload.preprocess_ms if image_upload else None,
            "image_resized": image_upload.resized if image_upload else None,
            "image_preset": getattr(args, "preset", None),
            "prompt_chars": prompt_chars,
            "client_total_ms": client_total_ms,
            "server_timing": result.get("timing", {}),
            "engine": result.get("meta", {}).get("engine"),
            "response_chars": len(result.get("response", "")),
            "request_id": result.get("request_id"),
        },
    )
    return 0


def prepare_image_upload(
    image_path: Path,
    image_bytes: bytes,
    *,
    max_edge: int | None,
    jpeg_quality: int | None,
) -> PreparedImageUpload:
    if max_edge is None and jpeg_quality is None:
        return PreparedImageUpload(
            path=image_path,
            bytes=image_bytes,
            original_bytes=len(image_bytes),
            upload_bytes=len(image_bytes),
            preprocess_ms=0,
            resized=False,
        )
    if max_edge is not None and max_edge < 1:
        raise ValueError("--resize-max-edge must be at least 1")
    if jpeg_quality is not None and not 1 <= jpeg_quality <= 100:
        raise ValueError("--jpeg-quality must be between 1 and 100")

    try:
        from PIL import Image
    except ImportError as exc:
        raise RuntimeError(
            "image preprocessing requires Pillow; install it in Termux with `pkg install python-pillow`"
        ) from exc

    started = time.perf_counter()
    with Image.open(BytesIO(image_bytes)) as image:
        original_size = image.size
        if max_edge is not None:
            image.thumbnail((max_edge, max_edge))
        resized = image.size != original_size
        upload_path = image_path
        if jpeg_quality is not None:
            upload_path = image_path.with_suffix(".jpg")
            image = image.convert("RGB")
            output = BytesIO()
            image.save(output, format="JPEG", quality=jpeg_quality, optimize=True)
            upload_bytes = output.getvalue()
        else:
            output = BytesIO()
            image.save(output, format=image.format or "PNG")
            upload_bytes = output.getvalue()
    preprocess_ms = round((time.perf_counter() - started) * 1000)
    return PreparedImageUpload(
        path=upload_path,
        bytes=upload_bytes,
        original_bytes=len(image_bytes),
        upload_bytes=len(upload_bytes),
        preprocess_ms=preprocess_ms,
        resized=resized,
    )


def call_json(method: str, url: str, payload: dict | None = None) -> dict:
    data = None
    headers = {}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(request, timeout=300) as response:
        raw = response.read().decode("utf-8")
    return json.loads(raw)


def call_multipart_json(url: str, fields: dict[str, str], *, file_field: str, file_path: Path, file_bytes: bytes) -> dict:
    boundary = "----gemma-termux-" + secrets.token_hex(12)
    body = build_multipart_body(boundary, fields, file_field=file_field, file_path=file_path, file_bytes=file_bytes)
    headers = {
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        "Content-Length": str(len(body)),
    }
    request = urllib.request.Request(url, data=body, headers=headers, method="POST")
    with urllib.request.urlopen(request, timeout=300) as response:
        raw = response.read().decode("utf-8")
    return json.loads(raw)


def build_multipart_body(boundary: str, fields: dict[str, str], *, file_field: str, file_path: Path, file_bytes: bytes) -> bytes:
    chunks: list[bytes] = []
    for name, value in fields.items():
        chunks.extend([
            f"--{boundary}\r\n".encode("utf-8"),
            f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"),
            value.encode("utf-8"),
            b"\r\n",
        ])
    content_type = mimetypes.guess_type(file_path.name)[0] or "application/octet-stream"
    chunks.extend([
        f"--{boundary}\r\n".encode("utf-8"),
        f'Content-Disposition: form-data; name="{file_field}"; filename="{file_path.name}"\r\n'.encode("utf-8"),
        f"Content-Type: {content_type}\r\n\r\n".encode("utf-8"),
        file_bytes,
        b"\r\n",
        f"--{boundary}--\r\n".encode("utf-8"),
    ])
    return b"".join(chunks)


def append_benchmark(path: Path, row: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as f:
        f.write(json.dumps(row, ensure_ascii=False) + "\n")


def print_json(value: dict) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    raise SystemExit(main())
