# Shruti Monitor 
An advanced, high-performance Android application built for Indian classical musicians. It combines a real-time pitch monitor with a high-fidelity, sample-based Tanpura drone to support your daily practice (*riyaz*).

---

## 📸 Screen Previews

<p align="center">
  <img src="screenshots/pitch_monitor.png" width="23%" alt="Real-time Pitch Monitor" />
  <img src="screenshots/instruments_tanpura.png" width="23%" alt="Just Intonation Harmonium" />
  <img src="screenshots/ragas_screen.png" width="23%" alt="Ragas Explorer" />
  <img src="screenshots/settings_screen.png" width="23%" alt="Tuning & Calibration" />
</p>

---

## ✨ What's New in Version 1.1.0

- 🎙️ **Smooth, Continuous Vocal Pitch Trace**: Completely re-engineered the pitch rendering pipeline to produce continuous, fluid vocal curves matching reference pitch monitors, eliminating artificial staircase quantization and median filter lag.
- ⚡ **Time-Aware Dynamic Pitch Discontinuity**: Automatically tracks musical pitch velocity ($\le 6000\text{¢/sec}$) to keep fast *taans*, *gamakas*, and descending scales connected as solid lines while cleanly breaking across genuine octave leaps or pauses.
- 🛡️ **Dual-Threshold Voicing Hysteresis (Schmitt Trigger)**: Introduces attack (`0.82`) and sustain (`0.70`) confidence thresholds. Silence, breaths, and room rumble are strictly blocked, while soft trailing vocal decays and low *Mandra Saptak* notes stay connected.
- 🌉 **Micro-Gap Visual Bridging**: Intelligently bridges micro-dropouts ($\le 80\text{ ms}$) caused by acoustic interference, keeping vocal lines continuous without bridging actual musical pauses.
- ⏱️ **Hardware Capture Timestamps**: Decoupled x-axis plotting from coroutine scheduling by using native `AudioRecord` capture timestamps, eliminating horizontal jitter on high-refresh-rate displays.
- 👆 **1-Finger Vertical Canvas Scroll**: When Auto-Follow is disabled, freely pan the pitch graph vertically with one finger to explore any octave.
- 🎯 **Accurate Swara Cent Tuning**: Swara card cent readout directly reflects Just Intonation microtonal deviation (±cents) with color-coded in-tune feedback.

---

## 🚀 Quick Install (No Tech Skills Needed)

If you just want to use the app on your Android phone, you do not need to build it from code. You can download and install it in one click:

1. **Download the Installer (APK)**:
   - On your Android phone, open your web browser and click this direct download link:
     [Download ShrutiMonitor-v1.1.0.apk (Direct Link)](https://github.com/hari2897/shruti-monitor-android/releases/download/v1.1.0/ShrutiMonitor-v1.1.0.apk)
   - Or, go to the **Releases** tab on this GitHub page and download the latest `.apk` file.

2. **Open the File**:
   - Once downloaded, pull down your notification shade or open your phone's **Files** / **Downloads** app, and tap the downloaded `ShrutiMonitor-v1.0.apk` file.

3. **Allow Installation**:
   - If your phone displays a message saying *"For your security, your phone is not allowed to install unknown apps from this source"*, tap **Settings** in that message box and toggle on the switch for **Allow from this source**.
   - Tap **Install** when prompted.

4. **Launch the App**:
   - Once installed, tap **Open** or locate the new golden-saffron 3D treble-clef icon labeled **Shruti Monitor** on your phone's home screen or app drawer!

---

## 📖 Feature Guide

### 🎹 1. Setting Your Reference Tonic (Sa)
Before singing or playing the Tanpura, you must set your reference pitch (Tonic / Sa):
- Tap the **Tonic Card** at the top of the screen (or navigate to the **Settings** tab).
- You will see a scrollable **Keyboard Picker Strip** resembling piano keys.
- Tap any key to select your base note (C, C#, D, G, etc.).
- Use the **left/right arrow chevrons** to change octaves.
  - *Note: To maintain professional audio standards, the app prevents setting a tonic below the physical A2 (110.0 Hz) lower limit of the Tanpura sample set.*

### 🎙️ 2. Real-Time Pitch Monitor
The pitch monitor tracks your voice as you sing, showing your pitch deviation in cents:
- Go to the **Monitor** tab and tap the **Microphone Toggle Button** to activate monitoring.
- Grant microphone access permissions if prompted.
- Sing a note. A glowing colored line will draw on the graph, tracing your pitch stability in real-time.
- **Microtonal Grid**: The graph features horizontal grid lines representing the precise Just Intonation (*Gandhar*, *Madhyam*, *Pancham*, etc.) swaras.
- **In-Tune Target Feedback**: If you maintain your pitch within the tuning tolerance (e.g. ±10 cents), the note indicator glows bright mint-green, a progress circle animates to completion, and you will feel a gentle haptic tick feedback on your phone.

### 🎚️ 3. Scrolling Tuner Tape Scale
At the top of the monitor screen, a horizontal scale displays swaras from **Sa to Sa** (one full octave):
- As you sing from Sa to the upper Sa (*Tara Shadjam*), the glowing thumb slides smoothly from the far-left **S** (Sa) to the far-right **S** (Upper Sa).
- **Multi-Octave Scrolling**: If you sing higher than the upper Sa or lower than the starting Sa, the entire scale tape (ticks, labels, and saptak names like "Mandra", "Madhya", "Tara") slides smoothly to the left or right under the thumb.
- **Jitter-Free Boundaries**: Special hysteresis math prevents the scale from sliding back and forth if your voice wobbles slightly at the octave transition borders.

### 🪕 4. High-Fidelity Tanpura Drone
A high-performance sample-based sequencer modeled after professional electronic Tanpuras:
- Navigate to the **Play** tab and tap the large **START** button. The drone will fade in.
- Adjust the plucking speed factor (**Tempo**) from slow and meditative (0.5x) to fast and rhythmic (2.0x).
- **Jhala String Adjustments**: Set the first tuning string to Pancham (Pa), Madhyam (Ma), or Nishad (Ni) using the dropdown menu.
- **Fine-Tuning**: Move the **Fine Tuning Slider** to adjust microtonal cents (±50 cents).
- **432 Hz Mode**: Toggle this mode to instantly shift your tuning reference from the western standard A4=440Hz to the natural resonance frequency of A4=432Hz.
- **Transverse String Vibrations**: The 4 horizontal strings oscillate dynamically as they are plucked. When you tap **STOP**, the sound fades out safely over 900ms and the string animations swing to rest exponentially.

### 🎙️ 5. Session Recording
Record your practice sessions directly in the app:
- Tap the record button on the Monitor page.
- When finished, tap stop. You can play back your recorded session.
- Tap **Share** to send the recorded audio file to your guru, music teacher, or friends via WhatsApp, Email, or Google Drive.

---

## 🛠️ Developer Corner (Build from Source)

If you are a developer and want to modify or compile the project from code:

### Prerequisites
- JDK 17
- Android SDK (API 34+)

### Build Instructions
Open your terminal in the project root directory and build the debug APK using Gradle:

```bash
# Compile and build the debug APK
./gradlew assembleDebug
```

The output APK will be generated at:  
`app/build/outputs/apk/debug/app-debug.apk`

### Running / Deploying
Connect your Android phone via USB, enable Developer Options and USB Debugging, and run:

```bash
# Install the debug APK on your connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
