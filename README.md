# Glosso Studio

Glosso Studio is a professional pronunciation trainer built with Kotlin Multiplatform and Jetpack Compose.

## Features
- Real-time Phonetic Assessment
- Offline First (Allosaurus model)
- Mastery & Streak System

## License
This project is licensed under the **GNU Affero General Public License v3 (AGPLv3)**. 

See the [LICENSE](LICENSE) file for the full license text.

## Setup & Running

To run this project locally, follow these steps:

1.  **Prerequisites:**
    *   **Git LFS:** This project uses Git Large File Storage for its ONNX models. Ensure you have [Git LFS](https://git-lfs.github.com/) installed before cloning.
    *   **Android Studio Hedgehog (2023.1.1) or later.**
    *   **JDK 17.**

2.  **Clone the Repository:**
    ```bash
    git clone https://github.com/shirobyte42/glosso-studio.git
    cd glosso-studio
    git lfs pull
    ```

3.  **Open in Android Studio:**
    *   Open the project from Android Studio.
    *   Let Gradle sync and download dependencies.

4.  **Run:**
    *   Select `androidApp` in the run configurations.
    *   Run it on an emulator or physical device.
