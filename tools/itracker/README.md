# iTracker export for FocusFlow

Turns the GazeCapture / iTracker checkpoint (Krafka et al., CVPR 2016) into
`app/src/main/assets/itracker.tflite`, the point-of-regard model FocusFlow uses
to estimate **where on the screen** the user is looking. MediaPipe Face
Landmarker still does detection; iTracker adds a gaze *point* on top of it.

## Licence — read this first

The GazeCapture dataset, models and code are released under a **Research
License** (`GazeCapture-master/LICENSE.md`): research use only, **no
commercial use** in any form (including models derived from it), and any work
made public must cite:

> Krafka, K., Khosla, A., Kellnhofer, P., Kannan, H., Bhandarkar, S.,
> Matusik, W., & Torralba, A. (2016). *Eye Tracking for Everyone.*
> IEEE Conference on Computer Vision and Pattern Recognition (CVPR).

That is fine for the FocusFlow student project and the STA manuscript. It
means the app cannot be published commercially while it ships this model.

## Pipeline

```
checkpoint.pth.tar ──export_onnx.py──▶ itracker.onnx ──export_tflite.py──▶ itracker.tflite
   (PyTorch 1.1 pickle)                 (opset 17)                          (float16 weights)
```

Every stage is verified numerically against the previous one on random
inputs; the scripts assert and abort if anything drifts:

| Check | Result |
|---|---|
| `nn.LocalResponseNorm` vs the checkpoint's legacy `nn.CrossMapLRN2d` | 3e-05 |
| ONNX Runtime vs PyTorch | 9e-06 |
| TFLite float32 vs ONNX Runtime | 2e-05 cm |
| TFLite float16 vs ONNX Runtime | 3e-03 cm |

Two things were folded into the graph so the Android side cannot get them
wrong: the three **mean-image subtractions** (feed raw 0..1 RGB), and the LRN
layers are emitted as the proper ONNX `LRN` op (PyTorch's default export
decomposes them into a 3-D pool that does not survive NHWC conversion).

## Reproduce

```bash
python -m venv .venv && .venv/Scripts/activate           # Windows
pip install --index-url https://download.pytorch.org/whl/cpu torch torchvision
pip install -r requirements.txt

python export_onnx.py   --gazecapture ../../GazeCapture-master/GazeCapture-master --out build
python export_tflite.py --build build --assets ../../app/src/main/assets
python test_preprocess.py
```

`GazeCapture-master/` is gitignored (98 MB, mostly Caffe weights we do not
use). Download the release from https://github.com/CSAILVision/GazeCapture
and place it at the repo root to re-run the export.

## Model contract

Written to `build/itracker.json` and installed beside the model. Summary:

| Input | TFLite name | Shape | Notes |
|---|---|---|---|
| `face` | `serving_default_face:0` | `[1,224,224,3]` | NHWC, RGB, float32 0..1, raw |
| `eye_left` | `serving_default_eye_left:0` | `[1,224,224,3]` | subject's physical **left** eye = **right** side of an un-mirrored frame |
| `eye_right` | `serving_default_eye_right:0` | `[1,224,224,3]` | |
| `face_grid` | `serving_default_face_grid:0` | `[1,625]` | 25×25 mask of the face box within the frame, row-major (`y*25+x`) |

Output `[1,2]` = `(x, y)` in **centimetres from the camera centre**: +x to the
subject's right in the camera image, +y up. In portrait the screen is below the
camera, so on-screen points have y < 0. Reported accuracy of this checkpoint
is 2.46 cm mean error on the GazeCapture test split (iPhone + iPad), before
any per-user calibration.

## Crop conventions

`preprocess.py` is the reference implementation; the Kotlin
`ITrackerPreprocessor` mirrors it. GazeCapture's boxes came from Apple's
CIDetector; we synthesise equivalents from the 478 MediaPipe landmarks:

* **Face box** — square around the full landmark extent, scaled ×1.15 and
  shifted up 8 % to include the forehead as Apple's box did. Zero-padded
  where it leaves the frame (`prepareDataset.cropImage`), then resized to 224².
* **Eye boxes** — square, 0.30 × face-box width, centred on the mean of that
  eye's 16 contour landmarks.
* **Face grid** — `ITrackerData.makeGrid` semantics with 0-based params.

The two constants (`FACE_BOX_SCALE`, `EYE_BOX_FRAC_OF_FACE`) are the tunable
part of this approximation; the per-user calibration in the app absorbs
residual bias.

## Validate on your own face

```bash
python validate.py --webcam                              # live boxes + gaze readout
python validate.py --image me.jpg --dump-crops build/crops   # inspect the 224² crops
```

Add `--screen-w-cm 6.7 --screen-h-cm 14.8 --cam-dy-cm 0.6` to also print the
mapped on-screen point and an ON/OFF-panel flag. This proves the pipeline
runs and lets you eyeball the crops; it is **not** an accuracy measurement —
that needs ground-truth dots, which the calibration screen collects.
