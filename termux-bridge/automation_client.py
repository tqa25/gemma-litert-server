#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

DEFAULT_BASE_URL = "http://127.0.0.1:8765"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Termux automation client for the Android Gemma backend.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("status")
    sub.add_parser("stop")
    sub.add_parser("config")
    sub.add_parser("logs")
    sub.add_parser("clear-logs")
    sub.add_parser("current-app")

    tap = sub.add_parser("tap")
    tap.add_argument("--x", type=int, required=True)
    tap.add_argument("--y", type=int, required=True)

    swipe = sub.add_parser("swipe")
    swipe.add_argument("--x1", type=int, required=True)
    swipe.add_argument("--y1", type=int, required=True)
    swipe.add_argument("--x2", type=int, required=True)
    swipe.add_argument("--y2", type=int, required=True)
    swipe.add_argument("--duration-ms", type=int, default=400)

    sub.add_parser("home")
    sub.add_parser("back")
    sub.add_parser("longpress-home")

    wait = sub.add_parser("wait")
    wait.add_argument("--ms", type=int, required=True)

    screenshot = sub.add_parser("screenshot")
    screenshot.add_argument("--output", required=True)

    screen_xml = sub.add_parser("screen-xml")
    screen_xml.add_argument("--output", required=True)

    open_app = sub.add_parser("open-app")
    open_app.add_argument("--package", required=True)

    calibrate = sub.add_parser("calibrate")
    calibrate.add_argument(
        "key",
        choices=[
            "chrome_discover_first_article",
            "gemini_summary_button",
            "gemini_copy_button",
            "chrome_menu_button",
            "chrome_show_reading_mode",
        ],
    )
    calibrate.add_argument("--x", type=int, required=True)
    calibrate.add_argument("--y", type=int, required=True)

    run = sub.add_parser("run")
    run.add_argument("workflow", choices=["chrome-discover-gemini-summary-once", "chrome-discover-reading-gemma-summary-once"])
    run.add_argument("--debug-capture", action="store_true")

    run_json = sub.add_parser("run-json")
    run_json.add_argument("--file", required=True, help="Path to a JSON workflow definition.")

    args = parser.parse_args(argv)
    try:
        result = dispatch(args)
        if result is not None:
            print_json(result)
        return 0
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        if detail:
            print(f"ERROR: backend returned HTTP {exc.code}: {detail}", file=sys.stderr)
        else:
            print(f"ERROR: backend returned HTTP {exc.code}: {exc.reason}", file=sys.stderr)
        return 1
    except urllib.error.URLError as exc:
        print(f"ERROR: cannot reach backend: {exc}", file=sys.stderr)
        return 1
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


def dispatch(args: argparse.Namespace) -> dict | None:
    base = args.base_url.rstrip("/")
    if args.command == "status":
        return call_json("GET", f"{base}/automation/status")
    if args.command == "config":
        return call_json("GET", f"{base}/automation/config")
    if args.command == "logs":
        return call_json("GET", f"{base}/automation/logs")
    if args.command == "clear-logs":
        return call_json("POST", f"{base}/automation/logs/clear", {})
    if args.command == "current-app":
        return call_json("GET", f"{base}/automation/current-app")
    if args.command == "stop":
        return call_json("POST", f"{base}/automation/stop", {})
    if args.command == "tap":
        return call_json("POST", f"{base}/automation/tap", {"x": args.x, "y": args.y})
    if args.command == "swipe":
        return call_json(
            "POST",
            f"{base}/automation/swipe",
            {"x1": args.x1, "y1": args.y1, "x2": args.x2, "y2": args.y2, "duration_ms": args.duration_ms},
        )
    if args.command == "home":
        return call_json("POST", f"{base}/automation/home", {})
    if args.command == "back":
        return call_json("POST", f"{base}/automation/back", {})
    if args.command == "longpress-home":
        return call_json("POST", f"{base}/automation/longpress-home", {})
    if args.command == "wait":
        return call_json("POST", f"{base}/automation/wait", {"ms": args.ms})
    if args.command == "screenshot":
        result = call_json("GET", f"{base}/automation/screenshot")
        image = base64.b64decode(result["image_base64"])
        Path(args.output).write_bytes(image)
        saved = dict(result)
        saved.pop("image_base64", None)
        saved["output"] = args.output
        return saved
    if args.command == "screen-xml":
        result = call_json("GET", f"{base}/automation/screen-xml")
        Path(args.output).write_text(result["xml"], encoding="utf-8")
        saved = dict(result)
        saved.pop("xml", None)
        saved["output"] = args.output
        return saved
    if args.command == "open-app":
        return call_json("POST", f"{base}/automation/open-app", {"package": args.package})
    if args.command == "calibrate":
        return call_json("POST", f"{base}/automation/calibrate", {"key": args.key, "x": args.x, "y": args.y})
    if args.command == "run":
        return call_json(
            "POST",
            f"{base}/automation/workflows/run",
            {"workflow": args.workflow, "debug_capture": args.debug_capture},
        )
    if args.command == "run-json":
        payload = json.loads(Path(args.file).read_text(encoding="utf-8"))
        return call_json("POST", f"{base}/automation/workflows/run-json", payload)
    raise ValueError(f"unknown command: {args.command}")


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


def print_json(value: dict) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    raise SystemExit(main())
