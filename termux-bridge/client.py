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
from datetime import datetime, timezone
from pathlib import Path

DEFAULT_BASE_URL = "http://127.0.0.1:8765"
DEFAULT_BENCHMARK_LOG = Path("termux-bridge/benchmark.jsonl")


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
    image.add_argument("-i", "--image", required=True, help="Path to image file on Termux/Android storage.")
    image.add_argument("--prompt", default="Extract visible text from this image. Return concise text.")
    image.add_argument("--max-tokens", type=int, default=256)
    image.add_argument("--temperature", type=float, default=0.1)

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
            image_path = Path(args.image)
            image_bytes = image_path.read_bytes()
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
                file_path=image_path,
                file_bytes=image_bytes,
            )
            client_total_ms = round((time.perf_counter() - started) * 1000)
            return finish_generate(
                args,
                result,
                has_image=True,
                image_path=str(image_path),
                prompt_chars=len(args.prompt),
                client_total_ms=client_total_ms,
            )
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


def finish_generate(
    args: argparse.Namespace,
    result: dict,
    *,
    has_image: bool,
    image_path: str | None,
    prompt_chars: int,
    client_total_ms: int,
) -> int:
    print_json(result)
    append_benchmark(
        Path(args.benchmark_log),
        {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "base_url": args.base_url,
            "has_image": has_image,
            "server_has_image": result.get("meta", {}).get("has_image"),
            "image_path": image_path,
            "prompt_chars": prompt_chars,
            "client_total_ms": client_total_ms,
            "server_timing": result.get("timing", {}),
            "engine": result.get("meta", {}).get("engine"),
            "response_chars": len(result.get("response", "")),
            "request_id": result.get("request_id"),
        },
    )
    return 0


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
