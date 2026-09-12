"""
Step 2 of the iTracker export: ONNX -> TensorFlow SavedModel -> TFLite, then
prove the TFLite model reproduces ONNX numerically, and install it into the
Android app's assets.

    python export_tflite.py --build build/ --assets ../../app/src/main/assets

onnx2tf converts the graph to NHWC (channels-last), which is the natural
layout for feeding a Bitmap from Kotlin, so the TFLite input shapes are
[1, 224, 224, 3]. The face grid stays [1, 625].
"""
import argparse
import json
import os
import shutil

import numpy as np

IM, GRID = 224, 25
INPUT_NAMES = ["face", "eye_left", "eye_right", "face_grid"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--build", default="build")
    ap.add_argument("--assets", default=None, help="Android assets dir to copy itracker.tflite into")
    args = ap.parse_args()

    onnx_path = os.path.join(args.build, "itracker.onnx")
    sm_dir = os.path.join(args.build, "saved_model")
    assert os.path.isfile(onnx_path), "run export_onnx.py first"

    import onnx2tf
    onnx2tf.convert(
        input_onnx_file_path=onnx_path,
        output_folder_path=sm_dir,
        copy_onnx_input_output_names_to_tflite=True,
        output_signaturedefs=True,
        non_verbose=True,
    )
    produced = [f for f in os.listdir(sm_dir) if f.endswith("_float32.tflite")]
    assert produced, f"no float32 tflite produced in {sm_dir}: {os.listdir(sm_dir)}"
    tflite_path = os.path.join(args.build, "itracker_fp32.tflite")
    shutil.copyfile(os.path.join(sm_dir, produced[0]), tflite_path)
    print(f"wrote {tflite_path} ({os.path.getsize(tflite_path) / 1e6:.1f} MB)")

    # Float16 weights: half the asset size, dequantised on the fly by XNNPACK.
    # Verified below against the float32 graph.
    import tensorflow as tf
    conv = tf.lite.TFLiteConverter.from_saved_model(sm_dir)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    conv.target_spec.supported_types = [tf.float16]
    fp16_path = os.path.join(args.build, "itracker.tflite")
    with open(fp16_path, "wb") as f:
        f.write(conv.convert())
    print(f"wrote {fp16_path} ({os.path.getsize(fp16_path) / 1e6:.1f} MB, float16 weights)")

    # ---- prove equivalence against ONNX ----------------------------------------
    import onnxruntime as ort
    try:
        from ai_edge_litert.interpreter import Interpreter
    except ImportError:
        from tensorflow.lite import Interpreter  # type: ignore

    sess = ort.InferenceSession(onnx_path, providers=["CPUExecutionProvider"])

    def load(path):
        it = Interpreter(model_path=path)
        it.allocate_tensors()
        return it, {d["name"]: d for d in it.get_input_details()}, it.get_output_details()[0]

    def canon(n):
        # onnx2tf keeps 'face'; TFLiteConverter wraps as 'serving_default_face:0'.
        n = n.split(":")[0]
        return n[len("serving_default_"):] if n.startswith("serving_default_") else n

    def find(in_details, name):
        for n, d in in_details.items():
            if canon(n) == name:
                return d
        raise KeyError(f"tflite input for {name!r} not found in {list(in_details)}")

    def run(it, in_details, out_detail, face, eye_l, eye_r, grid):
        for name, arr in (("face", face), ("eye_left", eye_l), ("eye_right", eye_r)):
            d = find(in_details, name)
            nhwc = np.ascontiguousarray(arr.transpose(0, 2, 3, 1)) if list(d["shape"]) == [1, IM, IM, 3] else arr
            it.set_tensor(d["index"], nhwc)
        it.set_tensor(find(in_details, "face_grid")["index"], grid)
        it.invoke()
        return it.get_tensor(out_detail["index"])

    it32, in32, out32 = load(tflite_path)
    it16, in16, out16 = load(fp16_path)
    print("tflite inputs:")
    for n, d in in16.items():
        print(f"  {n:10s} shape={d['shape'].tolist()} dtype={d['dtype'].__name__}")
    print(f"tflite output: {out16['name']} shape={out16['shape'].tolist()}")

    rng = np.random.default_rng(0)
    worst32 = worst16 = 0.0
    for _ in range(8):
        face = rng.random((1, 3, IM, IM), dtype=np.float32)
        eye_l = rng.random((1, 3, IM, IM), dtype=np.float32)
        eye_r = rng.random((1, 3, IM, IM), dtype=np.float32)
        grid = np.zeros((1, GRID * GRID), np.float32)
        x0, y0, w, h = rng.integers(0, 12, 4)
        for yy in range(y0, min(GRID, y0 + h + 4)):
            grid[0, yy * GRID + x0: yy * GRID + min(GRID, x0 + w + 4)] = 1
        want = sess.run(None, {"face": face, "eye_left": eye_l, "eye_right": eye_r, "face_grid": grid})[0]
        worst32 = max(worst32, float(np.abs(want - run(it32, in32, out32, face, eye_l, eye_r, grid)).max()))
        worst16 = max(worst16, float(np.abs(want - run(it16, in16, out16, face, eye_l, eye_r, grid)).max()))
    print(f"tflite fp32 vs onnxruntime: max |diff| over 8 random inputs = {worst32:.2e} cm")
    print(f"tflite fp16 vs onnxruntime: max |diff| over 8 random inputs = {worst16:.2e} cm")
    assert worst32 < 1e-3, "TFLite conversion does not reproduce ONNX"
    assert worst16 < 5e-2, "float16 weights drift too far from float32"
    # ---- update the contract with the TFLite layout ---------------------------
    contract_path = os.path.join(args.build, "itracker.json")
    with open(contract_path) as f:
        contract = json.load(f)
    contract["tflite"] = {
        "file": "itracker.tflite",
        "weights": "float16 (activations float32); itracker_fp32.tflite is the unquantised reference",
        "inputs": {n: {"name": find(in16, n)["name"], "shape": find(in16, n)["shape"].tolist(), "dtype": "float32"} for n in INPUT_NAMES},
        "output": {"name": out16["name"], "shape": out16["shape"].tolist()},
        "layout": "image inputs are NHWC (channels-last), RGB, 0..1 float; mean subtraction is inside the graph",
        "fp16_vs_fp32_max_abs_diff_cm": round(worst16, 5),
    }
    with open(contract_path, "w") as f:
        json.dump(contract, f, indent=2)

    if args.assets:
        os.makedirs(args.assets, exist_ok=True)
        shutil.copyfile(fp16_path, os.path.join(args.assets, "itracker.tflite"))
        shutil.copyfile(contract_path, os.path.join(args.assets, "itracker.json"))
        print(f"installed itracker.tflite + itracker.json -> {args.assets}")


if __name__ == "__main__":
    main()
