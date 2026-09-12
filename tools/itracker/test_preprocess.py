"""
Self-test for preprocess.py + the installed TFLite model, using synthetic
landmarks so it runs without a camera or a face image. It pins the crop
conventions the Kotlin ITrackerPreprocessor must reproduce.

    python test_preprocess.py
"""
import os
import sys
from types import SimpleNamespace

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preprocess as pp  # noqa: E402
from validate import Runner  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))


def fake_landmarks(W, H, cx, cy, face_w):
    """478 points: a face-shaped blob centred at (cx, cy); the eye-contour
    indices are placed where eyes would be so eye_box() has real targets."""
    rng = np.random.default_rng(3)
    pts = np.stack([rng.uniform(cx - face_w / 2, cx + face_w / 2, 478),
                    rng.uniform(cy - face_w * 0.55, cy + face_w * 0.55, 478)], 1)
    eye_y = cy - face_w * 0.12
    for i in pp.LEFT_EYE + pp.LEFT_IRIS:     # subject's left = image right
        pts[i] = (cx + face_w * 0.20, eye_y)
    for i in pp.RIGHT_EYE + pp.RIGHT_IRIS:
        pts[i] = (cx - face_w * 0.20, eye_y)
    return [SimpleNamespace(x=x / W, y=y / H) for x, y in pts]


def main():
    W, H = 640, 480
    img = np.zeros((H, W, 3), np.uint8)
    img[..., 0] = np.linspace(0, 255, W, dtype=np.uint8)[None, :]   # R ramps left->right
    img[..., 1] = np.linspace(0, 255, H, dtype=np.uint8)[:, None]   # G ramps top->bottom

    # 1. centred face: no padding, grid box in the middle, left eye on the right
    lms = fake_landmarks(W, H, 320, 240, 200)
    inputs, boxes = pp.prepare(img, lms)
    for k in ("face", "eye_left", "eye_right"):
        assert inputs[k].shape == (1, 224, 224, 3) and inputs[k].dtype == np.float32
        assert 0.0 <= inputs[k].min() and inputs[k].max() <= 1.0
    assert inputs["face_grid"].shape == (1, 625)
    assert boxes["eye_left"][0] > boxes["eye_right"][0], "subject's LEFT eye must be on the image RIGHT"
    # the R channel ramps with x, so the left-eye crop must be redder than the right-eye crop
    assert inputs["eye_left"][..., 0].mean() > inputs["eye_right"][..., 0].mean()
    g = inputs["face_grid"].reshape(25, 25)
    ys, xs = np.nonzero(g)
    assert 8 <= xs.mean() <= 16 and 8 <= ys.mean() <= 16, "centred face should light the middle of the grid"
    print(f"centred: face box={boxes['face']}, grid cells lit={int(g.sum())} at x~{xs.mean():.1f} y~{ys.mean():.1f}")

    # 2. face pushed off the top-left corner: crop must zero-pad, grid must clamp
    lms = fake_landmarks(W, H, 40, 30, 200)
    inputs, boxes = pp.prepare(img, lms)
    fb = boxes["face"]
    assert fb[0] < 0 and fb[1] < 0, "box should extend past the frame"
    face = inputs["face"][0]
    assert face[:20, :20].max() == 0.0, "out-of-frame region must be zero-padded, not clamped"
    g = inputs["face_grid"].reshape(25, 25)
    assert g[0, 0] == 1 and g.sum() > 0, "grid must clamp to the frame edge"
    print(f"off-corner: face box={fb}, zero-padded OK, grid cells lit={int(g.sum())}")

    # 3. grid orientation: row-major, index = y*25 + x
    flat = inputs["face_grid"][0]
    yy, xx = np.nonzero(g)
    assert all(flat[y * 25 + x] == 1 for y, x in zip(yy, xx))
    print("grid flattening: row-major confirmed")

    # 4. the installed model accepts these tensors and returns a finite 2-vector
    runner = Runner(os.path.join(HERE, "build"))
    out = runner(inputs)
    assert out.shape == (2,) and np.all(np.isfinite(out))
    print(f"model ({runner.kind}): gaze_cm = ({out[0]:+.2f}, {out[1]:+.2f})  [synthetic input — value is meaningless]")

    assets = os.path.join(HERE, "..", "..", "app", "src", "main", "assets")
    runner2 = Runner(assets)
    out2 = runner2(inputs)
    assert np.allclose(out, out2, atol=1e-4), "installed asset differs from build output"
    print("installed app asset matches build output")
    print("\nALL PREPROCESS TESTS PASSED")


if __name__ == "__main__":
    main()
