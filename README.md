# Yerevan Transport App

A complete Android application for transport and schedule information system for Yerevan, Armenia. This app provides route planning, stop search, and interactive map visualization for Yerevan's public transport system including buses, trolleybuses, and minibuses.

## Features

- **Smart Search**: Autocomplete search for starting and destination stops
- **Route Calculation**: 
  - Direct routes between stops
  - Routes with transfers when no direct route exists
  - Shows walking distance and estimated travel time
- **Interactive Map**: 
  - Visualizes all transport stops
  - Displays routes on the map
  - Shows calculated routes with polylines
- **Data Import**: Automatically extracts transport data from Yerevan GIS portal
- **Modern Architecture**: Built with MVVM pattern, Kotlin, Room database, and Material Design

## Screenshots

The app includes:
- Main search interface with autocomplete
- Route results with direct and transfer options
- Interactive Google Maps integration
- Clean Material Design UI

## Technology Stack

- **Language**: Kotlin
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room (SQLite)
- **UI**: Material Design Components, ViewBinding
- **Maps**: Google Maps Android API
- **Networking**: OkHttp, Retrofit
- **Parsing**: JSoup
- **Async**: Kotlin Coroutines
- **Lifecycle**: AndroidX Lifecycle components

## Project Structure

```
app/src/main/java/am/yerevan/transport/
├── data/
│   ├── database/         # Room database, DAOs
│   │   ├── TransportDatabase.kt
│   │   ├── StopDao.kt
│   │   ├── RouteDao.kt
│   │   └── RouteStopDao.kt
│   ├── model/            # Data entities
│   │   ├── Stop.kt
│   │   ├── Route.kt
│   │   ├── RouteStop.kt
│   │   └── RouteWithStops.kt
│   └── repository/       # Repository layer
│       └── TransportRepository.kt
├── ui/
│   ├── main/             # Main activity
│   │   └── MainActivity.kt
│   ├── map/              # Map activity
│   │   └── MapActivity.kt
│   └── search/           # Search adapters
│       ├── DirectRouteAdapter.kt
│       ├── TransferRouteAdapter.kt
│       └── StopAutoCompleteAdapter.kt
├── utils/
│   ├── GISDataExtractor.kt  # Data scraper
│   └── RouteCalculator.kt   # Route finding algorithm
├── viewmodel/
│   └── TransportViewModel.kt
└── TransportApplication.kt
```

## Database Schema

### Stop Entity
- `id`: Primary key
- `name`: Stop name (Armenian)
- `nameEn`: Stop name (English)
- `latitude`: GPS latitude
- `longitude`: GPS longitude
- `type`: Stop type (bus_stop, trolleybus_stop, minibus_stop, metro)

### Route Entity
- `id`: Primary key
- `routeNumber`: Route number (e.g., "1", "22")
- `routeName`: Route name
- `routeType`: Transport type (bus, trolleybus, minibus)
- `color`: Display color
- `isActive`: Active status

### RouteStop Entity (Junction Table)
- `id`: Primary key
- `routeId`: Foreign key to Route
- `stopId`: Foreign key to Stop
- `sequence`: Order of stop in route
- `direction`: Direction (0=forward, 1=backward)

## Route Calculation Algorithm

The app uses a graph-based pathfinding algorithm:

1. **Direct Routes**: 
   - Queries common routes between two stops
   - Validates stop sequence
   - Calculates distance and time

2. **Transfer Routes**:
   - Finds all routes from origin stop
   - For each potential transfer point:
     - Checks routes to destination
     - Validates walking distance (max 500m)
     - Calculates total time including transfer wait
   - Returns top 5 fastest routes

3. **Time Estimation**:
   - Average speed: 25 km/h
   - Stop wait time: 2 minutes per stop
   - Transfer wait: 5 minutes
   - Walking speed: 5 km/h

## Setup Instructions

### Prerequisites

1. **Android Studio**: Latest version (Flamingo or newer)
2. **JDK**: Java 17
3. **Android SDK**: API Level 24 (Android 7.0) minimum, API 34 target
4. **Google Maps API Key**: Required for map functionality

### Step 1: Clone the Repository

```bash
git clone <repository-url>
cd yerevan-transport-app
```

### Step 2: Configure Google Maps API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing one
3. Enable "Maps SDK for Android"
4. Go to "Credentials" and create an API key
5. Restrict the key to Android apps (optional but recommended)
6. Copy the API key

### Step 3: Create secrets.properties File

Create a file named `secrets.properties` in the project root:

```bash
cp secrets.properties.template secrets.properties
```

Edit `secrets.properties` and add your API key:

```properties
MAPS_API_KEY=YOUR_ACTUAL_API_KEY_HERE
```

**Important**: Never commit `secrets.properties` to version control. It's already in `.gitignore`.

### Step 4: Configure Android SDK Path

Edit `local.properties` and set your Android SDK path:

```properties
sdk.dir=/path/to/your/Android/Sdk
```

For example:
- **Windows**: `C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk`
- **Mac**: `/Users/YourName/Library/Android/sdk`
- **Linux**: `/home/YourName/Android/Sdk`

### Step 5: Open in Android Studio

1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to the project directory
4. Click "OK"
5. Wait for Gradle sync to complete

### Step 6: Build and Run

1. Connect an Android device or start an emulator
2. Click "Run" (green play button) or press Shift+F10
3. Select your device
4. App will install and launch

### Troubleshooting

**Gradle Sync Failed**:
- Check internet connection
- Try "File" > "Invalidate Caches" > "Invalidate and Restart"
- Check that you're using JDK 17

**Map not showing**:
- Verify API key is correct in `secrets.properties`
- Check that Maps SDK is enabled in Google Cloud Console
- Ensure device has Google Play Services

**Database errors**:
- Clear app data: Settings > Apps > Yerevan Transport > Clear Data
- Uninstall and reinstall the app

**No stops showing**:
- The app will automatically load sample data if GIS extraction fails
- Check internet connection on first launch

## Data Source

The app attempts to extract data from:
```
https://gis.yerevan.am/portal/apps/experiencebuilder/experience/?id=13c109e913644a8d877db51465ace1f2
```

If the extraction fails (due to API changes or network issues), the app automatically falls back to pre-populated sample data with real Yerevan stops and routes.

### Data Extraction Process

1. App queries ArcGIS REST API endpoints
2. Extracts stops with coordinates
3. Extracts routes with numbers and types
4. Creates route-stop relationships
5. Stores in local Room database

## Building for Release

1. Generate a signing key:
```bash
keytool -genkey -v -keystore yerevan-transport.keystore -alias yerevan-transport -keyalg RSA -keysize 2048 -validity 10000
```

2. Create `keystore.properties` in project root:
```properties
storePassword=YOUR_KEYSTORE_PASSWORD
keyPassword=YOUR_KEY_PASSWORD
keyAlias=yerevan-transport
storeFile=../yerevan-transport.keystore
```

3. Build release APK:
```bash
./gradlew assembleRelease
```

4. Find APK at: `app/build/outputs/apk/release/app-release.apk`

## Testing

### Unit Tests
```bash
./gradlew test
```

### Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## License

This project is provided as-is for educational and public use.

## Contact & Support

For issues, questions, or contributions, please open an issue on the repository.

## Acknowledgments

- Yerevan Municipality for the GIS data
- Google Maps Platform
- AndroidX and Jetpack libraries
- Open-source community

---

**Note**: This is a community project and is not officially affiliated with Yerevan Municipality or any transport authority.
