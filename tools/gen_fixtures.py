#!/usr/bin/env python3
"""Generate golden test fixtures for the Kotlin codec tests.

Uses pycocotools as the reference implementation of COCO RLE.
Run:  pip install pycocotools numpy  &&  python tools/gen_fixtures.py
Outputs JSON files into fixtures/. Kotlin tests must reproduce every value.
"""
import base64
import json
import zlib
from pathlib import Path

import numpy as np
from pycocotools import mask as M

OUT = Path(__file__).resolve().parent.parent / "fixtures"
OUT.mkdir(exist_ok=True)


def roboflow_encode(counts: bytes) -> str:
    # zlib default level (6) + standard base64. Matches Roboflow byte for byte.
    return base64.b64encode(zlib.compress(counts, 6)).decode()


def case(name: str, mask: np.ndarray, note: str) -> dict:
    h, w = mask.shape
    rle = M.encode(np.asfortranarray(mask.astype(np.uint8)))
    counts = rle["counts"]
    bbox = [float(v) for v in M.toBbox(rle)]
    # Uncompressed run lengths, column major, starting with a run of zeros.
    flat = mask.flatten(order="F")
    runs, cur, n = [], 0, 0
    for v in flat:
        if v == cur:
            n += 1
        else:
            runs.append(n)
            cur, n = v, 1
    runs.append(n)
    return {
        "name": name,
        "note": note,
        "height": h,
        "width": w,
        "rows": ["".join("1" if v else "0" for v in row) for row in mask],
        "rleCounts": runs,
        "cocoCompressedCounts": counts.decode(),
        "roboflowMask": roboflow_encode(counts),
        "area": int(M.area(rle)),
        "bboxXYWH": bbox,
        "bboxCenter": [bbox[0] + bbox[2] / 2, bbox[1] + bbox[3] / 2],
    }


cases = []

m = np.zeros((6, 8), np.uint8)
m[1:4, 2:6] = 1
cases.append(case("rect", m, "Single filled rectangle"))

m = np.zeros((7, 7), np.uint8)
m[1:6, 1:6] = 1
m[3, 3] = 0
cases.append(case("hole", m, "Square with a one pixel hole (YOLO cannot represent the hole)"))

m = np.zeros((6, 10), np.uint8)
m[1:3, 1:3] = 1
m[3:5, 6:9] = 1
cases.append(case("two_blobs", m, "Two disconnected regions in one annotation"))

m = np.zeros((5, 5), np.uint8)
cases.append(case("empty", m, "Empty mask: area 0, must be dropped on save"))

m = np.ones((4, 3), np.uint8)
cases.append(case("full", m, "Every pixel set: first run is 0"))

rng = np.random.default_rng(7)
m = (rng.random((40, 60)) > 0.6).astype(np.uint8)
cases.append(case("random_40x60", m, "Noisy mask for stress testing the LEB128 style encoder"))

(OUT / "rle_cases.json").write_text(json.dumps({"cases": cases}, indent=2))

# Real Roboflow sample supplied by the user.
sample = "eJxFjcEKwkAMRH9pJuvBgseCCt0EKUWhtHgRhK70/2/dXWNleMMQJsljnJ6YuGDVOeHOobfB3gkf3HobbUlYqWKF0AUNkfYTQBQzFW8Q2IFbbVQBKnkmygK+Z+kl1gUPllvG7vDHX4QoniWKBs1+bc7HS9POr3TaAH5gM+k="
counts = zlib.decompress(base64.b64decode(sample))
rle = {"size": [1024, 1024], "counts": counts}
bbox = [float(v) for v in M.toBbox(rle)]
dense = M.decode(rle)
flat = dense.flatten(order="F")
runs, cur, n = [], 0, 0
for v in flat:
    if v == cur:
        n += 1
    else:
        runs.append(n)
        cur, n = v, 1
runs.append(n)
(OUT / "roboflow_sample.json").write_text(json.dumps({
    "note": "Real Roboflow mask annotation. Image size inferred as 1024x1024 (run lengths sum to 1048576).",
    "input": {
        "id": "5", "type": "mask", "label": "11",
        "x": "568", "y": "402.5", "width": "86", "height": "127",
        "area": "8661", "mask": sample,
    },
    "expected": {
        "imageWidth": 1024,
        "imageHeight": 1024,
        "cocoCompressedCounts": counts.decode(),
        "runCount": len(runs),
        "runSum": int(sum(runs)),
        "firstRuns": runs[:6],
        "area": int(M.area(rle)),
        "bboxXYWH": bbox,
        "bboxCenter": [bbox[0] + bbox[2] / 2, bbox[1] + bbox[3] / 2],
        "reencodedMaskEqualsInput": roboflow_encode(counts) == sample,
    },
}, indent=2))

print("wrote", [p.name for p in OUT.iterdir()])
