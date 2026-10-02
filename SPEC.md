# Annotator: Product and Technical Spec

Working name "Annotator", package `com.example.annotator`. Rename freely.

An Android app for reviewing and editing image segmentation datasets on a phone or tablet. It opens a folder of images, shows any existing YOLO, COCO, or SAM annotations, lets the user fix them with box, brush, and pencil tools, tracks which images have been reviewed, and exports clean datasets in YOLO, COCO, and SAM formats.

The primary user is a single person cleaning up an auto-labeled dataset (for example one produced by SAM) who needs to step through every image, fix mistakes, and keep track of what has been checked.

---

## 1. Platform and stack

| Area | Choice | Notes |
|---|---|---|
| Platform | Android only | minSdk 26, target the current stable SDK |
| Language | Kotlin | |
| UI | Jetpack Compose + Material 3 | Editor canvas is a custom Compose `Canvas` |
| Async | Kotlin coroutines + Flow | |
| JSON | kotlinx.serialization | |
| Compression | `java.util.zip` Deflater / Inflater | No extra dependency |
| Base64 | `java.util.Base64` | Works on JVM tests and Android 26+ |
| File access | Storage Access Framework (tree URI) | See section 7 |
| Contours | Pure Kotlin (Moore neighbor tracing + Douglas-Peucker) | No OpenCV in MVP |
| Tests | JUnit 5 on the `:core` JVM module, Compose UI tests for the app | |

Why native and not web: Chrome on Android cannot open a local folder for read and write, so "load images by directory" and in-place autosave are not possible in a web app. Brush editing on full resolution masks is also far more reliable with native bitmaps.

### Module layout

```
:core   Pure Kotlin JVM library. No Android imports. Fully unit tested.
        model/      Annotation, Project, ClassDef, ImageEntry, ReviewState
        codec/      RLE (COCO compressed), Roboflow mask string, bbox/area
        geometry/   Rasterize polygons, trace contours, simplify, hit testing
        formats/    yolo/, coco/, sam/ importers and exporters
        editing/    Command objects for undo/redo, mask boolean ops
:app    Android application.
        storage/    SAF folder access, sidecar read/write, autosave
        editor/     Canvas, viewport transform, tools, gestures
        ui/         Screens, panels, class picker, toolbars
        render/     Mask overlay bitmaps, image adjustments
```

Rule: anything that can be expressed without Android types lives in `:core` so it can be tested on the JVM in seconds.

---

## 2. Data model (canonical, in `:core`)

All coordinates are in **original image pixels**, origin at the top-left, x to the right, y down.

```kotlin
data class ClassDef(val id: Int, val name: String, val color: Int /* ARGB */)

sealed interface Shape {
    data class Box(val x: Float, val y: Float, val w: Float, val h: Float) : Shape  // top-left based
    data class Mask(val rle: Rle) : Shape                                           // full image RLE
}

data class Annotation(
    val id: String,          // UUID, stable across edits
    val classId: Int,
    val shape: Shape,
    val source: Source,      // IMPORTED_YOLO, IMPORTED_COCO, IMPORTED_SAM, USER
    val extra: Map<String, JsonElement> = emptyMap() // passthrough, e.g. SAM predicted_iou
)

data class Rle(val height: Int, val width: Int, val counts: IntArray) // column major, starts with zeros
```

Key rules:

- Masks are always stored as **full image RLE** (same as COCO and Roboflow). Bbox and area are derived, never stored as truth.
- Imported polygons (YOLO seg, COCO polygon) are rasterized to masks on import. There is no polygon editing in the MVP, so masks are the only editable region type.
- An annotation whose mask becomes empty after erasing is deleted automatically.
- `extra` preserves fields the app does not understand so SAM exports can round-trip them.

---

## 3. Project files on disk

The user picks a dataset folder. The app never rewrites the user's original label files unless they explicitly export over them.

```
<dataset>/
  images...                    (any supported layout, see FORMATS.md)
  .annotator/
    project.json               classes, detected source format, settings
    state.json                 review status, last opened image, timestamps
    annotations/
      <image relative path>.json   canonical annotations for that image
    export/
      yolo/ coco/ sam/         default export targets
```

- On first open, the app detects the existing format, imports everything into `.annotator/annotations/`, and from then on those files are the source of truth.
- An image with no canonical file falls back to the original labels (lazy import), so opening a huge dataset is fast.

`state.json`:

```json
{
  "version": 1,
  "lastOpened": "train/images/img_0042.jpg",
  "images": {
    "train/images/img_0042.jpg": {
      "reviewed": true,
      "reviewedAt": "2026-10-01T15:20:00+08:00",
      "lastEditedAt": "2026-10-01T15:18:12+08:00"
    }
  }
}
```

---

## 4. Screens

### 4.1 Home

- "Open folder" button (SAF tree picker).
- List of recently opened folders with progress, e.g. `312 / 500 reviewed`.

### 4.2 Image list (optional quick jump)

- Grid of thumbnails with a reviewed checkmark and annotation count.
- Filters: All, Unreviewed, Reviewed, No annotations, Edited.
- Tap opens the editor at that image.

### 4.3 Editor (main screen)

```
+----------------------------------------------+
| <  img_0042.jpg   42/500   [Reviewed v]   >  |  top bar
+----------------------------------------------+
|                                              |
|                 image canvas                 |
|           with annotation overlays           |
|                                     [125%]   |  zoom chip: -, +, reset
|                                     [adj]    |  adjustments toggle
+----------------------------------------------+
| brush: (draw)(erase)  size ----o----  24px   |  tool options row (contextual)
+----------------------------------------------+
| sel  boxsel lasso  box  brush pencil pan | undo redo | eye | panel |
+----------------------------------------------+
```

Top bar:
- Previous / next image buttons. Swipe is NOT used for navigation (it conflicts with drawing).
- Filename and index `42/500`.
- Reviewed toggle. Progress text `312/500 reviewed` under the title or in the overflow.
- Overflow menu: Jump to last edited, Jump to next unreviewed, Image list, Export, Project settings.

Canvas:
- Shows the image with annotation overlays. Masks are filled with the class color at the chosen opacity plus a 1 px outline. Boxes are outlined with a small class label chip.
- Selected annotations get a thicker outline and handles (boxes only).

Panel (bottom sheet on phones, side panel on tablets), two tabs:
- **Classes in this image**: each class with its color, count, and a visibility toggle. Tapping a class selects all its annotations.
- **Annotations in this image**: list of every annotation with class, type (box or mask), area. Tap selects and centers it. Each row has a visibility toggle and delete.

Annotation visibility:
- Global eye button hides or shows all overlays.
- Opacity slider in the panel header.

Zoom indicator:
- Chip showing the current zoom percent, with zoom in, zoom out, and reset view (fit image to screen).

Image adjustments (temporary, view only):
- Brightness, contrast, gamma sliders plus reset.
- Applied only to the displayed image. Never written to disk or exported.
- Settings persist while moving between images in the session.

### 4.4 Class picker (bottom sheet)

Shown after creating a new annotation and when changing the class of a selection.
- Search field with live filtering (case insensitive substring).
- Recently used classes pinned at the top.
- If the search has no exact match, show "Create class '<query>'".
- Cancel discards a just created annotation.

### 4.5 Class management (project settings)

- List all classes with color, name, and total count in the dataset.
- Add, rename, recolor.
- Merge class A into B (reassign all annotations, then delete A).
- Delete only allowed when count is 0, or via merge.
- Class ids stay stable. Export remaps ids to the contiguous ranges each format needs.

### 4.6 Export

- Choose format: YOLO detect, YOLO segment, COCO, SAM.
- Choose scope: all images, reviewed only.
- Optional train / val / test split with percentages and seed.
- Options per format (see FORMATS.md), e.g. COCO masks as RLE or polygons, YOLO polygon simplification tolerance.
- Output to `.annotator/export/<format>/` or a user chosen folder.
- Shows a summary with warnings (masks with holes flattened, multi-blob masks joined, empty annotations dropped).

---

## 5. Tools

General gesture rules (apply to every tool):
- **Two fingers always pan and pinch zoom.** One finger is the active tool.
- Double tap with the Pan tool resets the view.
- Strokes are rendered live and committed on finger up as a single undo step.
- While drawing, a magnifier loupe appears near the finger (top-left or top-right, whichever is farther from the finger).

| Tool | One finger behavior |
|---|---|
| Selection | Tap selects the topmost annotation under the finger (mask hit test, then box). Tap empty space clears. Drag on a selected box handle resizes; drag inside a selected box moves it. |
| Box selection | Drag a rectangle. Selects every annotation whose bbox intersects it. |
| Lasso | Draw a freeform loop. Selects every annotation whose bbox center falls inside it. |
| Bounding box | Drag to create a box. On release, class picker opens. Minimum size 4 x 4 image px or it is discarded. |
| Brush | Paints a circle of the chosen size. Draw mode adds to the target mask, erase mode removes from it. |
| Pencil | Draw an outline. On release the path closes and the interior is filled. Draw mode adds, erase mode subtracts. |
| Pan | One finger pans. |

Brush and pencil target:
- If exactly one mask annotation is selected, strokes edit that annotation.
- If nothing is selected, a draw stroke creates a new mask annotation and opens the class picker on release.
- Erase mode with nothing selected does nothing and shows a hint "Select an annotation to erase from".
- Selecting a box and using the brush converts it to a mask after a confirm dialog.

Brush and pencil options row:
- Draw / Erase segmented button.
- Size slider in **screen pixels** (4 to 120), shown also as the equivalent image pixels. A live preview circle follows the finger, and appears in the center of the canvas while dragging the slider.

Selection actions (contextual bar when something is selected):
- Change class (opens class picker, applies to all selected).
- Delete.
- Count chip "3 selected".

Undo / redo:
- Command pattern. Every commit (create, delete, class change, box move or resize, mask stroke) is one command.
- Mask stroke commands store the RLE before and after (RLE is small and makes undo exact).
- History is per image, kept for the session, max 100 steps. Navigating to another image keeps that image's history in memory (LRU of 10 images).

---

## 6. Rendering and performance

- Display image: decode with `inSampleSize` so the longest side is at most 4096 px. Keep a scale factor between display bitmap and original pixel space.
- Masks stay at original resolution in RLE. For display, each mask is rasterized once into an `ALPHA_8` bitmap at display resolution and drawn with a color filter. Re-rasterize only the annotation that changed.
- The mask being edited is decoded to an `ALPHA_8` bitmap at **original** resolution (12 MB for 4000 x 3000). Only one mask is editable at a time.
- Brush strokes draw onto the editable bitmap with `Paint` (`SRC_OVER` for draw, `DST_OUT` / clear for erase). Commit re-encodes to RLE off the main thread.
- Image adjustments: brightness and contrast via `ColorMatrix`; gamma via a 256 entry lookup table applied to a cached preview bitmap on a background thread. Debounce slider updates by 50 ms.
- Target: next / previous image ready in under 300 ms for a 12 MP JPEG on a mid range phone.

---

## 7. Storage and autosave

- Folder access: `ACTION_OPEN_DOCUMENT_TREE`, then `takePersistableUriPermission` for read and write. Store the tree URI in DataStore for "recent folders".
- Listing files: query `DocumentsContract.buildChildDocumentsUriUsingTree` directly. Do not use `DocumentFile.listFiles()` in loops, it is too slow for thousands of files.
- Build an index of images once per open and cache it in `.annotator/index.json` with file sizes and modified times to detect changes.
- Autosave: write the current image's canonical JSON 500 ms after the last change, and immediately on image change, `onPause`, and export. Writes go to `<name>.json.tmp` then rename, so a crash never leaves a half written file. If rename is unsupported by the provider, write in place with `"wt"` mode.
- Review state and last opened image are saved on every change with the same debounce.

---

## 8. Error handling

- Unreadable image: show a placeholder with the error, allow skipping, mark it in the image list.
- Malformed label file: import what parses, list problems in an import report, never crash.
- Lost folder permission: return to Home with a "Re-open folder" prompt.
- Label references an unknown class index: create a placeholder class `class_<n>` and warn.

---

## 9. Out of scope for MVP (planned later)

- Polygon tool and vertex editing.
- SAM smart select (tap to segment with an on-device model).
- Copy annotations from previous image.
- Cloud sync, accounts, collaboration.
- Baked augmentation on export.
- iOS.

---

## 10. Open questions

1. Should export optionally write labels back into the original dataset layout (in place) instead of a separate export folder? Default is separate folder for safety.
2. Confirm the "SAM format" target is SA-1B style (one JSON per image). See FORMATS.md.
3. Should Roboflow's annotation JSON (the object with `"type": "mask"` and a base64 `mask` string) also be an import/export format, or only used as the internal mask encoding reference?
