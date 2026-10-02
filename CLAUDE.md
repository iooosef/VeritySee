# CLAUDE.md

Android app for reviewing and editing image segmentation datasets (YOLO, COCO, SAM) on a phone or tablet. Kotlin + Jetpack Compose, Android only.

## Read first

- `docs/SPEC.md`: what the app does, screens, tools, architecture.
- `docs/FORMATS.md`: exact file format rules. Follow it to the letter for anything touching RLE, YOLO, COCO, or SAM.
- `docs/ROADMAP.md`: milestones. Work on one milestone at a time and tick its boxes when done.

## Commands

```bash
./gradlew :core:test                 # fast JVM unit tests, run after every core change
./gradlew :app:assembleDebug         # build the app
./gradlew :app:connectedDebugAndroidTest   # instrumented tests, needs a device
./gradlew lint                       # Android lint
python tools/gen_fixtures.py         # regenerate golden fixtures (needs pycocotools)
```

## Architecture rules

- `:core` is pure Kotlin. Never import `android.*` or `androidx.*` there. If logic can live in `:core`, it must.
- `:app` holds only Android specific code: SAF storage, Compose UI, bitmaps, rendering, gestures.
- Canonical annotations are boxes or full image RLE masks. Polygons only exist at import/export boundaries.
- All coordinates are original image pixels, top-left origin. Display scale lives only in the viewport transform.
- Every edit goes through a `Command` so undo/redo stays exact.

## Gotchas

- RLE counts are **column major** and start with a run of zeros. See FORMATS 1.
- Compressed RLE uses deltas only for index `> 2`, not `>= 2`.
- Roboflow `x`, `y` are the bbox **center**. COCO and SAM bbox are top-left `XYWH`.
- Use `DocumentsContract` queries for listing files. `DocumentFile.listFiles()` is too slow.
- Never modify the user's original label files. Write to `.annotator/` and export folders only.
- Two finger gestures always pan/zoom. One finger belongs to the active tool.

## Testing

- Golden fixtures live in `core/src/test/resources/fixtures/` (copied from `fixtures/`). Codec code must reproduce them exactly.
- Add a unit test with every bug fix in `:core`.
- Prefer small hand written datasets in test resources over mocks for format tests.

## Working style

- Before a milestone, propose a short plan and wait for approval.
- Ask before adding any dependency not listed in SPEC section 1.
- Keep functions small and files under ~400 lines.
- When the spec is unclear, ask instead of guessing, and record the answer in the relevant doc.
