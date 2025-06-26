
# Let's Travel - Mobile App and Backend

Let's Travel is an Android mobile application with Firebase backend integration, supporting trip management, real-time notifications, user authentication (email/password, Google Sign-In, biometric login), location-based weather updates, eye-protection mode, and admin functionalities via a Node.js Express backend.

---

## Table of Contents
- [Project Structure](#project-structure)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [APIs and Sensors Used](#apis-and-sensors-used)
- [Setup Instructions](#setup-instructions)
- [API Endpoints](#api-endpoints)
- [Key Screens](#key-screens)
- [Notes](#notes)

---

## Project Structure

```plaintext
.
├── backend
│   ├── server.js              # Express backend server
│   ├── firebase.js            # Firebase configuration
│   ├── insertData.js          # Sample data insertion script
│   ├── package.json           # Node.js dependencies
│   ├── .env                   # Environment variables
│   └── data.json              # Example data
│
└── android-app (Android Studio Project)
    ├── app
    │   ├── src/main/java/com/app/letstravel
    │   └── res/layout
    ├── build.gradle
    └── other standard Android Studio files
```

---

## Features

### Mobile App
- 📍 **Real-time location detection and weather updates**
- 🔒 **Email/Password, Google OAuth, and Biometric authentication**
- 🌙 **Eye-protection mode using ambient light sensor**
- 🛑 **Portrait lock with orientation detection**
- 🗓️ **Trip creation, management, and joining**
- 🔔 **Push notifications for trip participation and admin messages**
- 📰 **Integrated travel news module**
- 🔐 **Admin authentication for trip management**

### Backend Server
- 🚀 **RESTful API for Firebase configurations, notifications, and trip management**
- 🔑 **Admin secret verification for sensitive operations**
- 📡 **Cloud Messaging integration for FCM push notifications**
- ⚙️ **Rate-limited public API endpoints**

---

## Tech Stack

- **Mobile:** Android (Java), Retrofit, Firebase Auth, Firebase Firestore, Firebase Messaging, Biometric API
- **Backend:** Node.js, Express, Firebase Admin SDK, Google Auth Library, Firestore, dotenv
- **Other:** FCM, OpenWeatherMap API, GNews API, Retrofit, Google Sign-In

---

## APIs and Sensors Used

### APIs:
- **Firebase Authentication** (Email/Password, Google Sign-In)
- **Firebase Firestore** (Real-time database)
- **Firebase Cloud Messaging (FCM)** (Push notifications)
- **Google OAuth 2.0 API** (Google Sign-In support)
- **Google Location Services API** (Location detection)
- **OpenWeatherMap API** (Weather data based on location)
- **GNews API** (Provides travel-related news)
- **Custom Backend APIs** (Node.js Express server endpoints)

### Android Sensors:
- **Light Sensor:** Eye-protection mode based on ambient light levels.
- **Accelerometer Sensor:** Portrait lock detection and user orientation monitoring.
- **GPS Sensor:** Combine Google Location Services API to provide the location data

---

## Setup Instructions

### Backend
1. Navigate to the `backend` folder:
    ```bash
    cd backend
    ```
2. Install dependencies:
    ```bash
    npm install
    ```
3. Configure `.env` with your Firebase and API keys.
4. Start the server:
    ```bash
    npm start
    ```
5. (Optional) Insert sample data:
    ```bash
    node insertData.js
    ```

### Android App
1. Open the Android project in **Android Studio**.
2. Set your server base URL in `LoginActivity.java` and `MainActivity.java` (e.g., `http://<your-server-ip>:3000/`).
3. Sync the project and run the app on an emulator or physical device.

---

## API Endpoints

| Endpoint                  | Method | Description                    |
|---------------------------|--------|--------------------------------|
| `/firebase-config`        | GET    | Get Firebase configuration     |
| `/auth/google-client-ids` | GET    | Get Google client IDs          |
| `/admin-secret`           | GET    | Get admin secret               |
| `/weather-api-key`        | GET    | Get weather API key            |
| `/trip-news-api-key`      | GET    | Get GNews API key              |
| `/send-notification`      | POST   | Send cloud notification        |
| `/join-trip`              | POST   | Notify admin of trip join      |

---

## Key Screens
- **Login Page:** Supports email, Google, and biometric authentication.
- **Main Page:** Displays location, weather, and trip list with refresh.
- **Trip Detail:** View trip info, join/unjoin trips.
- **New Trip Dialog:** Admin-only interface for trip creation.
- **Travel News:** Displays travel-related news using GNews API.

---

## Notes
- You need a valid Firebase project and Google OAuth setup.
- Notifications are managed via Firebase Cloud Messaging (FCM).
- Location permissions and notification permissions are required for full app functionality.
- Ensure your server is accessible to your mobile device (use public IP or local network).
