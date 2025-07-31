# Photo Reminder

Photo Reminder is an Android application written in Kotlin that lets you store photography spots on a map together with camera settings and photos.

This project was created as part of the **Programmazione di sistemi mobili** exam for the Bachelor's degree in **Ingegneria IET** at the University of Parma.
The backend is available at [Photo-Reminder-Backend](https://github.com/davekingdoms/Photo-Reminder-Backend).

## Features

- User registration and login with token saved via DataStore
- Google Map view showing saved markers
- Capture new photos with CameraX and associate them with map markers
- Local Room database with WorkManager background sync to a REST API
- Thumbnail generation and download for photos

## Getting Started

1. **Prerequisites**
   - Android Studio Hedgehog (or later) with Android SDK 35
   - A Google Maps API key
   - Optional: a backend API compatible with the endpoints defined in `ApiService`

2. **Clone the repository**

   ```bash
   git clone <repo-url>
   cd Photo-Reminder
   ```

3. **Set your Google Maps API key**

   The project uses the Secrets Gradle Plugin. Create a `secrets.properties` file (or update `local.properties`) in the project root and define:

   ```properties
   MAPS_API_KEY=YOUR_KEY_HERE
   ```

   A sample file exists as `local.default.properties`.

4. **Build and run**

   Use Android Studio to open the project or build from command line:

   ```bash
   ./gradlew installDebug
   ```

   When running on an emulator, the app connects to the backend at `http://10.0.2.2:5000/`. On a real device it defaults to `http://10.46.49.197:5000/` (see `RetrofitInstance.kt`).

5. **Manual synchronization**

   From the home screen you can trigger a manual sync which uploads local markers and downloads server updates. Periodic sync runs every 3 hours via WorkManager when logged in.

## Libraries Used

- AndroidX Room
- Retrofit with Moshi
- CameraX
- Google Maps & Places
- WorkManager
- Kotlin Coroutines

## Project Structure

The main source code resides under `app/src/main`:

- `data/` – local database, REST API and repositories
- `ui/` – view models and RecyclerView adapters
- Fragment classes for each screen

## Screenshots

_Add screenshots of the app here if available._

