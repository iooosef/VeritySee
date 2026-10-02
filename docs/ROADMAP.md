# Roadmap

Build in this order. Each milestone is sized for one or a few Claude Code sessions and ends with something testable. Do not start a milestone until the previous one's acceptance checks pass.

How to run a milestone with Claude Code:

```
/milestone M1
```

(see `.claude/commands/milestone.md`), or paste: "Read CLAUDE.md, docs/SPEC.md and docs/FORMATS.md. Plan milestone M1 from docs/ROADMAP.md, show me the plan, then implement it with tests."

Tick the boxes in this file as items land, so the next session knows where things stand.

---

## M0. Project scaffold

- [x] Gradle project with `:core` (Kotlin JVM library) and `:app` (Android, Compose, Material 3).
- [x] JUnit 5 in `:core`, kotlinx.serialization in both.
- [x] Copy `fixtures/` into `core/src/test/resources/fixtures/`.
- [x] Empty Home screen that builds and launches.
- [x] `./gradlew :core:test` and `./gradlew :app:assembleDebug` both succeed.

Accept: both commands green, app launches on an emulator or device.

## M1. RLE and mask codec (`:core/codec`)

- [x] `Rle` type, encode from a bitmap-like `BooleanGrid` (row major in memory, column major in counts).
- [x] Compressed counts string encode and decode (FORMATS 1.1).
- [x] Roboflow mask string encode and decode (FORMATS 1.2).
- [x] `area()`, `bbox()`, `contains(col, row)` without full decode.
- [x] Boolean ops on RLE or on grids: union, subtract, isEmpty.

Accept: every case in `fixtures/rle_cases.json` and `fixtures/roboflow_sample.json` reproduces exactly (counts, compressed string, area, bbox, center). Round trip decode then encode is identity.

## M2. Geometry (`:core/geometry`)

- [x] Polygon rasterization (pixel center, even-odd).
- [x] Outer contour tracing per connected component (FORMATS 2.2).
- [x] Douglas-Peucker simplification.
- [x] Hole detection and multi component joining for YOLO.
- [x] Point in polygon, rect intersection helpers for selection tools.

Accept: mask to polygon to mask round trip is exact with tolerance 0 and IoU >= 0.97 with tolerance 1.0 on the fixture masks and on generated circles, rings, and L shapes. Warnings are reported for the `hole` and `two_blobs` fixtures.

## M3. Format importers and exporters (`:core/formats`)

- [x] YOLO detect and segment: parse, write, data.yaml and classes.txt, all three layouts.
- [x] COCO: polygons, compressed and uncompressed RLE, Roboflow category quirk, all layouts.
- [x] SAM SA-1B: parse and write, `extra` passthrough, edited mask rules.
- [x] Format auto detection for a folder listing (pure function over a list of relative paths plus a file reader).
- [x] Canonical per-image JSON read and write (FORMATS 6).
- [x] Train/val/test split with seed.

Accept: unit tests with small hand written datasets in test resources for each format and layout. Import then export then import gives the same annotations (IoU >= 0.97 for polygon formats, exact for RLE formats).

## M4. Storage and project (`:app/storage`)

- [x] SAF folder picker, persistable permission, recent folders in DataStore.
- [x] Fast listing via `DocumentsContract` queries, image index with cache.
- [x] Read image bounds without decoding.
- [x] `.annotator/` creation, `project.json`, `state.json`, canonical files.
- [x] Lazy import from detected format.
- [x] Autosave with debounce and tmp then rename.

Accept: open a real folder of 1,000+ images in under 3 seconds after first index. Kill the app mid edit, reopen, nothing lost except the last 500 ms at most.

## M5. Viewer

- [x] Editor screen layout from SPEC 4.3.
- [x] Image display with downsampling, viewport transform, two finger pan and pinch zoom.
- [x] Zoom chip with zoom in, zoom out, reset.
- [x] Mask and box overlays, global hide, opacity.
- [x] Prev / next, index display, jump to last edited, jump to next unreviewed.
- [x] Reviewed toggle, progress display, resume at last opened image.
- [x] Image list screen with filters.

Accept: browse a SAM-annotated dataset, every annotation visible and aligned with the image at all zoom levels; reviewed status survives app restart.

## M6. Panels and classes

- [x] Bottom sheet / side panel with Classes and Annotations tabs.
- [x] Per class and per annotation visibility, tap to select, delete.
- [x] Class picker sheet with search, recents, create.
- [x] Class management: add, rename, recolor, merge, delete when unused.

Accept: merge two classes across the whole dataset and see counts update; picker search filters 500 classes without lag.

## M7. Editing tools

- [x] Tool state machine and toolbar.
- [x] Selection tool with mask hit test; box move and resize handles.
- [x] Bounding box tool.
- [x] Brush tool with draw / erase, size slider, live preview circle, loupe. (loupe not implemented, see summary)
- [x] Pencil tool with auto close and fill, draw / erase.
- [x] Contextual selection bar: change class, delete.
- [x] Undo / redo command stack per image.
- [x] Empty mask auto delete.

Accept: every tool works one handed with two finger navigation available at all times; 100 consecutive undo / redo steps restore the exact original RLE.

## M8. Multi select

- [x] Box selection tool.
- [x] Lasso tool.
- [x] Bulk change class and delete, undoable as one step.

## M9. Export UI

- [x] Export screen with format, scope, split, options.
- [x] Background export with progress and cancel.
- [x] Summary with warnings.

Accept: export each format, then train a tiny Ultralytics model for 1 epoch on the YOLO export and load the COCO export with pycocotools without errors.

## M10. Image adjustments and polish

- [x] Brightness, contrast, gamma panel (view only).
- [x] Performance pass against SPEC 6 targets.
- [x] Error states from SPEC 8.
- [x] Tablet layout with side panel.

---

## Later

- Polygon tool and vertex editing.
- SAM smart select (on-device MobileSAM or EdgeSAM).
- Copy annotations from previous image.
- Roboflow JSON import/export (pending open question 3 in SPEC).
