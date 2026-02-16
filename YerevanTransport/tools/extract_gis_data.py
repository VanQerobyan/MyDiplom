#!/usr/bin/env python3
"""
Yerevan Transport GIS Data Extractor

Extracts transport stop and route data from Yerevan GIS ArcGIS REST API.

Data source: https://gis.yerevan.am/portal/apps/experiencebuilder/experience/?id=13c109e913644a8d877db51465ace1f2

API Endpoints:
- Bus stops:     https://gis.yerevan.am/server/rest/services/Hosted/Bus_stops_lots/FeatureServer/0/query
- Metro stations: https://gis.yerevan.am/server/rest/services/Hosted/Մdelays_κkeydelays/FeatureServer/0/query
- Metro lines:   https://gis.yerevan.am/server/rest/services/Hosted/Մdelays_lines/FeatureServer/0/query

Usage:
    python extract_gis_data.py [--output path/to/output.json]

The output JSON is placed in app/src/main/assets/transport_data.json
for the Android app to use during database initialization.
"""

import json
import math
import os
import random
import sys
import urllib.request
import urllib.parse
from typing import Dict, List, Optional, Tuple

# GIS API base URL
GIS_BASE_URL = "https://gis.yerevan.am/server/rest/services/Hosted"

# Service names (URL-encoded Armenian characters)
BUS_STOPS_SERVICE = "Bus_stops_lots"
METRO_STATIONS_SERVICE = "%D5%84%D5%A5%D5%BF%D6%80%D5%B8_%D5%AF%D5%A1%D5%B5%D5%A1%D5%B6%D5%B6%D5%A5%D6%80"
METRO_LINES_SERVICE = "%D5%84%D5%A5%D5%BF%D6%80%D5%B8_lines"


def fetch_features(service_name: str, max_records: int = 2000) -> List[dict]:
    """Fetch features from an ArcGIS Feature Service."""
    url = (
        f"{GIS_BASE_URL}/{service_name}/FeatureServer/0/query"
        f"?where=1%3D1&outFields=*&f=json&resultRecordCount={max_records}&outSR=4326"
    )
    print(f"  Fetching: {url[:100]}...")
    
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            data = json.loads(response.read().decode('utf-8'))
            features = data.get('features', [])
            print(f"  Retrieved {len(features)} features")
            return features
    except Exception as e:
        print(f"  Error fetching data: {e}")
        return []


def haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculate distance between two points in meters using Haversine formula."""
    R = 6371000  # Earth radius in meters
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def extract_bus_stops(features: List[dict]) -> List[dict]:
    """Extract and format bus stop data."""
    stops = []
    for feat in features:
        attrs = feat.get('attributes', {})
        geom = feat.get('geometry', {})
        
        if not geom or 'x' not in geom or 'y' not in geom:
            continue
            
        stops.append({
            'gisId': attrs.get('fid', 0),
            'name': (attrs.get('street', '') or '').strip(),
            'street': (attrs.get('street', '') or '').strip(),
            'address': (attrs.get('address', '') or '').strip(),
            'community': (attrs.get('community', '') or '').strip(),
            'latitude': geom['y'],
            'longitude': geom['x'],
            'stopType': 'BUS',
            'lot': attrs.get('lot', 0) or 0,
        })
    
    return stops


def extract_metro_stations(features: List[dict]) -> List[dict]:
    """Extract and format metro station data (existing stations only)."""
    stops = []
    for feat in features:
        attrs = feat.get('attributes', {})
        geom = feat.get('geometry', {})
        
        if not geom or 'x' not in geom or 'y' not in geom:
            continue
        
        # Armenian field names
        station_name = attrs.get('\u0574\u0565\u057f\u0580\u0578_\u056f\u0561\u0575\u0561\u0576', '')
        phase = attrs.get('\u0583\u0578\u0582\u056c', '')
        
        # Only include existing stations (not planned)
        if station_name and phase == '\u0533\u0578\u0575\u0578\u0582\u0569\u0575\u0578\u0582\u0576 \u0578\u0582\u0576\u0565\u0581\u0578\u0572':
            stops.append({
                'gisId': 10000 + (attrs.get('objectid', 0) or 0),
                'name': station_name.strip(),
                'street': '',
                'address': '',
                'community': '',
                'latitude': geom['y'],
                'longitude': geom['x'],
                'stopType': 'METRO',
                'lot': 0,
            })
    
    return stops


def generate_routes(all_stops: List[dict]) -> Tuple[List[dict], List[dict]]:
    """
    Generate transport routes based on geographic analysis.
    
    Route generation strategy:
    1. Radial routes: From city center outward in 8 directions
    2. East-West routes: Horizontal corridors across the city
    3. North-South routes: Vertical corridors across the city
    4. Community routes: Circular routes within each community
    5. Express routes: Long-distance routes spanning the city
    6. Metro line: Connects all existing metro stations
    7. Connector routes: Ensure all stops are on at least one route
    """
    bus_stops = [s for s in all_stops if s['stopType'] == 'BUS']
    metro_stops = [s for s in all_stops if s['stopType'] == 'METRO']
    
    # Yerevan center
    center_lat, center_lon = 40.1792, 44.4991
    
    # Calculate angles and distances from center
    for stop in bus_stops:
        dlat = stop['latitude'] - center_lat
        dlon = stop['longitude'] - center_lon
        angle = math.degrees(math.atan2(dlon, dlat))
        if angle < 0:
            angle += 360
        stop['_angle'] = angle
        stop['_dist'] = haversine(center_lat, center_lon, stop['latitude'], stop['longitude'])
    
    routes = []
    route_stop_refs = []
    route_id = [0]  # Use list for mutability in nested function
    
    route_colors = [
        '#E53935', '#D81B60', '#8E24AA', '#5E35B1',
        '#3949AB', '#1E88E5', '#039BE5', '#00ACC1',
        '#00897B', '#43A047', '#7CB342', '#C0CA33',
        '#FDD835', '#FFB300', '#FB8C00', '#F4511E',
        '#6D4C41', '#546E7A', '#2196F3', '#4CAF50'
    ]
    
    def create_route(route_num, route_name, stop_list, route_type='BUS', color='#2196F3'):
        route_id[0] += 1
        rid = route_id[0]
        route = {
            'id': rid,
            'routeNumber': str(route_num),
            'routeName': route_name,
            'routeType': route_type,
            'color': color,
            'avgIntervalMinutes': random.randint(5, 15),
            'operatingHours': '07:00-22:00'
        }
        refs = []
        for order, stop in enumerate(stop_list):
            dist = 0
            time = 0
            if order > 0:
                prev = stop_list[order - 1]
                dist = int(haversine(prev['latitude'], prev['longitude'],
                                    stop['latitude'], stop['longitude']))
                time = max(60, dist // 5)
            refs.append({
                'routeId': rid,
                'stopId': stop['id'],
                'stopOrder': order,
                'distanceFromPrevMeters': dist,
                'timeFromPrevSeconds': time
            })
        return route, refs
    
    all_used_stop_ids = set()
    
    # 1. Radial routes (8 directions from center)
    sectors = [(i * 45, (i + 1) * 45) for i in range(8)]
    for i, (ang_min, ang_max) in enumerate(sectors):
        sector_stops = [s for s in bus_stops if ang_min <= s['_angle'] < ang_max]
        sector_stops.sort(key=lambda s: s['_dist'])
        if len(sector_stops) >= 3:
            step = max(1, len(sector_stops) // 12)
            selected = sector_stops[::step][:15]
            if len(selected) >= 3:
                r, refs = create_route(i + 1, f"Radial Route {i+1}", selected, 'BUS', route_colors[i])
                routes.append(r)
                route_stop_refs.extend(refs)
                all_used_stop_ids.update(s['id'] for s in selected)
    
    # 2. East-West routes
    lat_bands = [(40.13, 40.15), (40.15, 40.17), (40.17, 40.19), (40.19, 40.21), (40.21, 40.23)]
    for i, (lat_min, lat_max) in enumerate(lat_bands):
        band = [s for s in bus_stops if lat_min <= s['latitude'] < lat_max]
        band.sort(key=lambda s: s['longitude'])
        if len(band) >= 3:
            step = max(1, len(band) // 12)
            selected = band[::step][:15]
            if len(selected) >= 3:
                r, refs = create_route(10 + i, f"East-West Route {10+i}", selected, 'BUS', route_colors[(8+i) % len(route_colors)])
                routes.append(r)
                route_stop_refs.extend(refs)
                all_used_stop_ids.update(s['id'] for s in selected)
    
    # 3. North-South routes
    lon_bands = [(44.44, 44.47), (44.47, 44.50), (44.50, 44.53), (44.53, 44.56), (44.56, 44.59)]
    for i, (lon_min, lon_max) in enumerate(lon_bands):
        band = [s for s in bus_stops if lon_min <= s['longitude'] < lon_max]
        band.sort(key=lambda s: s['latitude'])
        if len(band) >= 3:
            step = max(1, len(band) // 12)
            selected = band[::step][:15]
            if len(selected) >= 3:
                r, refs = create_route(20 + i, f"North-South Route {20+i}", selected, 'BUS', route_colors[(13+i) % len(route_colors)])
                routes.append(r)
                route_stop_refs.extend(refs)
                all_used_stop_ids.update(s['id'] for s in selected)
    
    # 4. Community routes
    by_community = {}
    for stop in bus_stops:
        comm = stop.get('community', '')
        if comm:
            by_community.setdefault(comm, []).append(stop)
    
    for i, (comm, stops) in enumerate(sorted(by_community.items())):
        if len(stops) >= 4:
            clat = sum(s['latitude'] for s in stops) / len(stops)
            clon = sum(s['longitude'] for s in stops) / len(stops)
            stops.sort(key=lambda s: math.atan2(s['longitude'] - clon, s['latitude'] - clat))
            step = max(1, len(stops) // 10)
            selected = stops[::step][:12]
            if len(selected) >= 3:
                r, refs = create_route(30 + i, f"Community Route {comm}", selected, 'BUS', route_colors[i % len(route_colors)])
                routes.append(r)
                route_stop_refs.extend(refs)
                all_used_stop_ids.update(s['id'] for s in selected)
    
    # 5. Metro line
    if len(metro_stops) > 1:
        metro_stops.sort(key=lambda s: s['latitude'], reverse=True)
        r, refs = create_route('M1', 'Metro Line 1', metro_stops, 'METRO', '#E53935')
        r['avgIntervalMinutes'] = 5
        r['operatingHours'] = '06:30-23:30'
        routes.append(r)
        route_stop_refs.extend(refs)
        all_used_stop_ids.update(s['id'] for s in metro_stops)
    
    # 6. Connector routes for unused stops
    unused = [s for s in all_stops if s['id'] not in all_used_stop_ids]
    if unused:
        unused.sort(key=lambda s: (s['latitude'], s['longitude']))
        chunk_size = 6
        for i in range(0, len(unused), chunk_size):
            chunk = unused[i:i + chunk_size]
            if len(chunk) >= 2:
                connected = [s for s in bus_stops if s['id'] in all_used_stop_ids]
                if connected:
                    nearest = min(connected, key=lambda s: haversine(
                        chunk[0]['latitude'], chunk[0]['longitude'],
                        s['latitude'], s['longitude']))
                    route_stops = [nearest] + chunk
                else:
                    route_stops = chunk
                
                r, refs = create_route(50 + i // chunk_size, f"Connector Route {50 + i//chunk_size}",
                                      route_stops, 'BUS', route_colors[(i // chunk_size) % len(route_colors)])
                routes.append(r)
                route_stop_refs.extend(refs)
                all_used_stop_ids.update(s['id'] for s in chunk)
    
    # 7. Express routes
    all_sorted = sorted(bus_stops, key=lambda s: s['latitude'], reverse=True)
    step = max(1, len(all_sorted) // 20)
    ns_route = all_sorted[::step][:20]
    if len(ns_route) >= 3:
        r, refs = create_route(100, 'Express North-South', ns_route, 'BUS', '#FF5722')
        routes.append(r)
        route_stop_refs.extend(refs)
    
    all_sorted = sorted(bus_stops, key=lambda s: s['longitude'])
    step = max(1, len(all_sorted) // 20)
    ew_route = all_sorted[::step][:20]
    if len(ew_route) >= 3:
        r, refs = create_route(101, 'Express East-West', ew_route, 'BUS', '#9C27B0')
        routes.append(r)
        route_stop_refs.extend(refs)
    
    return routes, route_stop_refs


def main():
    output_path = "app/src/main/assets/transport_data.json"
    
    # Parse arguments
    if '--output' in sys.argv:
        idx = sys.argv.index('--output')
        if idx + 1 < len(sys.argv):
            output_path = sys.argv[idx + 1]
    
    print("=" * 60)
    print("Yerevan Transport GIS Data Extractor")
    print("=" * 60)
    print()
    
    # Step 1: Fetch bus stops
    print("[1/3] Fetching bus stops from GIS API...")
    bus_features = fetch_features(BUS_STOPS_SERVICE)
    bus_stops = extract_bus_stops(bus_features)
    print(f"  Extracted {len(bus_stops)} bus stops")
    
    # Step 2: Fetch metro stations
    print("\n[2/3] Fetching metro stations from GIS API...")
    metro_features = fetch_features(METRO_STATIONS_SERVICE)
    metro_stops = extract_metro_stations(metro_features)
    print(f"  Extracted {len(metro_stops)} existing metro stations")
    
    # Assign IDs
    all_stops = bus_stops + metro_stops
    for i, stop in enumerate(all_stops):
        stop['id'] = i + 1
    
    # Step 3: Generate routes
    print(f"\n[3/3] Generating routes for {len(all_stops)} stops...")
    routes, route_stop_refs = generate_routes(all_stops)
    
    used_ids = set(ref['stopId'] for ref in route_stop_refs)
    print(f"  Generated {len(routes)} routes")
    print(f"  Created {len(route_stop_refs)} route-stop connections")
    print(f"  Stops on routes: {len(used_ids)}/{len(all_stops)}")
    
    # Prepare output
    output = {
        'stops': [{
            'id': s['id'],
            'gisId': s['gisId'],
            'name': s['name'],
            'nameEn': '',
            'street': s.get('street', ''),
            'address': s.get('address', ''),
            'community': s.get('community', ''),
            'latitude': s['latitude'],
            'longitude': s['longitude'],
            'stopType': s['stopType'],
            'lot': s.get('lot', 0)
        } for s in all_stops],
        'routes': routes,
        'routeStopRefs': route_stop_refs
    }
    
    # Write output
    os.makedirs(os.path.dirname(output_path) if os.path.dirname(output_path) else '.', exist_ok=True)
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(output, f, ensure_ascii=False)
    
    file_size = os.path.getsize(output_path)
    print(f"\n{'=' * 60}")
    print(f"Output saved to: {output_path}")
    print(f"File size: {file_size / 1024:.1f} KB")
    print(f"{'=' * 60}")
    
    # Print summary
    print(f"\nSummary:")
    print(f"  Bus stops:       {len(bus_stops)}")
    print(f"  Metro stations:  {len(metro_stops)}")
    print(f"  Total stops:     {len(all_stops)}")
    print(f"  Total routes:    {len(routes)}")
    print(f"  Route-stop links: {len(route_stop_refs)}")
    
    # Community breakdown
    communities = {}
    for stop in bus_stops:
        comm = stop.get('community', 'Unknown')
        communities[comm] = communities.get(comm, 0) + 1
    
    print(f"\n  Communities ({len(communities)}):")
    for comm, count in sorted(communities.items()):
        print(f"    {comm}: {count} stops")


if __name__ == '__main__':
    main()
