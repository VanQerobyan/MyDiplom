# Yerevan Transport Information System

A complete Android application for Yerevan's public transport and schedule information. Built with Kotlin, Room, and MVVM architecture.

## Features

- **Search with Autocomplete**: Enter starting and ending stops with real-time autocomplete suggestions
- **Route Calculation**: 
  - Direct routes: Shows all transport options between two stops
  - Transfer routes: When no direct route exists, finds nearest transfer stops with distance and estimated time
- **Interactive Map**: Displays all stops and routes with calculated paths visually highlighted
- **GIS Data Integration**: Automatically fetches and imports transport data from Yerevan GIS portal

## Data Source

The app imports transport data from the Yerevan GIS portal:
https://gis.yerevan.am/portal/apps/experiencebuilder/experience/?id=13c109e913644a8d877db51465ace1f2

**Data Extraction Strategy:**
1. Fetches Experience Builder config to discover ArcGIS Feature Service layer URLs
2. Queries ArcGIS REST API for stops and routes (geometry + attributes)
3. Parses point geometries (stops) and polyline geometries (routes)
4. Falls back to embedded Yerevan transport data if GIS is unavailable or returns no data

## Route Calculation Algorithm

1. **Direct Routes**: Find routes that serve both start and end stops. Verify stop order (start before end) in route sequence.
2. **Transfer Routes** (when no direct route):
   - Get all routes serving the start stop
   - Get all routes serving the end stop
   - Find common stops (transfer points) between route paths
   - For each transfer: segment 1 = start → transfer, segment 2 = transfer → end
   - Calculate distance using Haversine formula
   - Estimate time: ~400m/min walking + transit speed
3. Results sorted by estimated travel time

## Setup Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Google Maps API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing
3. Enable **Maps SDK for Android** and **Places API** (optional)
4. Create credentials → API Key
5. Restrict the key to your app's package name: `com.yerevan.transport`

**Add the API key:**

1. Copy `local.properties.example` to `local.properties`
2. Add your Maps API key: `MAPS_API_KEY=your_actual_api_key_here`
3. Add your Android SDK path: `sdk.dir=/path/to/Android/sdk`

### Build & Run

```bash
cd YerevanTransport
./gradlew assembleDebug
# Or open in Android Studio and Run
```

### Database Population

The database is populated automatically on first launch:
1. App attempts to fetch data from GIS
2. If successful, stores stops, routes, and route-stop mappings
3. If GIS fails, uses embedded fallback data (25 stops, 6 routes)

Use the refresh button (↻) in the search card to re-sync from GIS.

## Project Structure

```
app/src/main/java/com/yerevan/transport/
├── data/
│   ├── local/           # Room entities, DAOs, Database
│   ├── remote/          # GIS fetcher, ArcGIS API
│   └── repository/      # TransportRepository
├── domain/model/        # TransportStop, RouteOption
├── di/                  # Hilt modules
└── ui/                  # MainActivity, TransportViewModel
```

## Tech Stack

- **Kotlin** 1.9
- **Room** - Local database
- **Hilt** - Dependency injection
- **Retrofit** - HTTP for ArcGIS API
- **Coroutines & Flow** - Async
- **ViewModel & StateFlow** - MVVM
- **Google Maps SDK** - Map display

## License

MIT
