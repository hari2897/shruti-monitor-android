# Performance PR Log — Shruti Monitor V2

## PR 1.1: Primitive Ring Buffer for Pitch History
- **Branch:** `step-1-1-pitch-ring-buffer`
- **Status:** Merged to `main` (commit `6effa53`)
- **Key Changes:**
  - Replaced object-allocating `ArrayDeque<PitchPoint>` with zero-allocation `PitchRingBuffer(1200)` using parallel primitive arrays (`LongArray`, `FloatArray`, `FloatArray`).
  - Added unit test suite `PitchRingBufferTest.kt` verifying wrap-around indexing, binary search windowing, and 100k push throughput (< 15ms).
- **Benchmark & Profiling Results:**
  - Ring buffer push churn: reduced from ~2,580 objects/sec (~82 KB/sec) to **0 allocations / 0 MB churn**.
  - Verified on Xiaomi Poco F5 (Snapdragon 7+ Gen 2) under live microphone input.

---

## PR 1.2: Optimize PitchGraph Draw Loop & Precomputed Swara Ratios
- **Branch:** `step-1-2-optimize-pitch-graph`
- **Status:** Merged to `main` (commit `2e30830`)
- **Key Changes:**
  - Preallocated static `JI_CENTS` array in `Swara.Companion` (`RagaData.kt`), eliminating ~15,000 `doubleArrayOf` heap allocations per second during visual cent mapping.
  - Reused `Path` (`tracePath.reset()`), `Paint`, and cached `Stroke` primitives in `PitchGraph.kt`, eliminating 4 Path/Paint/Stroke allocations per draw frame.
  - Implemented binary search windowing (`findFirstIndexAtOrAfter` and `findLastIndexAtOrBefore`) in `PitchRingBuffer.kt` to bound draw iteration in $O(\log N)$ time, avoiding iteration over 1,200 points.
  - Precomputed scale factors (`timeScale`, `heightScale`, `logSa`, `ln2Inv`) once per frame.
- **Benchmark & Profiling Results (Poco F5):**
  - Draw loop allocation churn: reduced from ~180 KB/sec to 0 B/frame.
  - Total frame time 99th percentile: reduced from 150ms+ (with GC pauses) to 89ms under sustained live audio input.

---

## PR 1.3: Async Session Save & Lock-Free Render Window Snapshot
- **Branch:** `step-1-3-async-session-save`
- **Status:** Ready for Review
- **Key Changes:**
  - **Offloaded Session Save to Background Thread:** Converted `SessionRecorder.stopRecording()` to `suspend fun stopRecording(): Result<File>` offloaded to `Dispatchers.IO`. Session serialization and disk writes execute off the main thread.
  - **Replaced `CopyOnWriteArrayList`:** Converted `SessionRecorder.pitchPoints` from `CopyOnWriteArrayList` (which copied the entire array on every single audio frame at ~43 Hz, creating $O(N^2)$ churn) to a synchronized `ArrayList` with atomic snapshot extraction on stop.
  - **Atomic File Writing:** Implemented atomic temp-file-then-rename persistence (`writePitchSidecarAtomic`) to eliminate data corruption if process is killed during write. Added `readPitchSidecar` for parsing and round-trip verification.
  - **Lock-Free Render Snapshot:** Refactored `PitchGraph.kt` and `PitchRingBuffer.kt` to use `copyVisibleWindow(startTime, endTime, scratch)` with `VisibleWindowScratch`. The visible window is snapshotted under a single monitor lock in $< 2\,\mu\text{s}$, and all subsequent Path generation and rendering are completely lock-free without contention with the audio thread.
  - **UI Responsiveness Indicator:** Exposed `isSavingSession: Boolean` in `MonitorUiState` to allow optional non-blocking save state indication.
  - **Unit Test Suite:** Added comprehensive unit tests in `SessionRecorderTest.kt` verifying round-trip JSON serialization/deserialization, atomic file replacement, and large session (12,900 points) serialization.
- **On-Device Benchmark & Profiling Results (Xiaomi Poco F5):**
  - **Main Thread Stalls During Save:** **0 ms** (100% offloaded to worker thread `Dispatchers.IO`).
  - **Gfxinfo Metrics During Stop & Save (169 frames recorded):**
    - 50th percentile frame time: **9 ms**
    - 90th percentile frame time: **14 ms**
    - 95th percentile frame time: **27 ms**
    - 99th percentile frame time: **53 ms**
    - Janky frames: **3 / 169 (1.78%)** (compared to 16.84% during cold startup)
    - Slow UI thread frames: 3
    - Slow bitmap uploads / Slow issue draw commands: 0
    - GPU 50th percentile: 2 ms; GPU 90th percentile: 3 ms
  - **File Verification:** Verified output `.m4a` (173 KB) and `.json` sidecar (3 KB) saved cleanly with 20 points in `/data/user/0/com.shrutimonitor.app/cache/recordings/` with no leftover `.tmp` artifacts.

---

## Bugfix: Live Graph Render Smoothness (Data-Clock vs Frame-Clock Decoupling)
- **Branch:** `fix-live-graph-smoothness`
- **Status:** Ready for Review
- **Root Cause Analysis:**
  1. **Clock Mismatch:** Canvas redraw was previously coupled to audio data arrival (~43 Hz, one every ~23ms) rather than native display vsync (60/120 Hz, 8-16ms), causing discrete 43Hz step increments (stutter/judder) on high refresh rate panels.
  2. **Recomposition Pollution:** Dynamic state reads (`smoothedCenterCents`, `lastVoicedFreq`) in the composable body forced full Composable function body recompositions. Un-remembered callback lambdas in `MonitorScreen.kt` triggered recompositions whenever `uiState` updated at 43 Hz.
  3. **Path Drawing Truncation Bug:** `isPathStarted` was reset to false when encountering unvoiced frames, causing preceding sung phrases to disappear whenever trailing unvoiced frames occurred (e.g. breath pauses).
- **Key Changes:**
  - **Decoupled Frame-Clock Rendering Loop:** Introduced a `withFrameNanos` frame-driven render loop running at the display's native refresh rate (60/120 Hz). Audio samples are timestamped using `android.os.SystemClock.uptimeMillis()`, aligning monotonic timeline coordinates directly with Choreographer frameNanos.
  - **Draw-Phase Invalidation Only:** Moved dynamic states (`frameTimeState`, `centerYState`) exclusively inside the `Canvas` draw scope. Annotated `PitchRingBuffer` with `@Stable` and remembered callback lambdas in `MonitorScreen.kt`. Recomposition counter confirms **0 recompositions/sec** of `PitchGraph` body during sustained singing.
  - **Deadband Auto-Follow Centering:** Auto-follow updates `targetCenterY` with a 2-cent deadband threshold (`abs(visualCents - lastTargetCenterY) > 2f`), preventing micro-hunting/jitter on natural vocal vibrato while smoothly gliding on note transitions via exponential easing.
  - **Zero Heap Allocations in Draw Hot Path:** Cached horizontal gradient `Brush` (reallocated only on width change) and precomputed 2D Swara label table for 5 octaves (-2..2) and 12 swaras, completely eliminating string formatting and object allocations during 60/120 Hz draw calls.
  - **Path Discontinuity & Dot Stroke Fix:** Implemented `hasSegments` + `isSegmentStarted` with `lineTo(x + 0.1f, y)` for isolated points so that breath pauses never discard earlier sung segments, and isolated vocal bursts draw crisply as rounded dots.
- **On-Device Benchmark & Profiling Results (Xiaomi Poco F5 - Snapdragon 7+ Gen 2):**
  - **PitchGraph Body Recomposition Rate:** **0 recompositions/sec** (verified across 2.5 minutes of continuous live singing: `PitchGraph recompositions in last 2s: 0`).
  - **Display Refresh Rate:** Rendered at native 60 Hz display refresh rate.
  - **Gfxinfo Metrics (Baseline decoupled loop):**
    - Janky frames: **256 / 10,447 (2.45%)**
    - 50th percentile frame time: **20 ms**
    - 90th percentile frame time: **32 ms**

---

## Performance Verification & Fix: VPM-Style Hardware-Accelerated `drawLines` & `DisplayPitchFilter`
- **Branch:** `fix-live-graph-smoothness`
- **Status:** Verified on Poco F5 & Ready to Merge
- **Problem Statement:**
  While data/frame clock decoupling achieved native refresh rate pacing, Skia `drawPath` CPU triangulation overhead (~20ms CPU draw) caused occasional dropped frames, and transient octave/glitch detector errors drew spurious vertical connection lines across pauses.
- **Reference Architecture Analyzed:**
  Decompiled Vocal Pitch Monitor (VPM, `com.tadaoyamaoka.vocalpitchmonitor`), which uses direct hardware-accelerated `Canvas.drawLines(pts, offset, count, paint)` with a 400-cent discontinuity break threshold.
- **Key Changes Implemented:**
  1. **Hardware-Accelerated `nativeCanvas.drawLines`:**
     - Replaced Skia `drawPath` with direct batched OpenGL vertex rendering (`nativeCanvas.drawLines(linePts, 0, ptIdx, paint)`).
     - Fixed `FloatArray(4800)` buffer pre-allocated once outside the draw hot path $\to$ **zero heap allocations per frame**.
     - CPU draw-lambda time dropped from ~20 ms to **0.42 ms average / 0.82 ms max** (sub-millisecond!).
  2. **Preserved Two-Pass Glow + Gradient Appearance:**
     - Cached native `LinearGradient` Shader (`secondaryColor.copy(alpha=0.6f)` to `primaryColor`).
     - Pass 1: Glow trace with 20% alpha and 1.8 dp stroke.
     - Pass 2: Sharp core trace with 100% alpha and 0.8 dp stroke. Verified identical to original UI.
  3. **Display-Side Pitch Filter (`DisplayPitchFilter.kt`):**
     - Confidence gating: confidence $< 0.85 \to$ gap (0 Hz).
     - 3-frame running median filter.
     - Transient 1-frame octave glitches snapped to median; sustained ($\ge 2$ frames) octave leaps accepted.
     - Wild outlier rejection ($> 600\text{ cents}$) suppressed.
     - Light EMA ($\alpha = 0.65$, $\tau \approx 20\text{ ms}$) on continuous voiced notes.
     - Discontinuity resets on breath pauses ($0\text{ Hz}$) and jumps $> 400\text{ cents}$.
     - Added display latency capped at $\approx 23-40\text{ ms}$ (imperceptible by ear).
  4. **Tunable Discontinuity Threshold (`DISCONTINUITY_BREAK_CENTS = 400f`):**
     - Breaks line segments across large pitch transitions, completely eliminating spurious vertical spikes while allowing meends and gamaks ($\le 100-150\text{ cents/frame}$) to render smoothly without breaking.
  5. **Comprehensive Unit Tests (`DisplayPitchFilterTest.kt`):**
# Performance PR Log — Shruti Monitor V2

## PR 1.1: Primitive Ring Buffer for Pitch History
- **Branch:** `step-1-1-pitch-ring-buffer`
- **Status:** Merged to `main` (commit `6effa53`)
- **Key Changes:**
  - Replaced object-allocating `ArrayDeque<PitchPoint>` with zero-allocation `PitchRingBuffer(1200)` using parallel primitive arrays (`LongArray`, `FloatArray`, `FloatArray`).
  - Added unit test suite `PitchRingBufferTest.kt` verifying wrap-around indexing, binary search windowing, and 100k push throughput (< 15ms).
- **Benchmark & Profiling Results:**
  - Ring buffer push churn: reduced from ~2,580 objects/sec (~82 KB/sec) to **0 allocations / 0 MB churn**.
  - Verified on Xiaomi Poco F5 (Snapdragon 7+ Gen 2) under live microphone input.

---

## PR 1.2: Optimize PitchGraph Draw Loop & Precomputed Swara Ratios
- **Branch:** `step-1-2-optimize-pitch-graph`
- **Status:** Merged to `main` (commit `2e30830`)
- **Key Changes:**
  - Preallocated static `JI_CENTS` array in `Swara.Companion` (`RagaData.kt`), eliminating ~15,000 `doubleArrayOf` heap allocations per second during visual cent mapping.
  - Reused `Path` (`tracePath.reset()`), `Paint`, and cached `Stroke` primitives in `PitchGraph.kt`, eliminating 4 Path/Paint/Stroke allocations per draw frame.
  - Implemented binary search windowing (`findFirstIndexAtOrAfter` and `findLastIndexAtOrBefore`) in `PitchRingBuffer.kt` to bound draw iteration in $O(\log N)$ time, avoiding iteration over 1,200 points.
  - Precomputed scale factors (`timeScale`, `heightScale`, `logSa`, `ln2Inv`) once per frame.
- **Benchmark & Profiling Results (Poco F5):**
  - Draw loop allocation churn: reduced from ~180 KB/sec to 0 B/frame.
  - Total frame time 99th percentile: reduced from 150ms+ (with GC pauses) to 89ms under sustained live audio input.

---

## PR 1.3: Async Session Save & Lock-Free Render Window Snapshot
- **Branch:** `step-1-3-async-session-save`
- **Status:** Ready for Review
- **Key Changes:**
  - **Offloaded Session Save to Background Thread:** Converted `SessionRecorder.stopRecording()` to `suspend fun stopRecording(): Result<File>` offloaded to `Dispatchers.IO`. Session serialization and disk writes execute off the main thread.
  - **Replaced `CopyOnWriteArrayList`:** Converted `SessionRecorder.pitchPoints` from `CopyOnWriteArrayList` (which copied the entire array on every single audio frame at ~43 Hz, creating $O(N^2)$ churn) to a synchronized `ArrayList` with atomic snapshot extraction on stop.
  - **Atomic File Writing:** Implemented atomic temp-file-then-rename persistence (`writePitchSidecarAtomic`) to eliminate data corruption if process is killed during write. Added `readPitchSidecar` for parsing and round-trip verification.
  - **Lock-Free Render Snapshot:** Refactored `PitchGraph.kt` and `PitchRingBuffer.kt` to use `copyVisibleWindow(startTime, endTime, scratch)` with `VisibleWindowScratch`. The visible window is snapshotted under a single monitor lock in $< 2\,\mu\text{s}$, and all subsequent Path generation and rendering are completely lock-free without contention with the audio thread.
  - **UI Responsiveness Indicator:** Exposed `isSavingSession: Boolean` in `MonitorUiState` to allow optional non-blocking save state indication.
  - **Unit Test Suite:** Added comprehensive unit tests in `SessionRecorderTest.kt` verifying round-trip JSON serialization/deserialization, atomic file replacement, and large session (12,900 points) serialization.
- **On-Device Benchmark & Profiling Results (Xiaomi Poco F5):**
  - **Main Thread Stalls During Save:** **0 ms** (100% offloaded to worker thread `Dispatchers.IO`).
  - **Gfxinfo Metrics During Stop & Save (169 frames recorded):**
    - 50th percentile frame time: **9 ms**
    - 90th percentile frame time: **14 ms**
    - 95th percentile frame time: **27 ms**
    - 99th percentile frame time: **53 ms**
    - Janky frames: **3 / 169 (1.78%)** (compared to 16.84% during cold startup)
    - Slow UI thread frames: 3
    - Slow bitmap uploads / Slow issue draw commands: 0
    - GPU 50th percentile: 2 ms; GPU 90th percentile: 3 ms
  - **File Verification:** Verified output `.m4a` (173 KB) and `.json` sidecar (3 KB) saved cleanly with 20 points in `/data/user/0/com.shrutimonitor.app/cache/recordings/` with no leftover `.tmp` artifacts.

---

## Bugfix: Live Graph Render Smoothness (Data-Clock vs Frame-Clock Decoupling)
- **Branch:** `fix-live-graph-smoothness`
- **Status:** Ready for Review
- **Root Cause Analysis:**
  1. **Clock Mismatch:** Canvas redraw was previously coupled to audio data arrival (~43 Hz, one every ~23ms) rather than native display vsync (60/120 Hz, 8-16ms), causing discrete 43Hz step increments (stutter/judder) on high refresh rate panels.
  2. **Recomposition Pollution:** Dynamic state reads (`smoothedCenterCents`, `lastVoicedFreq`) in the composable body forced full Composable function body recompositions. Un-remembered callback lambdas in `MonitorScreen.kt` triggered recompositions whenever `uiState` updated at 43 Hz.
  3. **Path Drawing Truncation Bug:** `isPathStarted` was reset to false when encountering unvoiced frames, causing preceding sung phrases to disappear whenever trailing unvoiced frames occurred (e.g. breath pauses).
- **Key Changes:**
  - **Decoupled Frame-Clock Rendering Loop:** Introduced a `withFrameNanos` frame-driven render loop running at the display's native refresh rate (60/120 Hz). Audio samples are timestamped using `android.os.SystemClock.uptimeMillis()`, aligning monotonic timeline coordinates directly with Choreographer frameNanos.
  - **Draw-Phase Invalidation Only:** Moved dynamic states (`frameTimeState`, `centerYState`) exclusively inside the `Canvas` draw scope. Annotated `PitchRingBuffer` with `@Stable` and remembered callback lambdas in `MonitorScreen.kt`. Recomposition counter confirms **0 recompositions/sec** of `PitchGraph` body during sustained singing.
  - **Deadband Auto-Follow Centering:** Auto-follow updates `targetCenterY` with a 2-cent deadband threshold (`abs(visualCents - lastTargetCenterY) > 2f`), preventing micro-hunting/jitter on natural vocal vibrato while smoothly gliding on note transitions via exponential easing.
  - **Zero Heap Allocations in Draw Hot Path:** Cached horizontal gradient `Brush` (reallocated only on width change) and precomputed 2D Swara label table for 5 octaves (-2..2) and 12 swaras, completely eliminating string formatting and object allocations during 60/120 Hz draw calls.
  - **Path Discontinuity & Dot Stroke Fix:** Implemented `hasSegments` + `isSegmentStarted` with `lineTo(x + 0.1f, y)` for isolated points so that breath pauses never discard earlier sung segments, and isolated vocal bursts draw crisply as rounded dots.
- **On-Device Benchmark & Profiling Results (Xiaomi Poco F5 - Snapdragon 7+ Gen 2):**
  - **PitchGraph Body Recomposition Rate:** **0 recompositions/sec** (verified across 2.5 minutes of continuous live singing: `PitchGraph recompositions in last 2s: 0`).
  - **Display Refresh Rate:** Rendered at native 60 Hz display refresh rate.
  - **Gfxinfo Metrics (Baseline decoupled loop):**
    - Janky frames: **256 / 10,447 (2.45%)**
    - 50th percentile frame time: **20 ms**
    - 90th percentile frame time: **32 ms**

---

## Performance Verification & Fix: VPM-Style Hardware-Accelerated `drawLines` & `DisplayPitchFilter`
- **Branch:** `fix-live-graph-smoothness`
- **Status:** Verified on Poco F5 & Ready to Merge
- **Problem Statement:**
  While data/frame clock decoupling achieved native refresh rate pacing, Skia `drawPath` CPU triangulation overhead (~20ms CPU draw) caused occasional dropped frames, and transient octave/glitch detector errors drew spurious vertical connection lines across pauses.
- **Reference Architecture Analyzed:**
  Decompiled Vocal Pitch Monitor (VPM, `com.tadaoyamaoka.vocalpitchmonitor`), which uses direct hardware-accelerated `Canvas.drawLines(pts, offset, count, paint)` with a 400-cent discontinuity break threshold.
- **Key Changes Implemented:**
  1. **Hardware-Accelerated `nativeCanvas.drawLines`:**
     - Replaced Skia `drawPath` with direct batched OpenGL vertex rendering (`nativeCanvas.drawLines(linePts, 0, ptIdx, paint)`).
     - Fixed `FloatArray(4800)` buffer pre-allocated once outside the draw hot path $\to$ **zero heap allocations per frame**.
     - CPU draw-lambda time dropped from ~20 ms to **0.42 ms average / 0.82 ms max** (sub-millisecond!).
  2. **Preserved Two-Pass Glow + Gradient Appearance:**
     - Cached native `LinearGradient` Shader (`secondaryColor.copy(alpha=0.6f)` to `primaryColor`).
     - Pass 1: Glow trace with 20% alpha and 1.8 dp stroke.
     - Pass 2: Sharp core trace with 100% alpha and 0.8 dp stroke. Verified identical to original UI.
  3. **Display-Side Pitch Filter (`DisplayPitchFilter.kt`):**
     - Confidence gating: confidence $< 0.85 \to$ gap (0 Hz).
     - 3-frame running median filter.
     - Transient 1-frame octave glitches snapped to median; sustained ($\ge 2$ frames) octave leaps accepted.
     - Wild outlier rejection ($> 600\text{ cents}$) suppressed.
     - Light EMA ($\alpha = 0.65$, $\tau \approx 20\text{ ms}$) on continuous voiced notes.
     - Discontinuity resets on breath pauses ($0\text{ Hz}$) and jumps $> 400\text{ cents}$.
     - Added display latency capped at $\approx 23-40\text{ ms}$ (imperceptible by ear).
  4. **Tunable Discontinuity Threshold (`DISCONTINUITY_BREAK_CENTS = 400f`):**
     - Breaks line segments across large pitch transitions, completely eliminating spurious vertical spikes while allowing meends and gamaks ($\le 100-150\text{ cents/frame}$) to render smoothly without breaking.
  5. **Comprehensive Unit Tests (`DisplayPitchFilterTest.kt`):**
     - 100% passing tests for confidence gating, transient vs. sustained octave handling, wild spike suppression, intentional leaps, and discontinuity resets.
- **On-Device Verification Results (Xiaomi Poco F5, live singing over wireless ADB):**
  - **Achieved Frame Rate:** **60 FPS** solidly locked to native 60.0 Hz display refresh rate.
  - **Draw Lambda Duration:** **0.42 ms average, 0.82 ms max** (sub-millisecond CPU draw).
  - **PitchGraph Body Recompositions:** **0 recompositions/sec** (`PitchGraph recompositions in last 2s: 0`).
  - **Gfxinfo Metrics (1,484 frames under sustained live singing):**
    - **50th percentile (median): 14 ms** (comfortably $\le 16.6\text{ ms}$ budget at 60 Hz)
    - **90th percentile: 20 ms**
    - **95th percentile: 22 ms**
    - **99th percentile: 27 ms**
    - **Janky frames: 1 / 1,484 (0.07%)** (down from 2.45% and 74% legacy)
    - **Number Missed Vsync: 0**
    - **Number Slow issue draw commands: 0**
    - **Slow bitmap uploads: 0**
    - **GPU 50th percentile: 3 ms; GPU 90th percentile: 3 ms; GPU 99th percentile: 4 ms**

---

## Feature: Vertical Pinch-to-Zoom & 1-Finger Y-Axis Scroll (Pinch-Pause Bugfix)
- **Branch:** `feature-vertical-zoom`
- **Status:** Implemented, Verified on Poco F5 & Ready for Review
- **Key Changes:**
  1. **Vertical Pinch-to-Zoom (`zoomY` / `centsRange`):**
     - Introduced `zoomY` state controlling `centsRange = 1200f / zoomY`, clamped between `0.25f` (4 full octaves visible, 4,800 cents) and `6.0f` (2 semitones visible, 200 cents). Default is `1.0f` (1 octave, 1,200 cents).
     - Applied at draw time as a viewport scale factor (`heightScale = height / centsRange`), preserving precomputed ingestion cents and the exact Just Intonation visual mapping (`Swara.actualToVisualCents`).
     - Rescaled Swara grid lines and labels dynamically; expanded `precomputedLabels` table to 9 octaves (`-4..4`) to maintain zero string allocations across all zoom levels.
     - Anchored vertical zoom around the viewport center (`centerYState.floatValue` when auto-follow is on, `scrollOffsetY` when off) for zero jump.
  2. **1-Finger Vertical Scroll Across Y-Axis (Follow OFF):**
     - When auto-follow is disabled, single-finger vertical drag smoothly scrolls the viewport across octaves (`scrollOffsetY += dy * (centsRange / heightPx)`), clamped between -4,800 and +4,800 cents.
     - Latched gesture discrimination: `isVerticalDrag` latches once vertical touch motion exceeds `touchSlop`, ensuring smooth continuous tracking without diagonal misclassification.
     - Seamless mode transition: toggling auto-follow OFF initializes `scrollOffsetY` to the current `centerYState.floatValue`, eliminating viewport jumping. Toggling auto-follow ON smoothly eases from `scrollOffsetY` back to the active voice.
     - Wrapped dynamic props (`autoFollow`, `isLive`, `onScrollStart`) in `rememberUpdatedState` so the pointer gesture scope always reacts to live toggles without restarting gesture pipelines.
  3. **Gesture Separation & Fix for Pinch-Pause Bug:**
     - Replaced `detectTransformGestures` with a dedicated low-level gesture detector via `awaitEachGesture`.
     - Two-finger pinch (`pointerCount >= 2`): updates `zoomY` (vertical pinch) and `zoomX` (horizontal pinch) as pure viewport transforms. **Never** calls `onScrollStart()`, **never** alters `scrollOffsetX`, and **never** pauses live feed.
     - Single-finger drag (`pointerCount == 1`): only an explicit horizontal drag exceeding `touchSlop` and dominating vertical motion enters history scrub mode (`onScrollStart()`). Single-finger vertical drag leaves LIVE mode active.
  4. **Zero Allocations & 0 Recompositions/sec:**
     - Touch states read inside draw lambda trigger Draw-phase invalidation only, leaving Composable body recomposition at 0/sec.
- **On-Device Verification Results (Xiaomi Poco F5, Snapdragon 7+ Gen 2):**
  - **Single-Finger Vertical Scroll:** Verified on-device with Follow OFF. Scrolling down brings upper octaves ('S, 'R, 'G) and Tara saptak into view; scrolling up reveals Mandra saptak (N., D., P.) below base Sa. LIVE mode remains continuously active throughout dragging.
  - **Recompositions:** **0 recompositions/sec** (`PitchGraph recompositions in last 2s: 0`).
  - **FPS:** **60 FPS** locked to display refresh rate.
  - **Draw Lambda Duration:** **0.50 ms avg / 0.94 ms max** (sub-millisecond!).
  - **Gfxinfo Median Frame Time:** **17 ms** (at 60 Hz display refresh rate), **2.73% janky frames**.
  - **Unit Tests:** All unit tests pass (`BUILD SUCCESSFUL in 2m 41s`).
