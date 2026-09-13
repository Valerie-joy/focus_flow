# Third-party notices

FocusFlow ships two on-device models and depends on the open-source libraries
resolved by Gradle. The two models carry obligations that travel with the app
and with anything written about it, so they are recorded here rather than
only in code comments.

## iTracker / GazeCapture — Research License (NOT open source)

`app/src/main/assets/itracker.tflite` is the iTracker convolutional network
from *Eye Tracking for Everyone*, exported from the GazeCapture PyTorch
checkpoint by `tools/itracker/`. It is a **derivative work of the GazeCapture
Licensed Material** and is governed by the *License Agreement for Use of
GazeCapture Database and iTracker Models* ("Research License"), reproduced in
`GazeCapture-master/GazeCapture-master/LICENSE.md` when the upstream release is
present locally.

The obligations that matter, quoted from the licence:

- **Research use only.** "The Licensed Material will only be used for research
  purposes and will not be used nor included in commercial applications in any
  form (such as original files, encrypted files, files containing extracted
  features, models trained on dataset, other derivative works, etc)."
  → A build containing `itracker.tflite` cannot be sold, monetised, or
  published to an app store as a product.
- **No distribution beyond backup.** "The Licensed Material will not be copied
  nor distributed in any form other than for Your backup," and sharing is
  limited to "direct research colleagues who belong to the same research
  institution as You and have adhered to the terms of this license."
  → The model may be shared with the project team at Saint Columban College.
  It must not be hosted in a **public** repository or offered for download.
- **Mandatory citation.** "Any work made public, whatever the form, based
  directly or indirectly on any part of the Licensed Material must include the
  following reference." This covers the PID, the manuscript, the portfolio and
  any presentation, not only source code:

  > Kyle Krafka, Aditya Khosla, Petr Kellnhofer, Harini Kannan, Suchi
  > Bhandarkar, Wojciech Matusik and Antonio Torralba. "Eye Tracking for
  > Everyone". IEEE Conference on Computer Vision and Pattern Recognition
  > (CVPR), 2016.

- **Termination is automatic on breach** (§4a), reinstated only if cured
  within 30 days of discovery.

Copyright (c) 2017 Kyle Krafka, Aditya Khosla, Petr Kellnhofer, Harini Kannan,
Suchendra Bhandarkar, Wojciech Matusik, and Antonio Torralba.

The GazeCapture dataset itself is **not** part of this repository and is never
shipped in the app; only the exported model is.

## MediaPipe Face Landmarker — Apache License 2.0

`app/src/main/assets/face_landmarker.task` is Google's MediaPipe Face
Landmarker bundle (face detection, 478-point mesh with iris refinement, and
blendshape models), used via `com.google.mediapipe:tasks-vision`. Licensed
under the Apache License, Version 2.0:
<https://www.apache.org/licenses/LICENSE-2.0>. Copyright Google LLC.

## Libraries

All other dependencies are declared in `app/build.gradle.kts` and resolved from
Maven Central and Google's Maven repository under their respective licences
(Apache 2.0 unless stated otherwise by the library): AndroidX, Jetpack
Compose, CameraX, Room, LiteRT, supabase-kt, Ktor, kotlinx.serialization, and
android-youtube-player.
