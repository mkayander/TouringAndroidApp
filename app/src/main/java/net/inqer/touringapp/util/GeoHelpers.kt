package net.inqer.touringapp.util

import android.location.Location
import android.util.Log
import net.inqer.touringapp.data.models.CalculatedPoint
import net.inqer.touringapp.data.models.Destination
import net.inqer.touringapp.data.models.Waypoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import kotlin.math.abs


object GeoHelpers {
    private const val TAG = "GeoHelpers"

    data class DistanceResult(
        val distance: Float,
        val initialBearing: Float,
        val finalBearing: Float
    )


    fun calculatePointBetween(p1: GeoPoint, p2: GeoPoint): GeoPoint {
        val bearing = p1.bearingTo(p2)
        val distance = p1.distanceToAsDouble(p2)
        return p1.destinationPoint(distance / 2, bearing)
    }


    suspend fun distanceBetween(location: Location, destination: Destination) =
        distanceBetween(
            location.latitude,
            location.longitude,
            destination.latitude,
            destination.longitude
        )

    suspend fun distanceBetween(location: Location, waypoint: Waypoint) =
        distanceBetween(
            location.latitude,
            location.longitude,
            waypoint.latitude,
            waypoint.longitude
        )


    suspend fun distanceBetween(waypoint: Waypoint, waypoint2: Waypoint) =
        distanceBetween(
            waypoint.latitude,
            waypoint.longitude,
            waypoint2.latitude,
            waypoint2.longitude
        )


    // TODO: Suspending these functions might be redundant
    private suspend fun distanceBetween(
        startLatitude: Double, startLongitude: Double,
        endLatitude: Double, endLongitude: Double
    ): DistanceResult {
        val results = FloatArray(3)
        Location.distanceBetween(startLatitude, startLongitude, endLatitude, endLongitude, results)
        return DistanceResult(results[0], results[1], results[2])
    }


    /**
     * Iterate through the waypoints, calculate distance between each and our location,
     * return closest waypoint distance and bearing, as well as the target's waypoint if one is specified.
     *
     * The reason of combining these 2 separate results into one task is simply to reduce the amount
     * of calling the [distanceBetween] calculation. While looping all waypoints to find the closest one,
     * we will get the target waypoint's result any way.
     *
     * We calculate both results within one distance calculation loop.
     *
     * @param location Location to measure distances from. Usually the current phone's location.
     * @param waypoints Array of available waypoints on the current route.
     * @param targetWaypoint If specified we check if encounter this waypoint's result
     * and return it as the second value.
     *
     * @return A pair of:
     * 1) Closest waypoint calculated distance & bearing (null if failed)
     * 2) Target waypoint calculated distance & bearing (null if failed or target not specified)
     */
    suspend fun findClosestWaypoint(
        location: Location,
        waypoints: Array<Waypoint>,
        targetWaypoint: Waypoint? = null
    ): Pair<CalculatedPoint?, CalculatedPoint?> {
        var targetDistance: DistanceResult? = null
        val distances = ArrayList<DistanceResult>()

        for (waypoint in waypoints) {
            val newResult = distanceBetween(location, waypoint)

            if (waypoint == targetWaypoint) {
                targetDistance = newResult
            }

            distances.add(newResult)
        }

        val minDistance =
            distances.minByOrNull { distanceResult: DistanceResult -> distanceResult.distance }

        val closestPointResult =
            minDistance?.let { CalculatedPoint(it, waypoints[distances.indexOf(minDistance)]) }
        val targetPointResult =
            targetDistance?.let { targetWaypoint?.let { wp -> CalculatedPoint(it, wp) } }

        return Pair(
            closestPointResult,
            targetPointResult
        )
    }


    /**
     * @param location Location to measure distances from. Usually the current phone's location.
     * @param destinations Array of available destinations at the current route.
     * @param targetDistance Optional parameter to override destination's own enter radius values.
     */
    suspend fun findActiveDestination(
        location: Location,
        destinations: Array<Destination>,
        targetDistance: Float? = null
    ): Destination? {
        for (destination in destinations) {
            val result = distanceBetween(location, destination)
            if (result.distance <= targetDistance ?: destination.radius) {
                Log.i(TAG, "findActiveDestination: found - $destination ; distance - $result")
                return destination
            }
        }

        return null
    }


    fun bearingToAzimuth(bearing: Float?): Float? {
        if (bearing == null) return null

        return if (bearing > 0) {
            bearing
        } else {
            abs(bearing - 180)
        }
    }


}