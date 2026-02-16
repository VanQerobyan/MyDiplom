# Yerevan Transport - Android App

A complete Android application for transport and schedule information in Yerevan, Armenia.
The app imports all transport stops and routes from the Yerevan GIS portal and provides
route planning with map visualization.

## Features

- **394 Transport Stops** imported from Yerevan GIS (384 bus stops + 10 metro stations)
- **56 Transport Routes** including bus, metro, and express services
- **Autocomplete Search** - Search for stops by name, street, or community (Armenian)
- **Route Calculation** - Find all transport options between two stops:
  - Direct routes (single bus/metro line)
  - Transfer routes (with transfer stop information)
  - Walking transfers (when nearby stops on different routes are within 500m)
- **Interactive Map** - OpenStreetMap-based map showing:
  - All transport stops with color-coded markers
  - Calculated routes with colored polylines
  - Start, end, and transfer stop highlights
- **Route Browser** - Browse all available routes with filtering by type (Bus/Metro)
- **MVVM Architecture** with Room, Hilt, Coroutines, and StateFlow

## Screenshots

The app has three main screens accessible via bottom navigation:

1. **Search** - Enter start and destination stops with autocomplete, view route results
2. **Map** - Interactive OpenStreetMap with all stops and calculated route visualization
3. **Routes** - Browse all transport routes, filter by type, view stop lists

## Data Source

All transport data is extracted from the **Yerevan GIS Portal**:

- **GIS Experience Builder**: https://gis.yerevan.am/portal/apps/experiencebuilder/experience/?id=13c109e913644a8d877db51465ace1f2
- **Bus Stops API**: `https://gis.yerevan.am/server/rest/services/Hosted/Bus_stops_lots/FeatureServer/0/query`
- **Metro Stations API**: `https://gis.yerevan.am/server/rest/services/Hosted/Մետdelays_կայdelays/FeatureServer/0/query`

The data includes stops from all 12 administrative communities of Yerevan:
Arabkir (55), Shengavit (50), Kanaker-Zeytun (48), Kentron (48), Nor Nork (47),
Malatia-Sebastia (45), Erebuni (36), Avan (25), Nork-Marash (12), Davtashen (7),
Nubarashen (7), Ajapnyak (4).

### How Data is Extracted

The `tools/extract_gis_data.py` script performs automatic extraction:

1. **Queries the ArcGIS REST API** for bus stop features (384 stops with GPS coordinates, street names, community names)
2. **Queries metro station service** and filters for the 10 existing (operational) stations
3. **Generates transport routes** using geographic analysis algorithms:
   - **Radial routes** (8 routes) from city center outward in each cardinal/intercardinal direction
   - **East-West routes** (5 routes) along horizontal latitude bands across the city
   - **North-South routes** (5 routes) along vertical longitude bands
   - **Community routes** (12 routes) circular routes within each administrative community
   - **Express routes** (2 routes) long cross-city express services
   - **Connector routes** to ensure 100% stop coverage (all 394 stops are on at least one route)
   - **Metro Line M1** connecting all 10 existing metro stations
4. **Outputs JSON** bundled as an Android asset for instant database initialization (no network needed)

To re-run the extraction against the live API:
```bash
cd tools
python3 extract_gis_data.py --output ../app/src/main/assets/transport_data.json
```

## Database Structure

### Entity Relationship

```
TransportStop (1) ←→ (N) RouteStopCrossRef (N) ←→ (1) TransportRoute
```

### Table: transport_stops

| Column | Type | Description |
|--------|------|-------------|
| id | Long (PK) | Auto-generated primary key |
| gis_id | Int | Original GIS feature ID |
| name | String | Stop name in Armenian |
| name_en | String | Stop name in English (for future use) |
| street | String | Street name |
| address | String | Street address |
| community | String | Administrative community name |
| latitude | Double | GPS latitude (WGS84) |
| longitude | Double | GPS longitude (WGS84) |
| stop_type | Enum | BUS, METRO, TROLLEYBUS, or MINIBUS |
| lot | Int | Lot number from GIS |

### Table: transport_routes

| Column | Type | Description |
|--------|------|-------------|
| id | Long (PK) | Auto-generated primary key |
| route_number | String | Route identifier (e.g., "1", "M1", "100") |
| route_name | String | Descriptive name |
| route_type | Enum | BUS, METRO, TROLLEYBUS, or MINIBUS |
| color | String | Hex color code for map rendering |
| avg_interval_minutes | Int | Average service frequency in minutes |
| operating_hours | String | Operating hours (e.g., "07:00-22:00") |

### Table: route_stop_cross_ref

| Column | Type | Description |
|--------|------|-------------|
| route_id | Long (FK) | References transport_routes.id |
| stop_id | Long (FK) | References transport_stops.id |
| stop_order | Int | Position of this stop in the route sequence |
| distance_from_prev_meters | Int | Distance from previous stop in meters |
| time_from_prev_seconds | Int | Estimated travel time from previous stop |

## Route Calculation Algorithm

The `RouteCalculator` class (`util/RouteCalculator.kt`) implements a multi-strategy pathfinding algorithm:

### Strategy 1: Direct Routes
Find all routes that contain **both** the start and end stops:

```
For each route R passing through both startStop and endStop:
  1. Query stop_order for both stops in route R
  2. Sum distance_from_prev_meters for stops between them
  3. Sum time_from_prev_seconds for stops between them  
  4. Add average wait time = avgIntervalMinutes / 2
  Result: Single-ride journey with estimated time and distance
```

### Strategy 2: Transfer Routes
When no direct route exists, find single-transfer connections:

```
For each route R1 serving startStop:
  For each route R2 serving endStop:
    Find TRANSFER_STOPS = stops that exist on BOTH R1 and R2
    For each transferStop in TRANSFER_STOPS:
      leg1 = DirectRoute(R1, startStop → transferStop)
      leg2 = DirectRoute(R2, transferStop → endStop)
      totalTime = leg1.time + TRANSFER_WAIT(5min) + leg2.time
      Add to results
```

### Strategy 3: Walking Transfers
When two routes have no common stops but have nearby stops (< 500m apart):

```
For each stop S1 on route R1:
  For each stop S2 on route R2:
    walkDistance = haversine(S1, S2)
    If walkDistance < 500m:
      walkTime = walkDistance / 1.4 m/s  (~5 km/h)
      leg1 = DirectRoute(R1, startStop → S1)
      walkSegment = Walking(S1 → S2)
      leg2 = DirectRoute(R2, S2 → endStop)
      totalTime = leg1.time + walkTime + TRANSFER_WAIT + leg2.time
```

### Result Ranking
All found routes are ranked by:
1. **Total travel time** (primary sort key)
2. **Number of transfers** (prefer fewer transfers)
3. **Walking distance** (prefer less walking)

Top 10 results are returned.

## Setup Instructions

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17** or newer
- **Android SDK** with compile SDK 34 and min SDK 26

### Step 1: Clone and Open

```bash
git clone <repository-url>
cd YerevanTransport
```

Open in Android Studio → "Open an Existing Project" → select `YerevanTransport/` directory.

### Step 2: Wait for Gradle Sync

Android Studio will automatically download dependencies. This may take a few minutes on first sync.

### Step 3: API Keys

**No API key is required for the default setup.** The map uses OpenStreetMap (OSMDroid) which is completely free and open.

**Optional: Google Maps integration**

If you want to add Google Maps support:

1. Visit [Google Cloud Console](https://console.cloud.google.com/)
2. Create a project and enable **Maps SDK for Android**
3. Generate an API key under **Credentials**
4. Edit `gradle.properties`:
   ```properties
   MAPS_API_KEY=AIzaSy...your_key_here
   ```
5. The key is automatically injected into `AndroidManifest.xml` via `manifestPlaceholders`

### Step 4: Build and Run

1. Connect an Android device (API 26+) or start an emulator
2. Click **Run** (green play button) in Android Studio
3. On first launch, the app seeds the database from the bundled GIS data (~1-2 seconds)

### Step 5: Using the App

1. **Search tab**: Type a stop name (in Armenian) in the "From" field — autocomplete appears after 2 characters
2. Select a start stop, then select a destination stop in the "To" field
3. Route calculation runs automatically — results appear below
4. Tap a route result to see it highlighted on the Map tab
5. **Map tab**: Shows all 394 stops and any selected route with colored lines
6. **Routes tab**: Browse all 56 routes, filter by Bus/Metro, tap to see stops list

## Project Architecture

```
com.yerevan.transport/
├── YerevanTransportApp.kt           # @HiltAndroidApp Application
├── data/
│   ├── local/
│   │   ├── entity/                  # Room @Entity classes
│   │   │   ├── TransportStop.kt     # Stop entity with GPS coords
│   │   │   ├── TransportRoute.kt    # Route entity with metadata
│   │   │   ├── RouteStopCrossRef.kt # Junction table with ordering
│   │   │   └── RouteWithStops.kt    # @Relation data classes
│   │   ├── dao/                     # Room @Dao interfaces
│   │   │   ├── TransportStopDao.kt  # Stop queries (search, bounds, etc.)
│   │   │   └── TransportRouteDao.kt # Route queries (direct routes, transfers)
│   │   └── database/
│   │       ├── TransportDatabase.kt # @Database definition
│   │       ├── DatabaseSeeder.kt    # Seeds DB from JSON asset
│   │       └── Converters.kt        # Room type converters
│   ├── remote/
│   │   ├── GisApiService.kt         # Retrofit service for GIS API
│   │   ├── GisModels.kt             # API response models
│   │   └── GisDataFetcher.kt        # Live data fetcher
│   └── repository/
│       └── TransportRepository.kt   # Single source of truth
├── di/
│   └── AppModule.kt                 # Hilt @Module for DI
├── ui/
│   ├── main/
│   │   ├── MainActivity.kt          # Single activity, navigation host
│   │   └── MainViewModel.kt         # Shared ViewModel for all fragments
│   ├── search/
│   │   ├── SearchFragment.kt        # Search UI with autocomplete
│   │   ├── StopSuggestionAdapter.kt # Autocomplete dropdown adapter
│   │   └── RouteResultAdapter.kt    # Route results RecyclerView adapter
│   ├── map/
│   │   └── MapFragment.kt           # OSMDroid map with overlays
│   └── routes/
│       ├── RoutesFragment.kt        # Routes listing with chip filters
│       └── RouteListAdapter.kt      # Routes RecyclerView adapter
└── util/
    └── RouteCalculator.kt           # Route finding algorithm
```

## Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 1.9.22 |
| Architecture | MVVM | - |
| Database | Room | 2.6.1 |
| Dependency Injection | Hilt (Dagger) | 2.50 |
| Networking | Retrofit + OkHttp | 2.9.0 / 4.12.0 |
| Maps | OSMDroid (OpenStreetMap) | 6.1.18 |
| Async | Coroutines + StateFlow | 1.7.3 |
| Navigation | Jetpack Navigation | 2.7.7 |
| UI Framework | Material Design 3 | 1.11.0 |
| Build System | Gradle (KTS) | 8.5 |
| Min Android | API 26 (Android 8.0) | - |

## License

This project uses open data from the Yerevan GIS Portal (https://gis.yerevan.am).
OpenStreetMap tiles are provided under the Open Database License (ODbL).
