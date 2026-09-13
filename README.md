# FocusFlow — Stages 1-10: Complete (Design System through Profile)

This package contains the full spec, all ten phases: Auth, ADHD
verification, user registration, camera calibration, adaptive attention
assessment, AI analysis, personalized recommendations, Dashboard, Results
export, and Profile. Every screen from the master prompt is built and
wired into a working navigation graph, and every quick-access button on
Dashboard now goes somewhere real — no `TODO` no-ops left in the main path.

## What's included

```
app/src/main/java/com/focusflow/
  reports/ (new in Stage 9)
    PdfReportGenerator.kt — builds the assessment report PDF using android.graphics.pdf.PdfDocument, the exact API the spec names
  services/ (new in Stage 9)
    EmailReportService.kt    — shares the PDF via an ACTION_SEND email intent ("JavaMail Intent" per spec, interpreted as hand-off to the user's mail app, not embedded SMTP)
    ReportDownloadService.kt — saves the PDF to the public Downloads collection via MediaStore (scoped-storage compliant)
  data/local/
    AssessmentHistoryEntity.kt — Room entity: one category's result from a completed session
    AssessmentDao.kt           — Room DAO: insert + observe history as a Flow
    FocusFlowDatabase.kt       — Room database singleton (manual, stand-in for Hilt @Singleton)
    UserPreferences.kt         — tiny SharedPreferences wrapper for the display name (Dashboard greeting)
  data/repository/ (new in Stage 8)
    AssessmentRepository.kt — wraps the DAO; saveSession() + observeHistory()
  ai/attention/
    AttentionTracker.kt — accumulates per-frame face detection into GazeMetrics + sustained-drop detection
  camera/eyetracking/
    GazeAnalyzer.kt — CameraX ImageAnalysis.Analyzer wrapping MediaPipe Face Landmarker (iris + blendshape gaze, head pose), optionally running iTracker every Nth frame
    CalibrationGate.kt — stable face/iris acquisition gate for the camera setup step
    itracker/ — ITrackerGeometry (crop/grid conventions), ITrackerPreprocessor, GazePointEstimator (LiteRT), GazeCalibration (affine fit), GazeFusion
  domain/models/
    AdhdSelfReportQuestion.kt — question bank + FrequencyAnswer enum for the self-reflection questionnaire
    AttentionCategory.kt — the 9 shuffled content categories
    GazeMetrics.kt — GazeMetrics + CategoryAssessmentResult
    LearningProfile.kt — AttentionTrait/TraitCluster taxonomy + category-to-trait mapping + LearningStyle enum
  domain/usecases/
    CalculateAttentionScoreUseCase.kt — the spec's "AI Logic" scoring engine: combines gaze duration, screen attention %, distraction delay, blink consistency, and self-report ratings into one 0-100 score per category
    GenerateRecommendationsUseCase.kt — Phase 7's core: infers learning style + study techniques + content types + weekly goals from scored results
  viewmodel/
    AssessmentViewModel.kt — owns the full shuffle/retry/success-count decision loop from the spec
    DashboardViewModel.kt — derives weekly progress, a time-of-day daily insight, and grouped session history from persisted data
    ResultsViewModel.kt — derives attention ranking, an AI insight, and progress-over-time from persisted history for Phase 9 (new in Stage 9)
  ui/theme/
    Color.kt        — full light/dark palette from the spec
    Type.kt          — editorial serif headings + Inter/Manrope body (see note below)
    Shape.kt         — corner radius tokens (pill buttons, rounded cards)
    Theme.kt         — FocusFlowTheme() + LocalFocusFlowColors extra tokens
  ui/components/
    GlassCard.kt          — frosted glass surface (real blur on API 31+)
    BlurBackground.kt     — animated ambient gradient w/ drifting glow blobs
    PrimaryButton.kt      — solid pill button, press-scale animation
    GradientButton.kt     — hero gradient pill button w/ tinted glow shadow
    FloatingBottomBar.kt  — floating pill nav bar (Home/Assessment/Results/Profile)
    ProgressRing.kt       — circular animated progress (e.g. weekly focus score)
    AnimatedGauge.kt      — semicircle gauge w/ needle (attention strength score)
    CustomSlider.kt       — 1–5 rating dots (post-video questions)
    PremiumDialog.kt      — glass modal (errors, confirmations)
    AIInsightCard.kt      — gradient-icon insight/recommendation card
    PremiumTextField.kt   — glass input field w/ floating label, password toggle, error state
    SelectableOptionCard.kt — large tappable glass card w/ selected state
    NumberStepper.kt        — glass pill +/- stepper for bounded ints, e.g. age
    SegmentedToggle.kt      — pill segmented control for small option sets, e.g. sex
    VideoPlayerCard.kt      — video surface w/ category/timer chips + play/pause overlay (placeholder media)
    RankingListItem.kt      — rank + label + animated horizontal bar + percentage
    RadarChart.kt           — N-axis radar/spider chart for comparing categories at once
    ProgressLineChart.kt    — simple animated line chart for score-over-time trends
    SettingsRow.kt          — icon + label row w/ trailing slot (chevron or Switch) for menu/settings lists (new in Stage 10)
  ui/screens/
    DesignSystemPreviewScreen.kt — renders every component together, light + dark @Preview
    auth/
      SplashScreen.kt    — animated gradient + floating particles + logo, auto-advances
      WelcomeScreen.kt   — editorial title, Log In / Create Account / social buttons
      LoginScreen.kt     — email + password, forgot password link
      RegisterScreen.kt  — full name, email, password, confirm password (with live mismatch check)
    onboarding/
      AdhdQuestionScreen.kt       — "Do you have an ADHD diagnosis?" branch
      AdhdUploadScreen.kt         — document upload w/ validation states + error dialog
      AdhdAssessmentScreen.kt     — disclaimer step + one-question-per-screen questionnaire
      UserRegistrationScreen.kt   — full name, age (5-30 stepper), sex (segmented toggle), email
      CameraPermissionScreen.kt   — privacy explanation + CAMERA permission request
      CameraCalibrationScreen.kt  — live CameraX preview + acquisition gate + five-dot gaze calibration
    assessment/
      AttentionAssessmentScreen.kt — plays a video while a hidden (no-preview) camera analysis stream feeds AttentionTracker; stops early on sustained attention drop
      AttentionShiftedScreen.kt    — "We noticed your attention shifted" interruption screen
      PostVideoRatingScreen.kt     — "How was that?" 1-5 ratings + successful-assessments footer
    results/
      AttentionResultsScreen.kt — Phase 6: "Here's your attention profile" for the just-completed session, w/ Strongest/Weakest toggle + ranked list
      FullAnalysisScreen.kt     — Phase 6: progress rings (top 3) + radar chart (all categories) + full ranking for the just-completed session
      RecommendationsScreen.kt  — Phase 7: "Your mind has a unique rhythm" — learning style, best categories, attention strength score, study techniques, content types, weekly goals
      ResultsHistoryScreen.kt   — Phase 9: attention ranking + AI insight + radar chart + progress-history line chart from persisted data, PDF download + email export (new in Stage 9)
    dashboard/
      DashboardScreen.kt — greeting, today's focus card, weekly progress ring, daily insight, recent recommendation, assessment history, quick access, floating bottom nav
    profile/ (new in Stage 10)
      ProfileScreen.kt              — avatar, name/email, menu list, dark mode toggle, logout (w/ confirmation)
      PersonalInformationScreen.kt  — read-only view of name/age/sex/email
      NotificationSettingsScreen.kt — push/daily reminder/weekly summary toggles
      PrivacySettingsScreen.kt      — camera/eye-tracking privacy explanation + anonymized analytics toggle
      HelpSupportScreen.kt          — FAQ cards + contact support (mailto intent)
  ui/navigation/
    FocusFlowDestinations.kt — central route constants for the whole app
    AuthNavGraph.kt           — wires Splash/Welcome/Login/Register
    OnboardingNavGraph.kt     — wires the ADHD question/upload/assessment screens
    RegistrationNavGraph.kt   — wires registration + camera permission/calibration + Dashboard + Results history/export
    AssessmentNavGraph.kt     — nested graph wiring Phases 5, 6 and 7's screens to a shared AssessmentViewModel; persists the session to Room on completion
    ProfileNavGraph.kt        — wires the Profile hub + all 5 sub-screens, including a standalone "AI Recommendations" reuse of Phase 7's screen (new in Stage 10)
```

## Setup

1. Copy the `app/src/main/java/com/focusflow` folder into your Android Studio
   project at the same path (merge if `com.focusflow` already exists).
2. Add these dependencies if not already present (Compose BOM will pin most
   versions — use whatever BOM you're already on):
   ```
   implementation("androidx.compose.material3:material3")
   implementation("androidx.compose.material:material-icons-extended")
   implementation("androidx.navigation:navigation-compose:2.8.0")
   implementation("androidx.activity:activity-compose:1.9.0") // file picker + permission launcher

   // CameraX (Stage 4)
   val cameraxVersion = "1.3.4"
   implementation("androidx.camera:camera-core:$cameraxVersion")
   implementation("androidx.camera:camera-camera2:$cameraxVersion")
   implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
   implementation("androidx.camera:camera-view:$cameraxVersion")

   // MediaPipe Face Landmarker (Stage 4-5) + LiteRT for iTracker
   implementation("com.google.mediapipe:tasks-vision:1.0.0")
   implementation("com.google.ai.edge.litert:litert:1.4.2")

   // ViewModel + StateFlow-in-Compose support (Stage 5)
   implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
   implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

   // Room persistence (Stage 8)
   implementation("androidx.room:room-runtime:2.6.1")
   implementation("androidx.room:room-ktx:2.6.1")
   ksp("androidx.room:room-compiler:2.6.1") // see plugin note below

   // YouTube video playback (Stage 11) — Google's own "YouTube Android
   // Player API" is deprecated; this is the maintained community
   // replacement, a WebView wrapper around YouTube's IFrame Player API
   implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0")
   ```

   Room's annotation processor needs KSP. Add the plugin to your **module-level**
   `build.gradle.kts`:
   ```kotlin
   plugins {
       id("com.google.devtools.ksp") version "2.0.0-1.0.24" // match your Kotlin version
   }
   ```
   (kapt works too if your project is already set up for it — swap `ksp(...)` for `kapt(...)`.)
3. Add the camera permission declaration to `AndroidManifest.xml`:
   ```xml
   <uses-permission android:name="android.permission.CAMERA" />
   <uses-feature android:name="android.hardware.camera.front" android:required="false" />

   <!-- Stage 11: YouTube video playback -->
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

   <!-- Stage 9: only needed for the legacy (API 28 and below) Downloads
        save path — MediaStore on API 29+ needs no permission at all -->
   <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
       android:maxSdkVersion="28" />
   ```

   And inside `<application>`, the FileProvider that lets the generated PDF
   be attached to an email intent (path config already included at
   `res/xml/file_paths.xml`):
   ```xml
   <provider
       android:name="androidx.core.content.FileProvider"
       android:authorities="${applicationId}.fileprovider"
       android:exported="false"
       android:grantUriPermissions="true">
       <meta-data
           android:name="android.support.FILE_PROVIDER_PATHS"
           android:resource="@xml/file_paths" />
   </provider>
   ```
4. **Fonts**: the type system falls back to `FontFamily.Serif` /
   `FontFamily.SansSerif` out of the box so it compiles immediately. For the
   real editorial look, download these (all free, open-licensed) from
   Google Fonts and drop the `.ttf` files into `res/font/`:
   - Instrument Serif → `instrument_serif_regular.ttf`
   - Cormorant Garamond (Medium) → `cormorant_garamond_medium.ttf`
   - Inter (Regular + Medium) → `inter_regular.ttf`, `inter_medium.ttf`
   - Manrope (SemiBold) → `manrope_semibold.ttf`

   Then flip `hasCustomFonts = true` at the top of `Type.kt`.
5. Wrap your app's root composable in `FocusFlowTheme { ... }`.
6. Wire all five graphs into your root NavHost:
   ```kotlin
   NavHost(navController, startDestination = FocusFlowDestinations.SPLASH) {
       authGraph(navController)
       onboardingGraph(navController)
       registrationGraph(navController)
       assessmentGraph(navController)
       profileGraph(navController)
   }
   ```
7. Drop `DesignSystemPreviewScreen()` into any Activity/Composable to see
   every component rendered, or open any screen file in Android Studio's
   Split view and use the `@Preview` annotations directly.

## Design notes

- **Glass blur** is real (native `Modifier.blur`) on Android 12+ (API 31).
  Below that it gracefully degrades to a flat translucent fill — still
  reads as "glass" against the gradient backgrounds, just without the
  frosted blur. If you need blur support on older API levels, the
  `haze` library (github.com/chrisbanes/haze) is the standard drop-in.
- All components pull colors from `MaterialTheme.colorScheme` and
  `LocalFocusFlowColors.current` rather than hardcoding — dark mode is
  "free" everywhere these are used.
- Auth and onboarding screens are intentionally dumb/presentational — they
  take callbacks and (for upload/assessment) explicit state, rather than
  calling a ViewModel directly. `AuthNavGraph.kt` and `OnboardingNavGraph.kt`
  have `// TODO` markers exactly where real business logic plugs in.
- Google/Apple sign-in buttons are styled but not functionally wired —
  hook `onContinueWithGoogle` / `onContinueWithApple` in `AuthNavGraph.kt`
  up to your actual auth SDKs when you get to that integration.
- `RegisterScreen` does live password-confirmation matching client-side;
  it doesn't validate password strength or email format yet — add that in
  the ViewModel layer alongside the real submit call.
- **Document validation is stubbed** (`fakeValidateDocument` in
  `OnboardingNavGraph.kt`) — it just checks the file extension so the flow
  is fully navigable. Swap in real validation (parse the file, confirm it
  reads as a diagnosis letter, maybe route to human review) behind the same
  `UploadState` contract the screen already expects.
- **`GazeAnalyzer` produces two gaze signals.** MediaPipe Face Landmarker
  (478 landmarks incl. 10 iris points, 52 blendshapes, head pose) runs every
  frame and gives gaze *deflection* — reliable for "eyes are off centre",
  blind to *where* they point. Optionally, iTracker runs on every Nth frame
  and gives a calibrated *point of regard* — see "Gaze-point estimation"
  below. `GazeFusion` combines them into the single `isLookingAtScreen`
  decision `AttentionTracker` consumes.
- **Camera frames are never persisted** — `ImageAnalysis` frames are
  converted in memory, handed to on-device MediaPipe (and iTracker) and
  released; nothing is written to disk, matching the privacy copy shown on
  `CameraPermissionScreen`.
- `RegistrationNavGraph.kt` includes a **temporary placeholder** for the
  `DASHBOARD` route so the whole onboarding flow (Splash → … → Calibration
  → Assessment) is testable end to end before Phase 8 exists. Delete that
  composable once the real dashboard is built.
- **Attention tracking is real, not simulated** — `AttentionTracker`
  consumes the per-frame `GazeFrame` (face presence, looking decision, blink)
  during video playback and computes screen-attention %, gaze shift count,
  first-distraction time, blink count, analysis frame rate, and — when
  calibrated — how many frames the gaze point decided, plus iTracker's mean
  inference time, so the two modes can be reported separately.
- **Video content itself is a placeholder** — `VideoPlayerCard` renders a
  themed gradient, not real per-category video. There's a `// TODO` in that
  file marking where an ExoPlayer/media3 `PlayerView` would go once real
  video assets exist. Whatever populates that library should be filtered by
  the user's registered age — Horror/Sad content isn't appropriate for the
  younger end of the app's 5-30 range (flagged in `AttentionCategory.kt`
  too).
- **I deliberately did not surface live metrics during playback** —
  `AttentionAssessmentScreen` only shows a subtle "keep your eyes on the
  screen" line, per the master spec's explicit "do not display these
  metrics during playback" instruction. Note this differs from the
  reference screenshot, which shows an Attention%/Gaze shifts/First shift
  stat row during playback — I went with the spec's written behavior over
  the mockup's literal layout on this one screen. Easy to add back (the
  numbers already exist in `AttentionTracker`) if you'd rather match the
  image exactly.
- **The 5-successful-assessments loop is a real state machine**
  (`AssessmentViewModel`), not just navigation — it shuffles categories
  once per session, marks failed categories as low-attention without
  incrementing the counter, and rotates in a fresh category each retry. It
  also handles the edge case the spec doesn't cover (running out of the 9
  categories before reaching 5 successes) via an `EXHAUSTED` phase rather
  than crashing.
- **The scoring engine's weights are documented, not a black box** — see
  `CalculateAttentionScoreUseCase.kt` for exactly why screen-attention % is
  weighted heaviest and blink consistency lightest, and how weight is
  redistributed for unsuccessful categories that never got a rating (no
  ratings are collected after an attention-shift interruption). This is a
  reasonable starting point, not a validated formula — worth revisiting the
  weights once real usage data exists.
- **Low-attention (unsuccessful) categories are scored too**, not just
  successful ones — a category that triggered an early stop is exactly the
  "weakest attention" signal the results screen needs, so it's included in
  `AttentionResultsScreen`/`FullAnalysisScreen` even though it never got
  interest/focus ratings.
- **Learning style is inferred, not hard-mapped** — `GenerateRecommendationsUseCase`
  tallies trait clusters (auditory/visual/interactive/narrative/textual)
  across a person's top-performing categories, weighted by score, rather
  than assuming e.g. "liked Music" always means "auditory learner." Three
  or more roughly-equal clusters produces "Multimodal Learner"; a single
  dominant cluster produces a more specific style. See `LearningProfile.kt`
  for the full trait taxonomy.
- The full flow (`Splash → … → Registration → Calibration → Assessment →
  Results → Full Analysis → Recommendations → Dashboard`) is now wired end
  to end with **no placeholders left in the main path** — this is the
  first stage where the whole spec'd sequence runs for real, start to
  finish, including data actually persisting between the assessment and
  the dashboard.
- **Session data now survives the assessment graph being popped** —
  `AssessmentViewModel` is scoped to `assessment_graph`'s back stack entry
  and dies when that graph is popped on the way to `DASHBOARD`. Rather than
  fight that lifetime, `RecommendationsScreen`'s "Continue" persists the
  scored session to Room first (`AssessmentRepository.saveSession`), and
  `DashboardViewModel` reads it back out independently — the same pattern
  a real backend-synced app would use, just with Room standing in for the
  backend.
- **The daily insight is a real computed value, not a canned string** —
  `DashboardViewModel.deriveDailyInsight` buckets historical entries by
  time-of-day and reports whichever bucket has the highest average score,
  requiring at least 2 data points in a bucket before trusting it. It
  returns null (and the UI hides the card) until there's enough history to
  say anything meaningful — worth knowing if you test with only 1-2
  sessions and don't see the insight card yet.
- **`FocusFlowDatabase.getInstance()` and the ad-hoc `viewModelFactory`
  calls are manual stand-ins for Hilt**, which the spec calls for but isn't
  wired app-wide in this scaffold. Every place doing this (`RegistrationNavGraph`,
  `AssessmentNavGraph`) is marked so they're easy to find and swap for
  `@Inject`/`@HiltViewModel` in one pass whenever Hilt gets set up — the
  DAO/Repository/ViewModel API surfaces don't need to change.
- Dashboard's Results and Profile quick-access buttons now both navigate
  to real screens — `ResultsHistoryScreen` (Stage 9) and `ProfileScreen`
  (Stage 10). No `TODO` no-ops remain in the main navigation path.
- **Phase 6 vs Phase 9 results, and why both exist**: `AttentionResultsScreen`/
  `FullAnalysisScreen` (Stage 6) only ever show the session that *just*
  finished, read from the short-lived `AssessmentViewModel`. `ResultsHistoryScreen`
  (Stage 9) reads persisted history via `ResultsViewModel`/`AssessmentRepository`
  instead, so it's reachable any time from Dashboard — including after the
  app's been closed and reopened — and it's what the PDF/email export pulls
  from. They're deliberately separate screens rather than one screen serving
  two purposes.
- **PDF generation uses `android.graphics.pdf.PdfDocument` directly** — the
  exact API the spec names, not a third-party library. `PdfReportGenerator`
  paginates automatically if content overflows a page.
- **Email export is an `ACTION_SEND` intent, not embedded SMTP** — the
  spec's "JavaMail Intent" is interpreted as handing the PDF to whichever
  mail app the user already has, via `FileProvider`. This never touches
  email credentials and needs the manifest `<provider>` entry above to work.
- **Download uses MediaStore on API 29+** (no storage permission needed)
  and falls back to the legacy direct-file-path approach below that (which
  does need `WRITE_EXTERNAL_STORAGE`, already scoped to `maxSdkVersion="28"`
  in the manifest snippet above).
- **Profile's "AI Recommendations" reuses Phase 7's `RecommendationsScreen`
  through a second overload** that takes already-scored entries directly
  (`CategoryAttentionScore`), rather than re-deriving scores from
  `CalculateAttentionScoreUseCase`. Raw gaze metrics aren't persisted (only
  the final score), so re-scoring from scratch would drift from the number
  the user already saw in Results — this way the exact stored score flows
  through unchanged. See the comment in `ProfileNavGraph.kt`.
- **Dark mode toggle persists a preference but doesn't yet control the
  theme app-wide** — `UserPreferences.getDarkModeOverride()` is saved
  correctly from Profile, but actually applying it means reading that value
  wherever `FocusFlowTheme { ... }` wraps the app root, which isn't part of
  this component/screen scaffold (there's no single root Activity file
  here to wire it into). This is a one-line change once you have that root
  composable: `FocusFlowTheme(darkTheme = UserPreferences(context).getDarkModeOverride() ?: isSystemInDarkTheme())`.
- **Logout clears everything via `UserPreferences.clearAll()`** and resets
  the back stack to `WELCOME` (`popUpTo(0)`) — a genuine fresh start, not
  just a navigation pop.
- Contact-support email uses a placeholder address
  (`support@focusflow.app`) in `EmailReportService.contactSupport()` —
  swap that for your real support inbox.

## Gaze-point estimation (iTracker)

Since 2026-09 the app can estimate **where on the screen** the user is
looking, not only whether their eyes are deflected. This closes the
"no calibrated point-of-regard" limitation stated in the PID and manuscript.

**This is a post-PID enhancement, and the approved PID does not yet reflect
it.** The PID as approved places calibrated point-of-regard *explicitly out
of scope* (§1.7: "the system is not calibrated per user and cannot determine
where on the screen the user is looking"), restates that under Constraints,
and defines *Gaze Deviation* (§3.1) in deflection-only terms. Those three
passages now describe a system this build no longer is. Until the PID is
revised, present the iTracker path as an enhancement added after PID
approval rather than as part of the approved scope, and add the citation
below to the PID and manuscript — the licence requires it in "any work made
public, whatever the form". Full obligations: `THIRD_PARTY_NOTICES.md`.

The deflection-only pipeline the PID *does* describe remains intact and is
the automatic fallback whenever the model is absent, calibration is skipped,
or the calibration fit is out of tolerance — so the approved behaviour is
still exactly what the app does in those cases.

**Model.** iTracker from *Eye Tracking for Everyone* (Krafka et al., CVPR
2016), the released GazeCapture PyTorch checkpoint exported to
`app/src/main/assets/itracker.tflite` (13.5 MB, float16 weights) by
`tools/itracker/` — every export stage is numerically verified, see that
directory's README. It takes a 224² face crop, two 224² eye crops and a 25×25
face-position grid, all synthesised from MediaPipe's landmarks by
`ITrackerPreprocessor`, and returns a gaze point in cm from the camera.

**Calibration.** iTracker was trained on iPhones/iPads; Android cameras sit at
unknown offsets and the checkpoint is a ~2.5 cm instrument uncalibrated. So
`CameraCalibrationScreen` gains a five-dot pass after the acquisition gate:
the user looks at each dot, per-dot medians of the model's output are fitted
to the dot positions with a least-squares affine map (`GazeCalibration`), and
a fit within tolerance is saved per user in `UserPreferences`. The map
absorbs camera offset, screen scale and axis sign in six parameters — no
device database. Every exit is graceful: no model, a poor fit, or "skip"
leaves the app in the blendshape-only mode it had before.

**Runtime.** LiteRT 1.4.2, CPU/XNNPACK, four threads. iTracker runs
synchronously on the landmarker's result thread (so frames stay in order for
`AttentionTracker`'s sustain timing) and self-throttles: it measures its own
wall time and skips enough frames to stay under ~25 ms amortised per frame.
`GazeMetrics.gazeMode` records which signal decided each clip.

**Licence — read before distributing.** The GazeCapture dataset, models and
code are under a *Research License*: research use only, **no commercial use**
in any form (including derived models), and any publication must cite the
paper. That is fine for this student project and the STA manuscript; the app
cannot be published commercially while it ships this model.

> Krafka, K., Khosla, A., Kellnhofer, P., Kannan, H., Bhandarkar, S.,
> Matusik, W., & Torralba, A. (2016). *Eye Tracking for Everyone.* IEEE
> Conference on Computer Vision and Pattern Recognition (CVPR).

## Stage 11: Real YouTube video playback

`AttentionAssessmentScreen` now embeds actual YouTube videos instead of a
themed gradient placeholder:

- **`YouTubePlayerCard`** (`ui/components/`) wraps `YouTubePlayerView` from
  the [android-youtube-player](https://github.com/PierfrancescoSoffritti/android-youtube-player)
  library — a WebView-based wrapper around YouTube's IFrame Player API.
  Google's own "YouTube Android Player API" is deprecated, so this is the
  maintained community-standard replacement (5,000+ apps use it, including
  some well-known ones).
- **`CategoryVideoLibrary.kt`** (`domain/models/`) maps each `AttentionCategory`
  to a list of video IDs. **The IDs shipped here are placeholders**
  (`"REPLACE_ME_MUSIC_1"` etc.) — deliberately obvious fakes rather than
  guessed-at real-looking IDs, so it's unmistakable what still needs
  populating. The file's doc comment walks through finding a real ID,
  confirming it allows embedding, and why "watch the whole video yourself
  first" isn't optional busywork — this puts a specific video in front of
  a specific person's eyes for up to a minute.
- **Graceful fallback**: if a category's list is still placeholder IDs, or
  the real player errors (no network, embedding disabled, video pulled),
  `AttentionAssessmentScreen` automatically falls back to the original
  `VideoPlayerCard` gradient placeholder rather than showing a broken
  player — the assessment loop still completes end to end either way.
- **Completion signal changed**: previously a hardcoded timer was the only
  way a video "finished." Now, with a real video, YouTube's own `ENDED`
  player state fires completion — the timer becomes a safety cap (in case
  a looping video never reports `ENDED`) rather than the primary signal.

### Content curation is still entirely manual, and that's intentional

`CategoryVideoLibrary.kt`'s doc comment repeats the concern already flagged
in `AttentionCategory.kt`: Horror, Sad, Melodrama, and Romance need real
editorial judgment given the app's stated 5-30 age range. This integration
deliberately does **not** pull from YouTube's search/recommendation API —
every video that plays here should be one a human specifically chose and
watched, not one an algorithm surfaced. Consider filtering which categories
are even offered based on `UserPreferences.getAge()` before wiring this up
for real users.

### Setup addition

```kotlin
implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0")
```
Plus `INTERNET` and `ACCESS_NETWORK_STATE` permissions (already added to
`AndroidManifest.xml` in this zip).

## Spec coverage

All ten phases from the original master prompt are built:

| Phase | Status |
|---|---|
| 1. Auth | ✅ Splash, Welcome, Login, Register |
| 2. ADHD verification | ✅ Question, document upload, self-reflection questionnaire (reframed — see below) |
| 3. User registration | ✅ Name, age, sex, email |
| 4. Camera calibration | ✅ Real CameraX + MediaPipe acquisition gate + iTracker gaze calibration |
| 5. Adaptive attention assessment | ✅ Real gaze tracking, shuffle/retry/success-count loop |
| 6. AI analysis | ✅ Real scoring engine, radar chart, progress rings, ranking |
| 7. Personalized recommendations | ✅ Learning style inference, study techniques, content types, weekly goals |
| 8. Dashboard | ✅ Backed by real Room persistence |
| 9. Results | ✅ Persisted history + PDF/email export |
| 10. Profile | ✅ All fields, settings, logout |

Everything not called out as a placeholder above (video content, document
validation, gaze-point precision, Hilt DI, dark-mode app-wide wiring) is a
genuine limitation worth knowing about before shipping, not a shortcut
hidden from you — each one is flagged inline in the relevant file and
summarized in these design notes.

## A note on the ADHD-assessment framing

The full spec's Phase 2 offers an in-app "professional ADHD assessment" as
an alternative to uploading a diagnosis, and Phase 5–7 turn gaze-tracking
during entertainment videos into an "attention profile." Worth deciding
deliberately before building those screens: gaze duration + self-reported
interest on video clips isn't a validated clinical assessment, and the app
targets users as young as 5. I'd recommend framing this feature as a
self-reflection / study-habits tool ("see what content keeps your focus")
rather than anything that reads as diagnosing or ruling out ADHD, with
plain-language copy saying so on the relevant screens — which is exactly
how it's implemented: `AdhdAssessmentScreen` opens on an explicit
disclaimer, and `HelpSupportScreen`'s FAQ repeats it in plain language.

