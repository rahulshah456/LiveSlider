<img width="1200" height="208" alt="header" src="https://github.com/user-attachments/assets/736cc2d3-8352-4ee7-8a7a-93fff452a6b7" />

# Live Slider - Parallax Slideshow Live Wallpaper
[![GitHub release](https://img.shields.io/github/v/release/rahulshah456/LiveSlider?style=for-the-badge)](https://github.com/rahulshah456/LiveSlider/releases/latest)
<a href="https://play.google.com/store/apps/details?id=com.droid2developers.liveslider"><img alt="Get it on Google Play" src="https://img.shields.io/badge/Google_Play-414141?style=for-the-badge&logo=google-play&logoColor=white" /></a>
<img alt="Android" src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" />

Welcome to Live Slider! This open-source Android application transforms your home screen into a dynamic, interactive experience. It creates a captivating parallax effect based on your device's movements and allows you to set up a personalized slideshow of your favorite wallpapers. Built with modern Android development practices, Live Slider is designed to be both feature-rich and developer-friendly.


## ✨ Key Features
- **Dynamic Parallax Effect:** Creates an illusion of depth by shifting the wallpaper in response to the device's rotation, providing an immersive 3D-like experience.
- **Customizable Slideshows:** Automatically cycle through your selected images at intervals you define, from seconds to hours.
- **Playlist Management:** Organize your wallpapers into distinct playlists for different moods or themes.
- **Double-Tap to Change:** A quick double-tap on your home screen instantly switches to the next wallpaper in your slideshow.
- **Material You & Dynamic Theming:** The app's UI seamlessly integrates with your device's color palette and system theme (Android 12+).
- **Fine-Grained Customization:** Adjust the parallax sensitivity, slideshow frequency, and other settings to perfectly match your preferences.




## 📸 Screenshots
<img width="1771" height="720" alt="group banner" src="https://github.com/user-attachments/assets/75b718d8-42ce-4170-ba44-fafdc3b09195" />

## 🚀 Getting Started

Follow these instructions to get the project up and running on your local machine for development and testing.

### Prerequisites
- Android Studio (latest stable version recommended)
- An Android device or emulator with **Rotation Vector Sensor** support. The parallax effect will not work without this sensor.

### Installation & Build
1.  **Clone the repository:**
    ```sh
    git clone https://github.com/droid2developers/LiveSlider.git
    ```
2.  **Open in Android Studio:**
    - Launch Android Studio.
    - Select `File > Open` and navigate to the cloned project directory.
3.  **Sync Gradle:**
    - Let Android Studio sync the project and download the required dependencies.
4.  **Build and Run:**
    - Select the `app` configuration and a target device.
    - Click the "Run" button. The app will be installed on your device.
5.  **Set as Live Wallpaper:**
    - Open the app and configure your desired settings.
    - Activate the live wallpaper through the app's main screen.

## 🛠️ Project Architecture & Technical Details

<details>
<summary><strong>Click to expand: Core Concept & How It Works</strong></summary>

### The Core Concept
The fundamental idea behind Live Slider is to create a more engaging and dynamic home screen. The parallax effect is achieved by using the device's orientation sensors to subtly shift the wallpaper. This creates a sense of depth, making it feel like you are looking through a window into a 3D space. Combined with the slideshow feature, the wallpaper becomes a constantly refreshing and personalized element of the user interface.

### How the Parallax Effect is Implemented
1.  **Sensor Data Acquisition:** The `RotationSensor` class registers a listener for the `TYPE_ROTATION_VECTOR` sensor. This sensor provides accurate data about the device's orientation in 3D space.
2.  **Offset Calculation:** The raw sensor data (a quaternion) is processed to determine the device's pitch and roll. These values are then mapped to X and Y offsets. A smoothing filter is applied to prevent jittery movements.
3.  **OpenGL Rendering:** The `LiveWallpaperRenderer` uses OpenGL ES to render the wallpaper. It applies the calculated X and Y offsets to the texture coordinates of the wallpaper image. This shifts the texture on the rendering surface, creating the parallax motion.
4.  **User Control:** The user can adjust the "Parallax Strength" in the settings. This value acts as a multiplier for the calculated offsets, allowing the user to control the intensity of the effect.

</details>

<details>
<summary><strong>Click to expand: Project Structure Overview</strong></summary>

The project follows a standard Android app architecture, separating concerns into different layers.

```
app/src/main/java/com/droid2developers/liveslider/
│
├── adapters/         # RecyclerView adapters for playlists and wallpapers
├── background/       # WorkManager components for background tasks (e.g., slideshow)
├── database/         # Room DB setup, DAOs, entities, and repositories
│   ├── dao/
│   ├── models/
│   └── repository/
├── live_wallpaper/   # Core logic for the live wallpaper service and rendering
│   ├── LiveWallpaperService.java  # The main service entry point
│   ├── LiveWallpaperRenderer.java # Handles all OpenGL rendering
│   └── RotationSensor.java        # Manages sensor data for parallax
├── utils/            # Utility classes for file handling, image manipulation, etc.
├── viewmodel/        # ViewModels for managing UI-related data
└── views/            # Activities, Fragments, and the Application class
    ├── activities/
    └── fragments/
```
</details>

<details>
<summary><strong>Click to expand: Core Libraries & Technologies</strong></summary>

- **GLWallpaperService:** A crucial base class that simplifies the creation of OpenGL-based live wallpapers. It manages the `EGL` context and rendering surface, allowing us to focus on the rendering logic itself.
- **Android Architecture Components:**
    - **ViewModel:** Manages UI-related data in a lifecycle-conscious way, surviving configuration changes.
    - **LiveData:** An observable data holder that respects the lifecycle of UI components.
    - **Room:** A persistence library that provides an abstraction layer over SQLite for robust local data storage. Used for storing wallpaper lists and playlists.
    - **WorkManager:** Manages deferrable, asynchronous background tasks, perfect for scheduling the wallpaper slideshow.
- **Material Components for Android:** Provides modern, customizable UI components that implement the Material Design system, including support for Material You.
- **EventBus:** A publish/subscribe event bus that simplifies communication between components (e.g., from the background service to the UI) without requiring direct dependencies.
- **Glide:** An efficient image loading and caching library that handles loading wallpapers from URIs, resizing, and applying transformations.
- **Kotlin Coroutines:** Used for managing asynchronous operations and background tasks in a structured and concise way, especially within ViewModels and repositories.

</details>

## 🤝 Contributing

Contributions are what make the open-source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

### How to Contribute
1.  **Fork the Project:** Click the "Fork" button at the top right of this page.
2.  **Create your Feature Branch:**
    ```sh
    git checkout -b feature/AmazingFeature
    ```
3.  **Commit your Changes:**
    ```sh
    git commit -m 'Add some AmazingFeature'
    ```
4.  **Push to the Branch:**
    ```sh
    git push origin feature/AmazingFeature
    ```
5.  **Open a Pull Request:** Navigate to your forked repository and open a new pull request against the `main` branch.

Please make sure to describe your changes clearly in the pull request. If you're fixing a bug, link to the issue if one exists.

## 🙏 Credits & Acknowledgements
This project was inspired by and builds upon the excellent work of the following open-source projects:
- [Beleco Parallax Live Wallpaper](https://github.com/dklaputa/BelecoLiveWallpaper)
- [Muzei Live Wallpaper](https://github.com/romannurik/muzei/)
- [GLWallpaperService](https://github.com/GLWallpaperService/GLWallpaperService)
- [EventBus](https://github.com/greenrobot/EventBus)

## 📜 License

This project is distributed under the MIT License. See the `LICENSE` file for more information.

```
Copyright (c) 2025 droid2developers

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

