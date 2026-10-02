# Formats Reference

Exact rules for every on-disk format the app reads or writes. When code and this file disagree, this file wins until it is deliberately updated.

Conventions used everywhere:
- Pixel coordinates, origin at the top-left corner of the image, x right, y down.
- Pixel `(col, row)` covers the square `[col, col+1) x [row, row+1)`. Its center is `(col + 0.5, row + 0.5)`.
- Bbox `XYWH` means top-left x, top-left y, width, height.

---

## 1. Run length encoding (RLE)

Same definition as pycocotools (COCO).

- The mask is flattened in **column major** order (go down the first column, then the second column, and so on). This is the opposite of normal row major bitmaps. Getting this wrong is the most likely bug.
- `counts` alternates run lengths of 0s and 1s and **always starts with a run of 0s**. If pixel 0 is set, the first count is `0`.
- Sum of counts equals `height * width`.
- Area is the sum of the odd indexed counts.

### 1.1 Compressed counts string (COCO "compressed RLE")

Each count is turned into a variable length sequence of ASCII characters. For index `i > 2` the value encoded is the delta `counts[i] - counts[i-2]` (note: strictly greater than 2, matching pycocotools).

Encode (per value `x`, as signed 64 bit):

```
more = true
while (more) {
    c = x and 0x1f
    x = x shr 5                       // arithmetic shift
    more = if (c and 0x10 != 0) x != -1L else x != 0L
    if (more) c = c or 0x20
    out.append((c + 48).toChar())
}
```

Decode:

```
while (p < s.length) {
    x = 0L; k = 0; more = true
    while (more) {
        c = s[p].code - 48
        x = x or ((c and 0x1f).toLong() shl (5 * k))
        more = (c and 0x20) != 0
        p++; k++
        if (!more && (c and 0x10) != 0) x = x or (-1L shl (5 * k))
    }
    if (m > 2) x += counts[m - 2]
    counts[m++] = x.toInt()
}
```

### 1.2 Roboflow mask string

Roboflow stores masks as:

```
mask = base64( zlib_compress( compressedCountsString ) )
```

- zlib at the default level (6) with the standard zlib header. `java.util.zip.Deflater()` with default settings produces the same bytes, so re-encoding an untouched mask reproduces Roboflow's string exactly. Tests should still compare at the counts level, not the base64 level, to stay robust.
- The RLE covers the **whole image**, not the bbox.
- The `x` and `y` fields in a Roboflow annotation are the **bbox center**, not the top-left.

Verified on the real sample in `fixtures/roboflow_sample.json`:

| Field | Value |
|---|---|
| Image size (inferred) | 1024 x 1024 |
| Run count | 179 |
| Area | 8661 (matches the `area` field) |
| Bbox XYWH | 525, 339, 86, 127 |
| Bbox center | 568, 402.5 (matches `x`, `y`) |

### 1.3 Derived values

- Bbox from RLE: min/max of set pixel columns and rows. `w = maxCol - minCol + 1`, `h = maxRow - minRow + 1`. Empty mask gives `[0, 0, 0, 0]`.
- These must match pycocotools `toBbox` and `area` exactly (see `fixtures/rle_cases.json`).

---

## 2. Polygon and mask conversion

### 2.1 Polygon to mask (import)

- A pixel is inside if its **center** is inside the polygon (even-odd rule).
- Multiple polygons for one annotation are OR-ed together.
- pycocotools rasterizes slightly differently at edges. Tests compare with IoU, not exact equality (IoU >= 0.97 for shapes larger than 20 x 20 px).

### 2.2 Mask to polygon (export to YOLO seg or COCO polygons)

1. Trace each connected component's **outer** boundary along pixel edges (crack following / marching squares on the pixel corner grid, 4-connectivity for foreground). Before simplification this outline rasterizes back to the identical component.
2. Simplify with Douglas-Peucker, tolerance in image pixels (default 1.0, user option 0 to 5).
3. Drop polygons with fewer than 3 points or area below 1 px.
4. Holes are not traced. Report a warning "N masks had holes that were filled" when the original component contains interior background pixels.
5. Multiple components:
   - COCO: write each component as its own polygon in the `segmentation` list (COCO supports this).
   - YOLO seg: join them into a single polygon by connecting each component to the next at their closest pair of points with a zero width bridge (same idea as Ultralytics `merge_multi_segment`). Option: "Largest component only". Report a warning count either way.

---

## 3. YOLO (Ultralytics)

### 3.1 Label lines

One `.txt` per image, same base name as the image, one object per line, space separated, all coordinates normalized to `[0, 1]` by image width and height.

| Kind | Line | Value count |
|---|---|---|
| Detect | `cls cx cy w h` | 5 |
| Segment | `cls x1 y1 x2 y2 ... xn yn` | 1 + 2n, n >= 3 |

Import rules:
- 5 values: box. 7 or more values with an odd total: polygon. Anything else: report the line as malformed and skip it.
- Values outside `[0, 1]` are clamped with a warning.
- Empty or missing `.txt` means the image has no annotations (not an error).
- Image width and height come from the image file header (read bounds only, do not decode the full image).

Export rules:
- Detect: masks export their bbox.
- Segment: masks export via section 2.2. Boxes export as a 4 point polygon `TL, TR, BR, BL`.
- Write 6 decimal places.
- An image with no annotations still gets an empty `.txt` (Ultralytics treats it as a background image).

### 3.2 Classes

`data.yaml`:

```yaml
path: .
train: images/train
val: images/val
test: images/test     # omitted if no test split
names:
  0: person
  1: forklift
```

- Import also accepts `names` as a list, and a plain `classes.txt` (one name per line) when there is no yaml.
- Export remaps internal class ids to contiguous `0..N-1` ordered by internal id. Unused classes are still included so ids are stable across exports.

### 3.3 Layouts recognized on import

```
A) images/ + labels/                      flat
B) images/{train,val,test}/ + labels/{train,val,test}/
C) {train,valid,test}/images/ + {train,valid,test}/labels/   (Roboflow export)
```

The label file for `.../images/x/foo.jpg` is `.../labels/x/foo.txt` (replace the last `images` path segment with `labels`, swap the extension).

Export writes layout B.

---

## 4. COCO

### 4.1 Structure

```json
{
  "images": [{"id": 1, "file_name": "train/foo.jpg", "width": 1024, "height": 1024}],
  "categories": [{"id": 1, "name": "person", "supercategory": "none"}],
  "annotations": [{
    "id": 1, "image_id": 1, "category_id": 1,
    "bbox": [525, 339, 86, 127],
    "area": 8661,
    "iscrowd": 0,
    "segmentation": [[x1, y1, x2, y2, ...]]
  }]
}
```

`segmentation` may be:
- Polygon list: `[[x1, y1, ...], [ ... ]]` in absolute pixels.
- Compressed RLE: `{"size": [h, w], "counts": "string"}`.
- Uncompressed RLE: `{"size": [h, w], "counts": [int, int, ...]}`.
- Missing or empty: treat as a box annotation from `bbox`.

Import accepts all of the above regardless of `iscrowd`.

Export options:
- Masks as **polygons** (default, widest tool support) or **compressed RLE** (lossless, keeps holes).
- `iscrowd` is always `0`.
- `area` is the mask pixel area for masks, `w * h` for boxes.
- Category ids start at 1. Ids are 1-based remaps of internal ids, same ordering rule as YOLO.
- `file_name` is relative to the export images folder.

### 4.2 Layouts recognized on import

```
A) annotations/instances_<split>.json + images/<split>/   (or images at file_name paths)
B) <split>/_annotations.coco.json with images next to it (Roboflow export)
C) any single *.json at the root with "images" and "annotations" keys
```

Roboflow quirk: COCO exports from Roboflow often include an extra category (commonly id 0, named after the dataset) used only as a `supercategory`. Skip any category that has zero annotations and whose name is used as another category's `supercategory`.

Export layout:

```
export/coco/
  annotations/instances_train.json
  annotations/instances_val.json
  images/train/...
  images/val/...
```

Without a split, a single `annotations/instances_default.json` and `images/` folder.

---

## 5. SAM (SA-1B style)

Assumed target, to be confirmed by the project owner. One JSON per image, same base name, next to the image.

```json
{
  "image": {"image_id": 42, "width": 1024, "height": 1024, "file_name": "foo.jpg"},
  "annotations": [{
    "id": 7,
    "segmentation": {"size": [1024, 1024], "counts": "compressed rle string"},
    "bbox": [525, 339, 86, 127],
    "area": 8661,
    "predicted_iou": 0.97,
    "stability_score": 0.95,
    "crop_box": [0, 0, 1024, 1024],
    "point_coords": [[568.0, 402.5]]
  }]
}
```

- SA-1B has **no classes**. On import, annotations get the class from `category_id` / `category_name` if present (non-standard fields written by this app or other tools), otherwise a project level default class `object`.
- Unknown fields (`predicted_iou`, `stability_score`, `crop_box`, `point_coords`, anything else) are kept in `Annotation.extra` and written back unchanged on export if the mask was not edited.
- If a mask was edited, drop `predicted_iou` and `stability_score` (they no longer describe the mask), recompute `bbox` and `area`, set `crop_box` to the full image.
- Export option "Include class fields" (default on) adds `category_id` and `category_name` to every annotation.
- Boxes have no SAM representation. Export them as filled rectangular masks.

---

## 6. App canonical per-image file

`.annotator/annotations/<image relative path>.json`:

```json
{
  "version": 1,
  "image": {"path": "train/images/foo.jpg", "width": 1024, "height": 1024},
  "annotations": [
    {
      "id": "6f1c...",
      "classId": 11,
      "type": "mask",
      "rle": "X[]`0]1k0oN^l0W1...",
      "bbox": [525, 339, 86, 127],
      "area": 8661,
      "source": "IMPORTED_SAM",
      "extra": {"predicted_iou": 0.97}
    },
    {
      "id": "a90b...",
      "classId": 3,
      "type": "box",
      "bbox": [10, 20, 100, 50],
      "source": "USER"
    }
  ]
}
```

- `rle` is the compressed counts string (section 1.1). `bbox` and `area` are written for readability and recomputed on load.
- `version` allows future migrations.
