"""
Builds iTracker's four inputs from a camera frame plus MediaPipe Face
Landmarker output. This is the reference implementation of the crop and
face-grid conventions; the Kotlin ITrackerPreprocessor in the app mirrors it
line for line, so any change here must be made there too.

Conventions (from GazeCapture-master/pytorch/prepareDataset.py and
ITrackerData.py):

  * Face crop: the detector's face box, zero-padded where it leaves the
    frame, resized to 224x224.
  * Eye crops: boxes around each eye; "left" is the subject's physical LEFT
    eye, which sits on the RIGHT side of an un-mirrored camera frame.
  * Face grid: a 25x25 mask of where the face box lies in the full frame,
    flattened row-major (index = y*25 + x).

GazeCapture's boxes came from Apple's CIDetector. We synthesise equivalents
from the 478 MediaPipe landmarks; the two constants below are the tunable
part of that approximation and the per-user calibration absorbs residual bias.
"""
import numpy as np
from PIL import Image


def _r(v):
    """Round half up. Matches ITrackerGeometry.roundHalfUp in the app; neither
    Python's banker's round() nor Kotlin's roundToInt() agree at exact .5."""
    return int(np.floor(v + 0.5))

IM = 224
GRID = 25

# Apple's face box is roughly square and includes the forehead; the landmark
# extent stops at the eyebrows, so expand the square upward a little.
FACE_BOX_SCALE = 1.15
FACE_BOX_UP_SHIFT = 0.08       # fraction of side, moved upward
# Apple eye boxes are ~square and about this fraction of the face box width.
EYE_BOX_FRAC_OF_FACE = 0.30

# MediaPipe canonical face-mesh indices. Subject's LEFT eye is the one with
# x > 0.5 in an un-mirrored frame.
LEFT_EYE = [362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398]
RIGHT_EYE = [33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246]
LEFT_IRIS = [473, 474, 475, 476, 477]
RIGHT_IRIS = [468, 469, 470, 471, 472]


def landmarks_to_px(landmarks, frame_w, frame_h):
    """MediaPipe normalised (x, y) -> (N, 2) float pixel coordinates."""
    return np.array([[lm.x * frame_w, lm.y * frame_h] for lm in landmarks], np.float32)


def face_box(pts):
    """Square box (x, y, w, h) ints around the whole landmark set."""
    x0, y0 = pts.min(0)
    x1, y1 = pts.max(0)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    side = max(x1 - x0, y1 - y0) * FACE_BOX_SCALE
    cy -= side * FACE_BOX_UP_SHIFT
    s = _r(side)
    return _r(cx - side / 2), _r(cy - side / 2), s, s


def eye_box(pts, idx, face_w):
    """Square box (x, y, w, h) centred on the mean of the eye-contour landmarks."""
    c = pts[idx].mean(0)
    side = face_w * EYE_BOX_FRAC_OF_FACE
    s = _r(side)
    return _r(c[0] - side / 2), _r(c[1] - side / 2), s, s


def crop_zero_pad(img, box):
    """prepareDataset.cropImage: crop, zero-filling any part outside the frame."""
    x, y, w, h = box
    H, W = img.shape[:2]
    ax, ay = max(x, 0), max(y, 0)
    bx, by = min(x + w, W), min(y + h, H)
    out = np.zeros((h, w, 3), img.dtype)
    if bx > ax and by > ay:
        out[ay - y: by - y, ax - x: bx - x] = img[ay:by, ax:bx]
    return out


def to_input(crop_rgb_u8):
    """224x224, RGB, float32 0..1, NHWC with batch dim. Mean subtraction is
    inside the exported graph, so nothing else is done here."""
    im = Image.fromarray(crop_rgb_u8).resize((IM, IM), Image.BILINEAR)
    return (np.asarray(im, np.float32) / 255.0)[None]


def face_grid(frame_w, frame_h, box):
    """ITrackerData.makeGrid with 0-based params, matching what
    faceGridFromFaceRect would produce for a new detection."""
    x, y, w, h = box
    sx, sy = GRID / frame_w, GRID / frame_h
    gx, gy = _r(x * sx), _r(y * sy)
    gw, gh = _r(w * sx), _r(h * sy)
    g = np.zeros((GRID, GRID), np.float32)
    x0, x1 = max(0, gx), min(GRID, gx + gw)
    y0, y1 = max(0, gy), min(GRID, gy + gh)
    if x1 > x0 and y1 > y0:
        g[y0:y1, x0:x1] = 1
    return g.reshape(1, GRID * GRID)


def prepare(img_rgb_u8, landmarks):
    """Full pipeline. Returns (inputs dict, boxes dict) — boxes are kept so a
    caller can draw or dump the crops for inspection."""
    H, W = img_rgb_u8.shape[:2]
    pts = landmarks_to_px(landmarks, W, H)
    fb = face_box(pts)
    lb = eye_box(pts, LEFT_EYE, fb[2])
    rb = eye_box(pts, RIGHT_EYE, fb[2])
    inputs = {
        "face": to_input(crop_zero_pad(img_rgb_u8, fb)),
        "eye_left": to_input(crop_zero_pad(img_rgb_u8, lb)),
        "eye_right": to_input(crop_zero_pad(img_rgb_u8, rb)),
        "face_grid": face_grid(W, H, fb),
    }
    return inputs, {"face": fb, "eye_left": lb, "eye_right": rb}
