"""
Step 1 of the iTracker export: load the released PyTorch checkpoint, fold the
mean-image subtraction into the graph, export to ONNX, and prove the ONNX
model reproduces PyTorch numerically.

    python export_onnx.py --gazecapture ../../GazeCapture-master/GazeCapture-master \
                          --out build/

Produces build/itracker.onnx and build/itracker.json (the I/O contract the
Android side is written against).
"""
import argparse
import json
import os
import sys

import numpy as np
import scipy.io as sio
import torch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from itracker_model import ITrackerModel, ITrackerWithMeans  # noqa: E402

IM = 224
GRID = 25
INPUT_NAMES = ["face", "eye_left", "eye_right", "face_grid"]
OUTPUT_NAME = "gaze_cm"


def load_mean(path):
    """mean_*_224.mat -> (3, 224, 224) float32 on the 0..1 scale, matching
    ITrackerData.SubtractMean (ToTensor on an HWC float array transposes
    without rescaling, so the /255 is applied here explicitly)."""
    m = sio.loadmat(path, squeeze_me=True)["image_mean"]
    assert m.shape == (IM, IM, 3), f"unexpected mean shape {m.shape} in {path}"
    return torch.from_numpy((m.astype(np.float32) / 255.0).transpose(2, 0, 1).copy())


def load_checkpoint(path):
    # torch 1.1-era pickle; needs the legacy loader and full unpickling.
    ckpt = torch.load(path, map_location="cpu", weights_only=False)
    state = ckpt["state_dict"] if isinstance(ckpt, dict) and "state_dict" in ckpt else ckpt
    # Trained under nn.DataParallel, so every key is prefixed "module.".
    state = {k[len("module."):] if k.startswith("module.") else k: v for k, v in state.items()}
    return state, ckpt


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gazecapture", required=True, help="path to GazeCapture-master (the inner one, containing pytorch/)")
    ap.add_argument("--out", default="build")
    ap.add_argument("--opset", type=int, default=17)
    args = ap.parse_args()

    py = os.path.join(args.gazecapture, "pytorch")
    os.makedirs(args.out, exist_ok=True)

    state, ckpt = load_checkpoint(os.path.join(py, "checkpoint.pth.tar"))
    core = ITrackerModel()
    core.load_state_dict(state, strict=True)
    print(f"checkpoint loaded: epoch={ckpt.get('epoch')} best_prec1={ckpt.get('best_prec1')}")
    print(f"  {sum(p.numel() for p in core.parameters()):,} parameters")

    def sample():
        g = torch.zeros(1, GRID * GRID)
        g[0, 6 * GRID + 8: 6 * GRID + 8 + 10] = 1  # a plausible face box row
        return (
            torch.rand(1, 3, IM, IM),
            torch.rand(1, 3, IM, IM),
            torch.rand(1, 3, IM, IM),
            g,
        )

    # ---- prove the LRN swap is exact ----------------------------------------
    # The checkpoint was trained with nn.CrossMapLRN2d; we export with
    # nn.LocalResponseNorm. Same weights into both, same inputs, must agree.
    legacy = ITrackerModel(legacy_lrn=True)
    legacy.load_state_dict(state, strict=True)
    legacy.eval(); core.eval()
    torch.manual_seed(1)
    lrn_diff = 0.0
    with torch.no_grad():
        for _ in range(4):
            ins = sample()
            lrn_diff = max(lrn_diff, float((legacy(*ins) - core(*ins)).abs().max()))
    print(f"LocalResponseNorm vs legacy CrossMapLRN2d: max |diff| = {lrn_diff:.2e}")
    assert lrn_diff < 1e-4, "LRN replacement is not numerically equivalent"

    model = ITrackerWithMeans(
        core,
        load_mean(os.path.join(py, "mean_face_224.mat")),
        load_mean(os.path.join(py, "mean_left_224.mat")),
        load_mean(os.path.join(py, "mean_right_224.mat")),
    ).eval()

    with torch.no_grad():
        ref = model(*sample())
    print(f"pytorch sanity forward -> gaze_cm = {ref.numpy().ravel()}")

    onnx_path = os.path.join(args.out, "itracker.onnx")
    torch.onnx.export(
        model,
        sample(),
        onnx_path,
        input_names=INPUT_NAMES,
        output_names=[OUTPUT_NAME],
        opset_version=args.opset,
        dynamo=False,          # legacy exporter: LRN maps cleanly to the ONNX LRN op
        do_constant_folding=True,
    )
    print(f"wrote {onnx_path} ({os.path.getsize(onnx_path) / 1e6:.1f} MB)")

    # ---- prove equivalence -------------------------------------------------
    import onnx
    import onnxruntime as ort

    onnx.checker.check_model(onnx.load(onnx_path))
    sess = ort.InferenceSession(onnx_path, providers=["CPUExecutionProvider"])
    worst = 0.0
    torch.manual_seed(0)
    for _ in range(8):
        ins = sample()
        with torch.no_grad():
            want = model(*ins).numpy()
        got = sess.run([OUTPUT_NAME], {n: t.numpy() for n, t in zip(INPUT_NAMES, ins)})[0]
        worst = max(worst, float(np.abs(want - got).max()))
    print(f"onnxruntime vs pytorch: max |diff| over 8 random inputs = {worst:.2e}")
    assert worst < 1e-3, "ONNX export does not reproduce PyTorch"

    contract = {
        "model": "iTracker (Krafka et al., CVPR 2016) — GazeCapture PyTorch checkpoint",
        "licence": "GazeCapture Research License: research use only, no commercial use, cite the paper",
        "inputs": {
            "face":      {"shape": [1, 3, IM, IM], "layout": "NCHW", "dtype": "float32", "range": "0..1 RGB, raw (mean subtraction is inside the graph)"},
            "eye_left":  {"shape": [1, 3, IM, IM], "layout": "NCHW", "dtype": "float32", "range": "0..1 RGB, raw", "note": "subject's physical LEFT eye = right-hand side of an un-mirrored camera frame"},
            "eye_right": {"shape": [1, 3, IM, IM], "layout": "NCHW", "dtype": "float32", "range": "0..1 RGB, raw"},
            "face_grid": {"shape": [1, GRID * GRID], "dtype": "float32", "range": "0/1", "layout": "25x25 flattened row-major (index = y*25 + x); 1 where the face box lies within the full frame"},
        },
        "output": {
            OUTPUT_NAME: {"shape": [1, 2], "dtype": "float32", "units": "centimetres from the camera centre", "axes": "[x, y]; +x = subject's right in the camera image, +y = up (screen below camera in portrait => y < 0)"},
        },
        "reported_accuracy": "2.46 cm mean L2 error on GazeCapture test split (iPhone+iPad), uncalibrated",
        "eye_crop_convention": "GazeCapture used Apple CIDetector face/eye boxes; face crop is zero-padded where the box leaves the frame (prepareDataset.cropImage), then resized to 224x224",
    }
    with open(os.path.join(args.out, "itracker.json"), "w") as f:
        json.dump(contract, f, indent=2)
    print("wrote I/O contract -> itracker.json")


if __name__ == "__main__":
    main()
