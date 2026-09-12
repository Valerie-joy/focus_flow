"""
Phase-1 validation harness: run the exported iTracker end-to-end on the
desktop using the SAME MediaPipe Face Landmarker model the Android app ships,
so the crop conventions can be checked before any Kotlin is written.

    # one or more still images
    python validate.py --image photo1.jpg photo2.jpg --dump-crops build/crops

    # live webcam (press q to quit)
    python validate.py --webcam

Optionally map the cm prediction onto a phone screen:

    python validate.py --webcam --screen-w-cm 6.7 --screen-h-cm 14.8 \
                       --cam-dx-cm 0 --cam-dy-cm 0.6

where (cam-dx, cam-dy) is the camera centre's offset from the screen's
top-centre in cm (positive dy = camera above the screen). Output then includes
the on-screen point in cm from the screen's top-left and whether it lies on
the panel.

Nothing here is a measurement of accuracy — that needs ground-truth dots,
which is what the Phase-2 calibration screen collects. This proves the
pipeline runs and lets you eyeball the crops.
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preprocess as pp  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_TASK = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "face_landmarker.task")


class Runner:
    def __init__(self, build):
        tfl = os.path.join(build, "itracker.tflite")
        onnx_p = os.path.join(build, "itracker.onnx")
        if os.path.isfile(tfl):
            try:
                from ai_edge_litert.interpreter import Interpreter
            except ImportError:
                from tensorflow.lite import Interpreter  # type: ignore
            self.kind = "tflite"
            self.it = Interpreter(model_path=tfl)
            self.it.allocate_tensors()
            self.inp = {d["name"]: d for d in self.it.get_input_details()}
            self.out = self.it.get_output_details()[0]
        else:
            import onnxruntime as ort
            self.kind = "onnx"
            self.sess = ort.InferenceSession(onnx_p, providers=["CPUExecutionProvider"])

    @staticmethod
    def _canon(n):
        n = n.split(":")[0]
        return n[len("serving_default_"):] if n.startswith("serving_default_") else n

    def _find(self, name):
        for n, d in self.inp.items():
            if self._canon(n) == name:
                return d
        raise KeyError(name)

    def __call__(self, inputs):
        if self.kind == "onnx":
            feed = {k: (np.ascontiguousarray(v.transpose(0, 3, 1, 2)) if v.ndim == 4 else v) for k, v in inputs.items()}
            return self.sess.run(None, feed)[0][0]
        for k, v in inputs.items():
            self.it.set_tensor(self._find(k)["index"], v.astype(np.float32))
        self.it.invoke()
        return self.it.get_tensor(self.out["index"])[0]


def make_landmarker(task_path):
    import mediapipe as mp
    from mediapipe.tasks import python as mpp
    from mediapipe.tasks.python import vision
    opts = vision.FaceLandmarkerOptions(
        base_options=mpp.BaseOptions(model_asset_path=task_path),
        running_mode=vision.RunningMode.IMAGE,
        num_faces=1,
        output_face_blendshapes=False,
        output_facial_transformation_matrixes=False,
    )
    return vision.FaceLandmarker.create_from_options(opts), mp


def to_screen(gaze_cm, args):
    """Camera-space cm -> screen cm from top-left, and on-panel flag."""
    if args.screen_w_cm is None:
        return None
    gx, gy = gaze_cm
    sx = args.screen_w_cm / 2 + gx - args.cam_dx_cm
    sy = args.cam_dy_cm - gy               # +y is up in camera space
    on = 0 <= sx <= args.screen_w_cm and 0 <= sy <= args.screen_h_cm
    return sx, sy, on


def run_frame(img_rgb, landmarker, mp, runner, args, tag, dump_dir=None):
    res = landmarker.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=img_rgb))
    if not res.face_landmarks:
        print(f"{tag}: no face")
        return None
    inputs, boxes = pp.prepare(img_rgb, res.face_landmarks[0])
    gaze = runner(inputs)
    line = f"{tag}: gaze = ({gaze[0]:+.2f}, {gaze[1]:+.2f}) cm from camera"
    scr = to_screen(gaze, args)
    if scr:
        line += f" | screen = ({scr[0]:.2f}, {scr[1]:.2f}) cm | {'ON' if scr[2] else 'OFF'} panel"
    print(line)
    if dump_dir:
        os.makedirs(dump_dir, exist_ok=True)
        for k in ("face", "eye_left", "eye_right"):
            Image.fromarray((inputs[k][0] * 255).astype(np.uint8)).save(os.path.join(dump_dir, f"{tag}_{k}.png"))
        g = (inputs["face_grid"].reshape(pp.GRID, pp.GRID) * 255).astype(np.uint8)
        Image.fromarray(g).resize((250, 250), Image.NEAREST).save(os.path.join(dump_dir, f"{tag}_grid.png"))
    return gaze, boxes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--build", default=os.path.join(HERE, "build"))
    ap.add_argument("--task", default=DEFAULT_TASK, help="face_landmarker.task (defaults to the app's asset)")
    ap.add_argument("--image", nargs="*")
    ap.add_argument("--webcam", action="store_true")
    ap.add_argument("--dump-crops", default=None)
    ap.add_argument("--screen-w-cm", type=float, default=None)
    ap.add_argument("--screen-h-cm", type=float, default=None)
    ap.add_argument("--cam-dx-cm", type=float, default=0.0)
    ap.add_argument("--cam-dy-cm", type=float, default=0.0)
    args = ap.parse_args()

    runner = Runner(args.build)
    print(f"model backend: {runner.kind}")
    landmarker, mp = make_landmarker(args.task)

    if args.image:
        for p in args.image:
            img = np.asarray(Image.open(p).convert("RGB"))
            run_frame(img, landmarker, mp, runner, args, os.path.splitext(os.path.basename(p))[0], args.dump_crops)

    if args.webcam:
        import cv2
        cap = cv2.VideoCapture(0)
        assert cap.isOpened(), "no webcam"
        n = 0
        while True:
            ok, bgr = cap.read()
            if not ok:
                break
            rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
            out = run_frame(rgb, landmarker, mp, runner, args, f"frame{n:05d}", args.dump_crops if n % 30 == 0 else None)
            if out:
                gaze, boxes = out
                for k, (x, y, w, h) in boxes.items():
                    cv2.rectangle(bgr, (x, y), (x + w, y + h), (0, 255, 0) if k == "face" else (255, 128, 0), 2)
                cv2.putText(bgr, f"gaze ({gaze[0]:+.1f}, {gaze[1]:+.1f}) cm", (10, 30),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.9, (0, 255, 255), 2)
            cv2.imshow("iTracker validation  (q to quit)", bgr)
            if cv2.waitKey(1) & 0xFF == ord("q"):
                break
            n += 1
        cap.release()
        cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
