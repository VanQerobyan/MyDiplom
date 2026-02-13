# Yerevan Transport Information System (Android)

Android Studio project for a **Yerevan transport + route/schedule information app** built with:

- **Kotlin**
- **Jetpack Compose**
- **Room (SQLite)**
- **MVVM**
- **Google Maps Compose**

The app automatically extracts stops and route layers from the provided GIS ExperienceBuilder link:

`https://gis.yerevan.am/portal/apps/experiencebuilder/experience/?id=13c109e913644a8d877db51465ace1f2`

No manual stop/route entry is used.

---

## Features

- Auto-sync transport data from ArcGIS REST (resolved from the Experience item).
- Local Room database with transport stops, routes, route geometry, and route-stop relations.
- Start/end stop inputs with autocomplete.
- Route engine that:
  - finds **all direct options** between two stops;
  - if no direct option exists, finds **transfer options** with nearest transfer stop pair;
  - estimates total distance and time.
- Interactive map:
  - shows stops and route geometries;
  - highlights selected route option;
  - visualizes transfer walk segment when needed.
- Clean Material 3 UI and one-tap GIS refresh.

---

## Project structure

- `app/src/main/java/com/yerevan/transport/data/remote/ArcGisSyncService.kt`  
  ArcGIS extraction + parsing + pagination + route-stop matching.
- `app/src/main/java/com/yerevan/transport/data/local/...`  
  Room entities, DAOs, database.
- `app/src/main/java/com/yerevan/transport/data/repository/TransportRepository.kt`  
  Sync orchestration and data access.
- `app/src/main/java/com/yerevan/transport/domain/RoutePlanner.kt`  
  Direct and transfer route calculations.
- `app/src/main/java/com/yerevan/transport/ui/...`  
  MVVM state and Compose UI.

---

## Database schema

The app creates and populates these Room tables:

1. `stops`
   - `id` (PK)
   - `name`
   - `lat`, `lng`
   - source layer metadata
   - raw attributes JSON

2. `routes`
   - `id` (PK)
   - `name`
   - `mode` (`METRO`, `MONORAIL`, `RAIL`, ...)
   - geometry type
   - source layer metadata
   - raw attributes JSON

3. `route_shape_points`
   - composite key: `routeId + partIndex + pointIndex`
   - lat/lng shape points for map rendering

4. `route_stops`
   - composite key: `routeId + stopId`
   - distance from stop to route geometry
   - projected distance used for route ordering

5. `sync_metadata`
   - sync timestamp and record counts

---

## How automatic GIS extraction works

1. Load Experience item JSON from ArcGIS Portal.
2. Read `dataSources` to find referenced WebMap item IDs.
3. Download each WebMap JSON and recursively collect operational layer URLs.
4. Read layer metadata (`geometryType`, fields, object id field).
5. Classify transport layers:
   - stop layers: point layers with station/transport keywords;
   - route layers: polyline/polygon layers with rail/line/metro keywords.
6. Query all features using ArcGIS `/query` endpoint with pagination.
7. Convert geometries to WGS84 and store in Room.
8. Build `route_stops` memberships by nearest distance from each stop to each route shape.

This pipeline is run on first launch and can be refreshed from the top-right refresh button.

---

## Route calculation logic

For selected `start stop` and `end stop`:

### 1) Direct routes

- Determine route memberships for both stops.
- Intersect those memberships.
- Every common route is returned as a direct option.
- Distance/time are estimated from geodesic distance with mode-specific multipliers and speeds.

### 2) Transfer routes (fallback)

If no direct option:

- For each start-route/end-route pair:
  - If routes share a stop -> same-stop transfer option.
  - Otherwise -> nearest transfer pair (one stop on first route, one on second route).
- Add walking distance/time between transfer stops.
- Add transfer penalty and average headway wait.
- Return sorted transfer options by estimated total time.

> Note: The GIS source does not expose full GTFS-like timetables in these layers, so schedule output is presented as **estimated headway + travel time**.

---

## Setup instructions

### Prerequisites

- Android Studio (latest stable)
- JDK 17
- Android SDK installed

### 1) Clone and open

Open this project folder in Android Studio.

### 2) Configure `local.properties`

Create (or edit) `/workspace/local.properties`:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
```

### 3) Build and run

- Sync Gradle
- Run app on emulator/device (Android 8.0+ / API 26+)

---

## Google Maps API key instructions

1. Open Google Cloud Console.
2. Enable:
   - **Maps SDK for Android**
3. Create an API key.
4. Restrict key by:
   - Android apps (package `com.yerevan.transport`)
   - Your SHA-1 signing certificate.
5. Put the key in `local.properties` as `MAPS_API_KEY=...`.

If no key is provided, map tiles will not render correctly.

---

## Notes

- The importer is linked to the specified Yerevan GIS Experience item ID and discovers layers dynamically.
- Layer naming/availability in ArcGIS can change over time; refresh and classification rules may need tuning if upstream schema changes.
