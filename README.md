# 🙂 VeritySee

An Android app for reviewing and editing image segmentation datasets (YOLO, COCO, SAM) on your phone or tablet.

## Why this exists

We have two weeks to annotate thousands of images for our undergrad thesis. Roboflow was our main driver, and it's fine, but coming from knowing other digital creative tools there are things about it that we would've wanted to have. Another big problem: no real mobile support. If you wanted to fix masks, you were stuck at a desk. But we have classes, we don't want to bring our bulky and heavy laptops and find power outlets that aren't always available.

So I vibecoded an app instead. Open a folder, see the auto-labeled annotations, fix them with box/brush/pencil, track what's been checked, export clean YOLO/COCO/SAM. Native Android, not a web wrapper as browsers still can't open a local folder and autosave into it reliably, so that decision made itself.

## The name

Named after [![Verity](https://www.youtube.com/watch?v=tVlKLpxyCBY)](https://www.youtube.com/watch?v=tVlKLpxyCBY). "Hey! It's me. I'm Verity. Ask me anything"


## What it does

- Opens a folder of images straight through SAF. No cloud upload, no middleman.
- Shows whatever's already labeled — YOLO, COCO, SAM.
- Box, brush, pencil. Fix the mask, move on.
- Keeps track of what you've reviewed and what you haven't.
- Exports clean YOLO, COCO, and SAM datasets when you're done.
- Your original label files stay untouched. Edits land in `.annotator/` and export folders, nowhere else.

Full spec's in `docs/SPEC.md`. File format rules in `docs/FORMATS.md`. Milestones in `docs/ROADMAP.md`.

## Stack

Kotlin, Jetpack Compose, Android only, minSdk 26. `:core` is plain Kotlin — no Android imports, runs on the JVM, fast to test. `:app` is where the SAF storage, UI, rendering, and gesture handling actually live.

## Building

```bash
./gradlew :core:test                 # fast JVM unit tests
./gradlew :app:assembleDebug         # build the app
./gradlew :app:connectedDebugAndroidTest   # instrumented tests, needs a device
./gradlew lint                       # Android lint
```
