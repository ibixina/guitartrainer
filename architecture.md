# Architecture Proposal: Guitar Neck Trainer App (Phase 1)

## Objective
Design the initial architecture for a portable guitar neck trainer mobile app focusing on chords, chord switching, and fingering patterns.

## Tech Stack
- **Native Applications**:
  - **Primary Target (Prototype)**: **Android** (Kotlin using Jetpack Compose with Canvas for rendering).
  - **Secondary Target**: **iOS** (Swift using SwiftUI with Canvas/CoreGraphics for custom rendering).
- **Core Advantages**: Maximum performance and responsiveness, which is crucial for real-time interaction like multi-touch fingering detection.

## UI/UX & Orientation
- **Orientation**: Fixed to **Portrait**. The user holds the phone like a guitar neck.
- **Handedness**: Standard right-handed guitar — left hand frets, so Low E (thickest) is on the **left**, High E (thinnest) on the **right**. Nut at the top, higher frets downward.
- **Visuals**: A custom-rendered fretboard spanning the device.
  - **Virtual Full-Sized Neck**: The app models a complete, mathematically accurate acoustic guitar neck where fret distances naturally decrease as you move up the neck.
  - **True-to-Life Scale**: The size of the neck, and the spacing between frets and strings, MUST exactly match the physical dimensions of an acoustic guitar.
  - **Screen Independence**: Rendering must rely on physical device metrics (PPI/DPI) to draw in real-world inches/millimeters, unaffected by the screen's logical size or aspect ratio.
  - **Dynamic Viewport**: The device screen acts as a "window" onto the virtual neck. The app displays only the relevant physical slice of the neck (e.g., the top part, the middle part) depending on the chord or exercise being practiced.
  - **Future Customization**: Support for custom sizing, including an auto-adjustment feature where the user can take a picture of their own guitar to calibrate the exact dimensions.
- **Touch Interaction & Accuracy**:
  - Requires full multi-touch support to detect complex chords (up to 4-5 simultaneous touch points).
  - **Muting Support**: The app will differentiate between a firm fingertip press (a fretted note) and a lighter or broader touch (muting an adjacent string). It will allow intentional muting where appropriate for the chord, rather than treating all adjacent string touches as strict errors.
- **Audio Feedback**: The app will play the actual sound of the chord when the user correctly places all their fingers.

## Data Architecture (Phase 1)
- **Storage**: Built-in chords and fingering patterns will be stored locally (e.g., as JSON files bundled with the app, or a lightweight SQLite database).
- **Domain Models**:
  - `Chord`: Represents a chord (e.g., "C Major", "G7").
  - `FingeringPattern`: Represents the required finger positions (Fret, String, Finger 1-4).
  - `TrainingSession`: Records accuracy, latency, and success rate to track the user's progress.

## System Components
1. **Fretboard View (Custom Canvas)**: Handles the low-level rendering of strings, frets, and target indicator dots.
2. **Touch Processor**: Maps physical screen coordinates to logical fret/string intersections. It evaluates multi-touch events against the active `FingeringPattern`, including touch pressure/area to distinguish between fretting and muting, and calculates accuracy.
3. **Training Engine**: Cycles through chord exercises, feeds target patterns to the UI, processes the results from the Touch Processor, and provides immediate visual feedback (e.g., green dot for correct finger placement, visual indicator for intentional muting vs. accidental string touches).
4. **Audio Engine**: Handles playing the audio samples for individual notes or full chords upon successful finger placement.
5. **Data Repository**: Serves the built-in library of chords and patterns to the Training Engine.
