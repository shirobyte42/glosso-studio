# Glosso Studio

Glosso Studio is an offline-first pronunciation training application built with Kotlin Multiplatform and Jetpack Compose. It provides real-time phonetic assessment and a structured curriculum to help users improve their spoken English.

## Features

### Phonetic Assessment
The application uses the Allosaurus model (specifically `eng2102`) to perform phonetic recognition. It processes 8kHz audio, computes Mel-frequency cepstral coefficients (MFCCs), and runs inference via ONNX Runtime to provide feedback on pronunciation accuracy.

### Offline-First Architecture
The application is designed to be fully functional offline once the initial assets are retrieved.
- **ONNX Runtime:** Executes the acoustic model (`eng2102`) directly on the device.
- **Dynamic Database Download:** To keep the APK size minimal, curriculum databases are downloaded on-demand from GitLab when a level is first accessed.
- **Room Database:** Manages the repository of practice sentences and user progress.
- **Git LFS:** Used for managing large binary assets in the repository.

### Mastery and Progress Tracking
- **Curriculum Levels:** Six difficulty tiers ranging from Beginner to Mastery.
- **Mastery System:** Sentences are marked as mastered when users achieve a threshold score (85%+).
- **Streak Tracking:** Encourages consistent practice through a daily streak system verified against activity logs.
- **Statistics:** Comprehensive tracking of total mastered phrases and level-specific progress.

## Tech Stack

- **Framework:** Kotlin Multiplatform (KMP)
- **UI:** Jetpack Compose (Android)
- **Dependency Injection:** Koin
- **Database:** Room (Android)
- **Networking:** Ktor Client
- **Machine Learning:** ONNX Runtime for Android
- **Phonetic Model:** Allosaurus `eng2102` (GPL-3.0) for high-accuracy phonetic recognition
- **Text-to-Speech:** Qwen3-TTS (Apache-2.0) for high-fidelity speech synthesis
- **Audio Processing:** Custom MFCC implementation for 8kHz signal processing
- **Serialization:** Kotlinx Serialization

## Prerequisites

- **Git LFS:** Required to pull the large ONNX models and database.
- **Android Studio:** Hedgehog (2023.1.1) or later recommended.
- **JDK:** Version 17.

## Setup and Installation

1. **Install Git LFS**
   Ensure Git LFS is installed on your system before cloning:
   ```bash
   git lfs install
   ```

2. **Clone the Repository**
   ```bash
   git clone git@gitlab.com:shirobyte421/glosso-studio.git
   cd glosso-studio
   git lfs pull
   ```

3. **Open in Android Studio**
   Open the root directory as a project. Android Studio will automatically start the Gradle sync process and download necessary dependencies.

4. **Run the Application**
   Select the `androidApp` configuration and run it on a physical device or emulator (API 26 or higher).

## Architecture Overview

The project follows a clean architecture pattern within the Kotlin Multiplatform structure:
- **`shared` module:** Contains the domain logic, repositories, and cross-platform use cases.
- **`androidApp` module:** Contains the Compose UI, Android-specific data implementations (Room, Audio), and the ONNX integration.

## License

This project is licensed under the **GNU Affero General Public License v3 (AGPLv3)**. See the [LICENSE](LICENSE) file for the full license text.

## F-Droid

Glosso Studio is designed to be compatible with F-Droid.
- **Metadata:** Located in `fastlane/metadata/android`.
- **Build Recipe:** The `me.shirobyte42.glosso.yml` file is provided as a reference for F-Droid inclusion.
