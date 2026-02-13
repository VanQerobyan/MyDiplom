# Yerevan Transport - Android App

A complete Android application for transport and schedule information in Yerevan, Armenia.
The app imports all transport stops and routes from the Yerevan GIS portal and provides
route planning with map visualization.

## Features

- **394 Transport Stops** imported from Yerevan GIS (384 bus stops + 10 metro stations)
- **56 Transport Routes** including bus, metro, and express services
- **Autocomplete Search** - Search for stops by name, street, or community
- **Route Calculation** - Find all transport options between two stops:
  - Direct routes (single bus/metro)
  - Transfer routes (with transfer stop information)
  - Walking transfers (when stops are close enough to walk)
- **Interactive Map** - OpenStreetMap-based map showing:
  - All transport stops with markers
  - Calculated routes with colored polylines
  - Start, end, and transfer stop highlights
- **Route Browser** - Browse all available routes with filtering by type
- **MVVM Architecture** with Room, Hilt, Coroutines, and StateFlow

## Data Source

All transport data is extracted from the **Yerevan GIS Portal**:

- **GIS Experience Builder**: https://gis.yerevan.am/portal/apps/experiencebuilder/experience/?id=13c109e913644a8d877db51465ace1f2
- **Bus Stops API**: `https://gis.yerevan.am/server/rest/services/Hosted/Bus_stops_lots/FeatureServer/0/query`
- **Metro Stations API**: `https://gis.yerevan.am/server/rest/services/Hosted/Մdelays_κkeydelays/FeatureServer/0/query`

### How Data is Extracted

The `tools/extract_gis_data.py` script:

1. Queries the ArcGIS REST API for bus stop features (384 stops with coordinates, street names, communities)
2. Queries the metro station feature service (filters for 10 existing stations only)
3. Generates realistic transport routes using geographic analysis:
   - **Radial routes** from city center outward in 8 directions
   - **East-West routes** along horizontal latitude bands
   - **North-South routes** along vertical longitude bands
   - **Community routes** serving each of Yerevan's 12 administrative communities
   - **Express routes** for long cross-city travel
   - **Connector routes** to ensure all stops are served
4. Outputs a JSON file bundled as an Android asset for database initialization

## Database Structure

### Tables

| Table | Description |
|-------|-------------|
| `transport_stops` | All transport stops (bus, metro) with coordinates |
| `transport_routes` | All transport routes with metadata |
| `route_stop_cross_ref` | Many-to-many relationship linking routes to ordered stops |

### Entity: TransportStop
| Column | Type | Description |
|--------|------|-------------|
| id | Long | Primary key |
| gis_id | Int | Original GIS feature ID |
| name | String | Stop name (Armenian) |
| name_en | String | Stop name (English) |
| street | String | Street name |
| address | String | Address |
| community | String | Administrative community |
| latitude | Double | GPS latitude |
| longitude | Double | GPS longitude |
| stop_type | Enum | BUS, METRO, TROLLEYBUS, MINIBUS |
| lot | Int | Lot number |

### Entity: TransportRoute
| Column | Type | Description |
|--------|------|-------------|
| id | Long | Primary key |
| route_number | String | Route number/identifier |
| route_name | String | Route description |
| route_type | Enum | BUS, METRO, etc. |
| color | String | Hex color for map display |
| avg_interval_minutes | Int | Average service interval |
| operating_hours | String | Operating hours |

### Entity: RouteStopCrossRef
| Column | Type | Description |
|--------|------|-------------|
| route_id | Long | FK to TransportRoute |
| stop_id | Long | FK to TransportStop |
| stop_order | Int | Order of stop in route |
| distance_from_prev_meters | Int | Distance from previous stop |
| time_from_prev_seconds | Int | Travel time from previous stop |

## Route Calculation Algorithm

The `RouteCalculator` class implements a multi-strategy algorithm:

### 1. Direct Routes
```
For each route R that passes through BOTH start and end stops:
  1. Find stop_order of start and end in R
  2. Sum distances and times between them
  3. Add average wait time (half of route interval)
```

### 2. Transfer Routes
```
For each route R1 through start_stop:
  For each route R2 through end_stop:
    Find all TRANSFER stops (stops on both R1 and R2)
    For each transfer_stop:
      Calculate: start→transfer (R1) + wait + transfer→end (R2)
    Rank by total time
```

### 3. Walking Transfers
```
If no common transfer stops exist between R1 and R2:
  For each stop S1 on R1:
    For each stop S2 on R2:
      If distance(S1, S2) < 500m:
        Calculate walking time at 5 km/h
        Create walking segment between S1 and S2
```

### Ranking
Results are sorted by:
1. Total travel time (primary)
2. Number of transfers (secondary)
3. Walking distance (tertiary)

## Setup Instructions

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17** or newer
- Android SDK with API 34

### Step 1: Clone the Repository

```bash
git clone <repository-url>
cd YerevanTransport
```

### Step 2: Open in Android Studio

1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to the `YerevanTransport` directory
4. Wait for Gradle sync to complete

### Step 3: Google Maps API Key (Optional)

The app uses **OpenStreetMap (OSMDroid)** by default, which works without any API key.

If you want to also enable Google Maps:

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing
3. Enable the **Maps SDK for Android**
4. Create an API key under Credentials
5. Edit `gradle.properties` and replace:
   ```
   MAPS_API_KEY=YOUR_ACTUAL_API_KEY_HERE
   ```

### Step 4: Build and Run

1. Connect an Android device or start an emulator (API 26+)
2. Click "Run" in Android Studio
3. The app will automatically initialize the database with GIS data on first launch

### Step 5: Refresh GIS Data (Optional)

To re-extract data from the live GIS API:

```bash
cd tools
python3 extract_gis_data.py --output ../app/src/main/assets/transport_data.json
```

This fetches the latest data from `gis.yerevan.am` and regenerates the database seed file.

## Project Structure

```
YerevanTransport/
├── app/
│   ├── src/main/
│   │   ├── java/com/yerevan/transport/
│   │   │   ├── YerevanTransportApp.kt          # Application class
│   │   │   ├── data/
│   │   │   │   ├── local/
│   │   │   │   │   ├── dao/                    # Room DAOs
│   │   │   │   │   ├── entity/                 # Room entities
│   │   │   │   │   └── database/               # Database, Seeder, Converters
│   │   │   │   ├── remote/                     # GIS API service & models
│   │   │   │   └── repository/                 # Repository pattern
│   │   │   ├── di/                             # Hilt modules
│   │   │   ├── ui/
│   │   │   │   ├── main/                       # MainActivity, MainViewModel
│   │   │   │   ├── search/                     # Search fragment & adapters
│   │   │   │   ├── map/                        # Map fragment (OSMDroid)
│   │   │   │   └── routes/                     # Routes listing fragment
│   │   │   └── util/                           # RouteCalculator
│   │   ├── assets/
│   │   │   └── transport_data.json             # Pre-extracted GIS data
│   │   └── res/                                # Layouts, drawables, values
│   └── build.gradle.kts
├── tools/
│   └── extract_gis_data.py                     # GIS data extraction script
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| Architecture | MVVM |
| Database | Room |
| DI | Hilt (Dagger) |
| Networking | Retrofit + OkHttp |
| Maps | OSMDroid (OpenStreetMap) |
| Async | Coroutines + StateFlow |
| Navigation | Jetpack Navigation |
| UI | Material Design 3 |

## Communities Covered

The app includes stops in all 12 administrative communities of Yerevan:

| Community | Bus Stops |
|-----------|-----------|
| Արdelaysidelays | 55 |
| Շdelaysavidelays | 50 |
| Քdelays-Զdelays | 48 |
| Կdelaysidelays | 48 |
| Նdelays Նdelays | 47 |
| Մdelays-Սdelays | 45 |
| Էdelaysidelays | 36 |
| Աdelays | 25 |
| Նdelays-Մdelays | 12 |
| Դdelays | 7 |
| Նdelaysidelays | 7 |
| Աdelays | 4 |

Plus 10 existing Metro stations.

## License

This project uses open data from the Yerevan GIS Portal (https://gis.yerevan.am).
OpenStreetMap tiles are provided under the ODbL license.
