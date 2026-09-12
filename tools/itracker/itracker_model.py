"""
iTracker network definition, used only to load the released checkpoint for
export. Mirrors GazeCapture-master/pytorch/ITrackerModel.py exactly (the
layer structure must match the checkpoint's state_dict key by key); the
unused imports and the training-side helpers have been dropped.

Original author: Petr Kellnhofer (MIT CSAIL), 2018.

Licence: the GazeCapture "Research License" — research use only, no
commercial use, and any work made public must cite:

    Krafka, K., Khosla, A., Kellnhofer, P., Kannan, H., Bhandarkar, S.,
    Matusik, W., & Torralba, A. (2016). Eye Tracking for Everyone.
    IEEE Conference on Computer Vision and Pattern Recognition (CVPR).

See GazeCapture-master/LICENSE.md for the full terms.
"""
import torch
import torch.nn as nn
import torch.nn.functional as F

LRN_SIZE, LRN_ALPHA, LRN_BETA, LRN_K = 5, 0.0001, 0.75, 1.0


class _OnnxLRN(torch.autograd.Function):
    """Cross-channel LRN that exports as a single ONNX `LRN` node.

    Numerically this is F.local_response_norm, but exporting that directly
    emits its decomposition (pad -> avg_pool3d over channels -> pow), and
    the 3-D pool does not survive onnx2tf's NHWC conversion. The `symbolic`
    hook makes the TorchScript exporter write the proper op instead, which
    onnx2tf maps straight onto tf.nn.local_response_normalization. ONNX LRN,
    Caffe LRN and torch's LocalResponseNorm all use the same formula:
        y_c = x_c / (k + alpha/size * sum_{c' in window} x_c'^2)^beta
    """

    @staticmethod
    def forward(ctx, x):
        return F.local_response_norm(x, LRN_SIZE, LRN_ALPHA, LRN_BETA, LRN_K)

    @staticmethod
    def symbolic(g, x):
        return g.op("LRN", x, alpha_f=LRN_ALPHA, beta_f=LRN_BETA, bias_f=LRN_K, size_i=LRN_SIZE)


class ExportLRN(nn.Module):
    def forward(self, x):
        return _OnnxLRN.apply(x)


def _lrn(legacy: bool) -> nn.Module:
    """The original uses nn.CrossMapLRN2d, a Lua-torch-era module implemented
    as an autograd Function that the ONNX exporter cannot trace (it leaves an
    opaque PythonOp). ExportLRN computes the identical Caffe-style formula
    and exports as the ONNX `LRN` op. LRN has no parameters, so the
    state_dict is unaffected. export_onnx.py asserts the two agree
    numerically before exporting, so this is verified rather than assumed."""
    if legacy:
        return nn.CrossMapLRN2d(size=LRN_SIZE, alpha=LRN_ALPHA, beta=LRN_BETA, k=LRN_K)
    return ExportLRN()


class ItrackerImageModel(nn.Module):
    """AlexNet-style trunk. Shared between both eyes; a separate instance with
    its own weights is used for the face."""

    def __init__(self, legacy_lrn: bool = False):
        super().__init__()
        self.features = nn.Sequential(
            nn.Conv2d(3, 96, kernel_size=11, stride=4, padding=0),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(kernel_size=3, stride=2),
            _lrn(legacy_lrn),
            nn.Conv2d(96, 256, kernel_size=5, stride=1, padding=2, groups=2),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(kernel_size=3, stride=2),
            _lrn(legacy_lrn),
            nn.Conv2d(256, 384, kernel_size=3, stride=1, padding=1),
            nn.ReLU(inplace=True),
            nn.Conv2d(384, 64, kernel_size=1, stride=1, padding=0),
            nn.ReLU(inplace=True),
        )

    def forward(self, x):
        x = self.features(x)
        return x.view(x.size(0), -1)


class FaceImageModel(nn.Module):
    def __init__(self, legacy_lrn: bool = False):
        super().__init__()
        self.conv = ItrackerImageModel(legacy_lrn)
        self.fc = nn.Sequential(
            nn.Linear(12 * 12 * 64, 128),
            nn.ReLU(inplace=True),
            nn.Linear(128, 64),
            nn.ReLU(inplace=True),
        )

    def forward(self, x):
        return self.fc(self.conv(x))


class FaceGridModel(nn.Module):
    """The 25x25 binary face-position mask, flattened row-major."""

    def __init__(self, grid_size=25):
        super().__init__()
        self.fc = nn.Sequential(
            nn.Linear(grid_size * grid_size, 256),
            nn.ReLU(inplace=True),
            nn.Linear(256, 128),
            nn.ReLU(inplace=True),
        )

    def forward(self, x):
        return self.fc(x.view(x.size(0), -1))


class ITrackerModel(nn.Module):
    """Inputs are mean-subtracted 224x224 RGB tensors (NCHW, 0..1 scale minus
    the per-pathway mean image) plus the flattened face grid. Output is the
    gaze point in centimetres relative to the camera centre: +x to the
    subject's right in the camera image, +y upward (so y is negative for a
    screen below the camera in portrait)."""

    def __init__(self, legacy_lrn: bool = False):
        super().__init__()
        self.eyeModel = ItrackerImageModel(legacy_lrn)
        self.faceModel = FaceImageModel(legacy_lrn)
        self.gridModel = FaceGridModel()
        self.eyesFC = nn.Sequential(
            nn.Linear(2 * 12 * 12 * 64, 128),
            nn.ReLU(inplace=True),
        )
        self.fc = nn.Sequential(
            nn.Linear(128 + 64 + 128, 128),
            nn.ReLU(inplace=True),
            nn.Linear(128, 2),
        )

    def forward(self, faces, eyes_left, eyes_right, face_grids):
        x_eye_l = self.eyeModel(eyes_left)
        x_eye_r = self.eyeModel(eyes_right)
        x_eyes = self.eyesFC(torch.cat((x_eye_l, x_eye_r), 1))
        x_face = self.faceModel(faces)
        x_grid = self.gridModel(face_grids)
        return self.fc(torch.cat((x_eyes, x_face, x_grid), 1))


class ITrackerWithMeans(nn.Module):
    """Export wrapper: folds the three mean-image subtractions into the graph
    so the mobile side feeds raw 0..1 RGB and cannot get the preprocessing
    wrong. `mean_*` are (3, 224, 224) float tensors already divided by 255."""

    def __init__(self, core: ITrackerModel, mean_face, mean_left, mean_right):
        super().__init__()
        self.core = core
        self.register_buffer("mean_face", mean_face.unsqueeze(0))
        self.register_buffer("mean_left", mean_left.unsqueeze(0))
        self.register_buffer("mean_right", mean_right.unsqueeze(0))

    def forward(self, faces, eyes_left, eyes_right, face_grids):
        return self.core(
            faces - self.mean_face,
            eyes_left - self.mean_left,
            eyes_right - self.mean_right,
            face_grids,
        )
