# Quick Setup Guide for Yerevan Transport App

## Getting Started in 5 Minutes

### 1. Prerequisites Check
- [ ] Android Studio installed (Flamingo or newer)
- [ ] Java 17 JDK installed
- [ ] Git installed

### 2. Get the Code
```bash
git clone <your-repo-url>
cd yerevan-transport-app
```

### 3. Get Google Maps API Key

**Why?** The app uses Google Maps to display stops and routes.

**How?**
1. Visit: https://console.cloud.google.com/
2. Create a new project (or use existing)
3. Enable "Maps SDK for Android"
4. Go to Credentials → Create Credentials → API Key
5. Copy the API key

### 4. Configure API Key

**Option A - Using Command Line:**
```bash
# Copy template
cp secrets.properties.template secrets.properties

# Edit the file (replace with your actual key)
# MAPS_API_KEY=AIzaSyXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
```

**Option B - Manual:**
1. Create file `secrets.properties` in project root
2. Add one line: `MAPS_API_KEY=YOUR_KEY_HERE`
3. Save and close

### 5. Set Android SDK Path

Edit `local.properties`:
```properties
# Windows
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk

# Mac
sdk.dir=/Users/YourName/Library/Android/sdk

# Linux
sdk.dir=/home/YourName/Android/Sdk
```

### 6. Open and Run

1. Open Android Studio
2. File → Open → Select project folder
3. Wait for Gradle sync (1-5 minutes)
4. Click green "Run" button ▶️
5. Select device/emulator
6. Done! 🎉

## Common Issues

### "Gradle sync failed"
- Check internet connection
- File → Invalidate Caches → Restart
- Check JDK 17 is installed

### "Map is blank"
- Verify API key in `secrets.properties`
- Check Maps SDK is enabled in Google Cloud
- Ensure device has Google Play Services

### "No stops appear"
- Wait 10-15 seconds on first launch
- App loads data automatically
- Uses sample data if internet fails

### "Cannot resolve symbol 'R'"
- Build → Clean Project
- Build → Rebuild Project
- File → Invalidate Caches → Restart

## Testing Without Google Maps API

If you want to test the app without setting up Google Maps:
1. Comment out map-related code in `MainActivity.kt` and `MapActivity.kt`
2. The search and route calculation will still work
3. You just won't see the map visualization

## Building APK

To create an installable APK:
```bash
./gradlew assembleDebug
```

Find APK at: `app/build/outputs/apk/debug/app-debug.apk`

## Project Structure Overview

```
├── app/
│   ├── src/main/
│   │   ├── java/am/yerevan/transport/
│   │   │   ├── data/          # Database & models
│   │   │   ├── ui/            # Activities & UI
│   │   │   ├── utils/         # Helpers & algorithms
│   │   │   └── viewmodel/     # ViewModels (MVVM)
│   │   └── res/               # Layouts, strings, colors
│   └── build.gradle.kts       # App dependencies
├── gradle/                    # Gradle wrapper
├── build.gradle.kts           # Project config
├── settings.gradle.kts        # Project settings
├── secrets.properties         # YOUR API KEY (create this!)
└── local.properties           # SDK path (auto-generated)
```

## Key Files to Understand

1. **MainActivity.kt** - Main search interface
2. **MapActivity.kt** - Map visualization
3. **TransportViewModel.kt** - Business logic
4. **RouteCalculator.kt** - Route finding algorithm
5. **GISDataExtractor.kt** - Data loading from GIS
6. **TransportDatabase.kt** - Database setup

## Features Walkthrough

### 1. Search for Stops
- Type at least 2 characters
- Autocomplete suggests stops
- Supports Armenian and English names

### 2. Find Routes
- Select start and destination
- Tap "Find Route"
- See direct routes or transfers

### 3. View on Map
- Tap floating map button
- See all stops on map
- Tap route to see path

### 4. Route Information
- Route number and name
- Number of stops
- Estimated travel time
- Transfer points (if applicable)
- Walking distance for transfers

## Data Source

The app loads data from Yerevan's official GIS portal:
```
https://gis.yerevan.am/portal/apps/experiencebuilder/
```

**Fallback:** If internet is unavailable or the API changes, the app uses pre-loaded sample data with 20 real Yerevan stops and 10 routes.

## Sample Data Included

The app includes real locations:
- Republic Square (Հանրապետության Հրապարակ)
- Opera (Օպերա)
- Cascade (Կասկադ)
- Yerevan State University
- Metro stations
- And more...

## Need Help?

1. Check README.md for detailed documentation
2. Open an issue on GitHub
3. Review the troubleshooting section

## Next Steps

After getting the app running:
1. Explore the code structure
2. Try modifying UI colors in `colors.xml`
3. Add new sample stops in `GISDataExtractor.kt`
4. Customize route calculation parameters
5. Add new features!

## Development Tips

- Use Android Studio's logcat to debug
- The app uses coroutines - check suspend functions
- Database queries are in DAO files
- UI updates via LiveData observers
- Route algorithm is in `RouteCalculator.kt`

---

**Good luck! If you encounter issues, check the main README.md or open an issue.**
