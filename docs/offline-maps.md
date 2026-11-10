# Offline maps

## Problem
Navigation needs mobile data, and the unit often has none when you are driving.

## Symptoms
- Maps shows grey tiles or no route when the hotspot is off.
- After the Google update unblock, Maps may keep showing an update notification until it rechecks Play services.

## Options
1. **Google Maps offline areas.** In Maps, open your profile menu, choose Offline maps and download the area you drive in, over Wi-Fi. Routing inside the downloaded area works without data. Keep the downloaded areas updated, and check how much storage they use.
2. **Waze.** It has no offline maps. It needs data for the map and for traffic.
3. **Organic Maps.** An open-source app that uses OpenStreetMap data downloaded per country or region and works fully offline. Install it from its official release channel and compare the checksum with the published digest.

If Google Maps is the only navigation app, it needs Play services to be recent enough. See [Google updates](google-updates.md).

## Fix
Download the offline area while the unit is on a Wi-Fi network with data, with the car parked. Then turn the hotspot off and test a route.

## Check it worked
With Wi-Fi and mobile data off on the unit, search for a place inside the downloaded area and start a route.

## Rollback
Delete the offline area in the Maps app, or uninstall Organic Maps with `adb uninstall <package>`.

## Safety
- Do not set a route while driving.
- Offline areas use storage on a unit that may have little free space. Check `adb shell df /data`.
