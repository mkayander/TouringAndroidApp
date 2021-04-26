package net.inqer.touringapp.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import net.inqer.touringapp.AppConfig
import net.inqer.touringapp.MainActivity
import net.inqer.touringapp.R
import net.inqer.touringapp.data.models.*
import net.inqer.touringapp.di.qualifiers.ActiveTourRouteLiveData
import net.inqer.touringapp.util.DispatcherProvider
import net.inqer.touringapp.util.GeoHelpers
import net.inqer.touringapp.util.GeoHelpers.bearingToAzimuth
import net.inqer.touringapp.util.GeoHelpers.findClosestWaypoint
import net.inqer.touringapp.util.round
import javax.inject.Inject

@AndroidEntryPoint
class RouteService : LifecycleService() {
    @Inject
    @ActiveTourRouteLiveData
    lateinit var activeTourRouteLiveData: LiveData<TourRoute?>

    @Inject
    lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastKnownLocation: Location? = null

    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var routeDataBus: ActiveRouteDataBus

    @Inject
    lateinit var appConfig: AppConfig

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private var currentStatus: ServiceAction = ServiceAction.STOP

    private var activeRoute: TourRoute? = null

    //    override fun onBind(intent: Intent?): IBinder? {
//        return null
//    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        intent?.let {
            val actionType = it.getSerializableExtra(EXTRA_INTENT_TYPE) as ServiceAction?

            if (actionType == currentStatus) return@let

            // Handle service communication using intents with certain type extra
            when (actionType) {
                ServiceAction.START -> {
                    launchService()
                    currentStatus = actionType
                }
                ServiceAction.STOP -> {
                    stopService()
                    currentStatus = actionType
                }
                ServiceAction.NEXT_WAYPOINT -> {
                    nextWaypoint()
                }
                ServiceAction.PREVIOUS_WAYPOINT -> {
                    previousWaypoint()
                }
            }
        }

        return START_STICKY
    }


    /**
     * Initiate the foreground service start sequence.
     */
    private fun launchService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        subscribeObservers()
        requestLocationUpdates()

        startForeground(NOTIFICATION_IDENTIFIER, createForegroundNotification())
    }


    /**
     * Initiate the service stop sequence.
     */
    private fun stopService() {
        removeLocationUpdates()
        clearBusData()
        stopSelf()
    }


    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: called")
    }


    /**
     * Initialize live data observers.
     */
    private fun subscribeObservers() {
        activeTourRouteLiveData.observe(this) { route ->
            Log.d(TAG, "activeTourRouteLiveData.observe: got route - $route")
            onActiveRouteChanged(route)
        }

        routeDataBus.targetWaypointIndex.observe(this) { index ->
            updateNotification(indexInput = index)

            lifecycleScope.launchWhenCreated {
                launch(dispatchers.default) {
                    recalculateTargetWaypointDistance()
                }
            }
        }

    }


    /**
     * Called each time the active route instance has been changed.
     */
    private fun onActiveRouteChanged(route: TourRoute?) {
        activeRoute = route

        Log.d(TAG, "onActiveRouteChanged: called, setting target to 0")
        setTargetWaypoint(0)
    }


    /**
     * Offset the select waypoint with the given step within the waypoints array.
     * @param step Active waypoint selection offset. Could be positive or negative.
     */
    private fun selectActiveWaypoint(step: Int) {
        val currentIndex = routeDataBus.targetWaypointIndex.value
        val nextIndex = currentIndex?.plus(step)
        val waypoints = activeRoute?.waypoints

        Log.d(TAG, "selectActiveWaypoint: moving waypoint. step = $step; index = $currentIndex ; nextIndex = $nextIndex ; waypoints = $waypoints")

        if (nextIndex != null && waypoints != null && nextIndex in waypoints.indices) {
            routeDataBus.targetWaypoint.postValue(waypoints[nextIndex])
            routeDataBus.targetWaypointIndex.postValue(nextIndex)
        } else {
            Log.w(TAG, "nextWaypoint: failed to activate next waypoint ; $nextIndex ; $waypoints")
        }
    }

    /**
     * Set active waypoint to the previous one.
     */
    private fun previousWaypoint() {
        Log.d(TAG, "previousWaypoint: called")
        selectActiveWaypoint(-1)
    }

    /**
     * Set active waypoint to the next one.
     */
    private fun nextWaypoint() {
        Log.d(TAG, "nextWaypoint: called")
        selectActiveWaypoint(1)
    }


    /**
     * Create the foreground service notification channel for API >= 26
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel =
                NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                )

        notificationManager.createNotificationChannel(channel)
    }


    /**
     * Create the location request with desired interval and priority parameters.
     * @return Properly configured [LocationRequest]
     */
    private fun createLocationRequest(): LocationRequest = LocationRequest.create().apply {
        val pollInterval = appConfig.locationPollInterval.toLong()
        interval = pollInterval
        fastestInterval = pollInterval / 2
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }


    /**
     * Simple location callback interface implementation that maps the location event to the local
     * method.
     */
    private val locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            onNewLocation(locationResult)
        }

        override fun onLocationAvailability(p0: LocationAvailability) {
            super.onLocationAvailability(p0)
            Log.d(TAG, "onLocationAvailability: $p0")
        }
    }


    /**
     * Called each time we receive the new location result.
     * The interval should depend on the [AppConfig.locationPollInterval] value.
     * @param locationResult Result of the latest location request
     */
    private fun onNewLocation(locationResult: LocationResult) {
        lastKnownLocation = locationResult.lastLocation

        activeRoute?.waypoints?.let { waypoints ->

            lifecycleScope.launchWhenCreated {
                Log.d(TAG, "onNewLocation: launching coroutine...")
                launch(dispatchers.default) {
                    val (closestPoint, targetPoint) = findClosestWaypoint(locationResult.lastLocation, waypoints, routeDataBus.targetWaypoint.value)
                    Log.i(TAG, "onLocationResult: closest waypoint - $closestPoint ; $targetPoint")

                    onClosestWaypointCalculated(closestPoint)
                    targetPoint?.let { onTargetWaypointCalculated(it) }
                }
                Log.d(TAG, "onNewLocation: launched!")
            }

        }
    }


    /**
     * Called on each calculated result of the closest waypoint.
     * @param point Result of [findClosestWaypoint] function. Null if failed.
     */
    private fun onClosestWaypointCalculated(point: CalculatedPoint?) {
        if (point == null) {
            Log.w(TAG, "onClosestWaypointCalculated: target point is null!")
        }
        routeDataBus.closestWaypointCalculatedPoint.postValue(point)
    }


    /**
     * Called on each successfully calculated result of the target waypoint.
     * @param point Result of [findClosestWaypoint] function.
     */
    private fun onTargetWaypointCalculated(point: CalculatedPoint) {
        routeDataBus.targetWaypointCalculatedDistance.postValue(point)

        updateNotification(targetCalculatedPoint = point)

        if (point.distanceResult.distance < WAYPOINT_ENTER_RADIUS) {
            nextWaypoint()
        }
    }


    /**
     * Manually recalculate current distance to target waypoint with existing [lastKnownLocation]
     * and [routeDataBus] target waypoint values.
     *
     * @return True if successful, false otherwise.
     */
    private suspend fun recalculateTargetWaypointDistance(): Boolean {
        lastKnownLocation?.let { location ->
            routeDataBus.targetWaypoint.value?.let { waypoint ->
                onTargetWaypointCalculated(
                        CalculatedPoint(GeoHelpers.distanceBetween(location, waypoint), waypoint)
                )
                return true
            }
        }

        return false
    }


    /**
     * Set the given waypoint as active.
     */
    private fun setTargetWaypoint(waypoint: Waypoint) =
            activeRoute?.waypoints?.let {
                setTargetWaypoint(it.indexOf(waypoint), waypoint)
            }

    /**
     * Sets the waypoint with the given id as target.
     */
    private fun setTargetWaypoint(index: Int) {
        activeRoute?.waypoints?.let {
            if (index in it.indices) {
                setTargetWaypoint(index, it[index])
            } else {
                Log.e(TAG, "setTargetWaypoint: out of bounds",
                        IndexOutOfBoundsException("Index $index is not in active waypoints bounds"))
            }
        }
    }

    /**
     * Sets the given target values to the liveData bus.
     */
    private fun setTargetWaypoint(index: Int, waypoint: Waypoint) {
        routeDataBus.targetWaypoint.postValue(waypoint)
        routeDataBus.targetWaypointIndex.postValue(index)
    }


    /**
     * Applies the [createLocationRequest] and [locationCallback] to the [fusedLocationClient], which
     * activates the GPS location updates with the specified parameters.
     */
    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Log.e(TAG, "requestLocationUpdates: Missing required permissions!")
            // TODO: Need to handle this situation as it is pretty common since service gets launched before the map fragment.
            return
        }
        fusedLocationClient.requestLocationUpdates(
                createLocationRequest(),
                locationCallback,
                Looper.getMainLooper()
        )
    }


    /**
     * Unsubscribe from the location updates of the [fusedLocationClient].
     */
    private fun removeLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }


    /**
     * Create new notification instance with the desired parameters.
     *
     * @param contentText Text to display in the notification. Default value is taken from the
     * [R.string.route_service_text] resource.
     * @param silent Do not use any sound or vibration for this notification. Default value is true.
     *
     * @return New foreground [Notification] instance with needed parameters and specified text.
     */
    private fun createForegroundNotification(contentText: String = getString(R.string.route_service_text), silent: Boolean = true): Notification? {
        Log.d(TAG, "createForegroundNotification: $contentText")

        val serviceClosePendingIntent = PendingIntent.getActivity(
                this,
                NOTIFICATION_REQUEST_CODE,
                Intent(this, RouteService::class.java).apply {
//                    this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_INTENT_TYPE, ServiceAction.STOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        // The PendingIntent to launch activity.
        val activityPendingIntent = PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_MAIN_INTENT_TYPE, MainActivity.IntentType.TO_MAP_FRAGMENT)
                },
                0
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID).apply {
            this.addAction(R.drawable.ic_baseline_launch_24, getString(R.string.open),
                    activityPendingIntent)
            this.addAction(R.drawable.ic_baseline_close_24, getString(R.string.cancel),
                    serviceClosePendingIntent)
            this.setContentIntent(activityPendingIntent)
            this.setContentText(contentText)
            this.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            this.setContentTitle(getText(R.string.route_service_title))
            this.setOngoing(true)
            this.setSmallIcon(R.drawable.osm_ic_center_map)
            this.setTicker(getText(R.string.route_service_ticker))

            if (!silent) this.setDefaults(NotificationCompat.DEFAULT_SOUND.or(NotificationCompat.DEFAULT_VIBRATE))

            // Set the Channel ID for Android O.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                this.setChannelId(CHANNEL_ID)
                this.priority = NotificationManager.IMPORTANCE_HIGH
            }

            // setWhen(System.currentTimeMillis())
        }

        return builder.build()
    }


    /**
     * Create a new notification instance with the latest [targetCalculatedPoint] data displayed
     * in the text content and notify user using the [notificationManager].
     *
     * @param targetCalculatedPoint The [findClosestWaypoint] result to display in the notification.
     * @param indexInput The target waypoint's index in the waypoints array.
     */
    private fun updateNotification(targetCalculatedPoint: CalculatedPoint? = null, indexInput: Int? = null) {
        val point = targetCalculatedPoint ?: routeDataBus.targetWaypointCalculatedDistance.value
        val index = indexInput ?: routeDataBus.targetWaypointIndex.value

        val notificationText = "Следуйте к следующей путевой точке. " +
                "${index}/${activeRoute?.waypoints?.size} \n" +
                "Расстояние: ${point?.distanceResult?.distance?.round(2)}м. \n" +
                "Направление: ${bearingToAzimuth(point?.distanceResult?.finalBearing)?.round(1)}°"

        notificationManager.notify(
                NOTIFICATION_IDENTIFIER,
                createForegroundNotification(notificationText)
        )
    }


    /**
     * Clear all liveData values in the [routeDataBus].
     */
    private fun clearBusData() {
        routeDataBus.clear()
    }


    companion object {
        private const val TAG = "RouteService"
        private const val NOTIFICATION_IDENTIFIER = 121212
        private const val NOTIFICATION_REQUEST_CODE = 420
        private const val CHANNEL_ID = "TourRouteControllerService"
        private const val CHANNEL_NAME = "Foreground Service Channel"

        private const val EXTRA_INTENT_TYPE = "EXTRA_INTENT_TYPE"

        private const val PENDING_INTENT_NOTIFICATION_CODE = 0

        private const val WAYPOINT_ENTER_RADIUS = 15f

        /**
         * Send the [ServiceAction.START] command to the service in order to launch it.
         * Should be called once as the route has been activated.
         *
         * @param context Context of the caller.
         */
        fun startService(context: Context) {
            val startIntent = Intent(context, RouteService::class.java).apply {
                putExtra(EXTRA_INTENT_TYPE, ServiceAction.START)
            }
            ContextCompat.startForegroundService(context, startIntent)
        }

        /**
         * Send the [ServiceAction.STOP] command to the service in order to stop it.
         * Usually called upon the deactivation of the route.
         *
         * @param context Context of the caller.
         */
        fun stopService(context: Context) {
            val stopIntent = Intent(context, RouteService::class.java).apply {
                putExtra(EXTRA_INTENT_TYPE, ServiceAction.STOP)
            }
            context.startService(stopIntent)
        }

        /**
         * Send the [ServiceAction.NEXT_WAYPOINT] command to the service in order to iterate
         * the active waypoint forward by one.
         *
         * @param context Context of the caller.
         */
        fun nextWaypoint(context: Context) {
            val nextWaypointIntent = Intent(context, RouteService::class.java).apply {
                putExtra(EXTRA_INTENT_TYPE, ServiceAction.NEXT_WAYPOINT)
            }
            context.startService(nextWaypointIntent)
        }

        /**
         * Send the [ServiceAction.PREVIOUS_WAYPOINT] command to the service in order to iterate
         * the active waypoint backwards by one.
         *
         * @param context Context of the caller.
         */
        fun prevWaypoint(context: Context) {
            val prevWaypointIntent = Intent(context, RouteService::class.java).apply {
                putExtra(EXTRA_INTENT_TYPE, ServiceAction.PREVIOUS_WAYPOINT)
            }
            context.startService(prevWaypointIntent)
        }

        enum class ServiceAction {
            START,
            STOP,
            NEXT_WAYPOINT,
            PREVIOUS_WAYPOINT
        }
    }
}