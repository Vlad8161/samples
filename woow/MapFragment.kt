package company.project.ui.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.BottomSheetBehavior
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.gson.Gson
import dev.klippe.karma.App
import dev.klippe.karma.R
import dev.klippe.karma.core.EventModel
import dev.klippe.karma.dataobjects.SearchDriver
import dev.klippe.karma.dataobjects.SearchPassenger
import dev.klippe.karma.dataobjects.SearchSender
import dev.klippe.karma.getStatusBarHeight
import dev.klippe.karma.showToast
import dev.klippe.karma.ui.MarkerAnimator
import dev.klippe.karma.ui.activity.DetailedPassengerSearchActivity
import dev.klippe.karma.ui.activity.DetailedSearchDriverActivity
import dev.klippe.karma.ui.activity.DetailedSenderSearchActivity
import dev.klippe.karma.ui.activity.MainActivity
import dev.klippe.karma.ui.adapter.SearchEventsAdapter
import dev.klippe.karma.ui.adapter.SearchInfoWindowAdapter
import dev.klippe.karma.ui.base.BaseFragment
import kotlinx.android.synthetic.main.fragment_new_map.*
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject
import javax.inject.Inject

class MapFragment : BaseFragment(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener, GoogleMap.OnInfoWindowClickListener {
    override fun onInfoWindowClick(p0: Marker?) {
        p0?.showInfoWindow()
    }

    override fun onMarkerClick(p0: Marker?): Boolean {

        return true
    }

    @Inject
    lateinit var mEventsModel: EventModel

    companion object {
        private const val DEFAULT_ZOOM = 16f
        const val EVENT_TYPE_PASSENGER = 0
        const val EVENT_TYPE_SENDER = 1
        const val EVENT_TYPE_DRIVER = 3
    }

    private lateinit var mLocationManager: LocationManager
    private lateinit var mBottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var mAdapter: SearchEventsAdapter
    private lateinit var mInfoWindowAdapter: SearchInfoWindowAdapter

    private var mMap: GoogleMap? = null
    private var mEventsSubscription: Subscription? = null

    private val mPrefs: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(activity) }
    private var mSelectedTypeObservable = BehaviorSubject.create<Int?>()
    private val mMarkers: MutableList<Marker> = ArrayList()

    init {
        App.appComponent.inject(this)
    }

    @SuppressLint("InflateParams")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_new_map, null)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentMapMapView?.onCreate(savedInstanceState)
        fragmentMapMapView.getMapAsync(this)

        mBottomSheetBehavior = BottomSheetBehavior.from(fragmentMapContentRecyclerView)
        mBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        mLocationManager = activity!!.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        mAdapter = SearchEventsAdapter(activity!!, {
            DetailedSearchDriverActivity.startActivity(context!!, it.eventId)
        }, {
            DetailedSenderSearchActivity.startActivity(context!!, it.eventId)
        }, {
            showToast("Click on user event (event id = " + it.id + ")")
        }, {
            DetailedPassengerSearchActivity.startActivity(context!!, it.eventId)
        }, true)
        fragmentMapContentRecyclerView.adapter = mAdapter
        val params = fragmentMapContentRecyclerView.layoutParams
        params.height = activity?.windowManager?.defaultDisplay?.height!! - context?.getStatusBarHeight()!! - (resources.displayMetrics.density * 56).toInt()
        fragmentMapContentRecyclerView.layoutParams = params
        fragmentMapContentRecyclerView.layoutManager = LinearLayoutManager(activity)

        mInfoWindowAdapter = SearchInfoWindowAdapter(activity!!)

        mSelectedTypeObservable = BehaviorSubject.create()
        mSelectedTypeObservable.onNext(null)
        mSelectedTypeObservable.subscribe { reloadEvents() }

        (activity as MainActivity).onMapFragmentViewCreated(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fragmentMapMapView?.onDestroy()

        mEventsSubscription?.unsubscribe()

        mSelectedTypeObservable.onCompleted()

        (activity as MainActivity).onMapFragmentViewDestroyed()
    }

    override fun onResume() {
        super.onResume()
        fragmentMapMapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        fragmentMapMapView?.onPause()
    }

    override fun onStart() {
        super.onStart()
        fragmentMapMapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        fragmentMapMapView?.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        fragmentMapMapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        fragmentMapMapView?.onSaveInstanceState(outState)
    }

    fun getSelectedTypeObservable(): Observable<Int?> =
            mSelectedTypeObservable

    fun loadDrivers() {
        if (mBottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            mBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        mSelectedTypeObservable.onNext(EVENT_TYPE_DRIVER)
    }

    fun loadPassengers() {
        if (mBottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            mBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        mSelectedTypeObservable.onNext(EVENT_TYPE_PASSENGER)
    }

    fun loadSenders() {
        if (mBottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            mBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        mSelectedTypeObservable.onNext(EVENT_TYPE_SENDER)
    }

    fun clearLoading() {
        mSelectedTypeObservable.onNext(null)
    }

    private fun reloadEvents() {
        val eventType = mSelectedTypeObservable.value
        if (eventType != null) {
            Log.d("LOGI", "reloading items")
            val bounds = mMap?.projection?.visibleRegion?.latLngBounds // ?: LatLngBounds(LatLng(1.0, 1.0), LatLng(1.0, 1.0))
            if (bounds == null) {
                mAdapter.updateData(listOf(SearchEventsAdapter.ErrorItem("Failed to load data", { reloadEvents() })))
                return
            }

            val eventObservable = when (eventType) {
                EVENT_TYPE_DRIVER -> mEventsModel.findDrivers(
                        bounds.southwest.latitude, bounds.southwest.longitude,
                        bounds.northeast.latitude, bounds.northeast.longitude
                )
                EVENT_TYPE_PASSENGER -> mEventsModel.findPassengers(
                        bounds.southwest.latitude, bounds.southwest.longitude,
                        bounds.northeast.latitude, bounds.northeast.longitude
                )
                EVENT_TYPE_SENDER -> mEventsModel.findSenders(
                        bounds.southwest.latitude, bounds.southwest.longitude,
                        bounds.northeast.latitude, bounds.northeast.longitude
                )
                else -> throw RuntimeException("Unknown event type")
            }

            mAdapter.updateData(listOf(SearchEventsAdapter.ProgressItem()))
            mEventsSubscription?.unsubscribe()
            fragmentMapTvHelp.visibility = View.VISIBLE
            mEventsSubscription = eventObservable
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        Log.d("LOGI", "on reload success. Size = ${it.size}")
                        mAdapter.updateData(it)
                        showMarkers(it)

                        if(it.size == 0){
                            mAdapter.updateData(listOf(SearchEventsAdapter.ErrorItem("No events created yet:(", {reloadEvents()})))
                        }
                    }, {
                        Log.d("LOGI", "on reload error")
                        it.printStackTrace()
                        mAdapter.updateData(listOf(SearchEventsAdapter.ErrorItem("Failed to load data", { reloadEvents() })))
                    })
        } else {
            mEventsSubscription?.unsubscribe()
            mBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(p0: GoogleMap?) {
        mMap = p0
        mMap?.setOnInfoWindowClickListener(this)
        mMap?.moveCamera(CameraUpdateFactory.newCameraPosition(getCameraPosition()))
        mMap?.setOnCameraIdleListener {
            val bounds = mMap?.cameraPosition ?: return@setOnCameraIdleListener
            storeCameraPosition(bounds)
            reloadEvents()
        }

        if (fragmentMapMapView.findViewById<View>(Integer.parseInt("1")) != null) {
            val btnLocation = (fragmentMapMapView.findViewById<View>(Integer.parseInt("1")).parent as View).findViewById<View>(Integer.parseInt("2"))
            val rlp = btnLocation.layoutParams as RelativeLayout.LayoutParams
            rlp.setMargins(0, 100, 36, 0)
        }

        if (fragmentMapMapView.findViewById<View>(Integer.parseInt("1")) != null) {
            val btnLocation = (fragmentMapMapView.findViewById<View>(Integer.parseInt("1")).parent as View).findViewById<View>(Integer.parseInt("5"))
            val rlp = btnLocation.layoutParams as RelativeLayout.LayoutParams
            rlp.setMargins(0, 100, 36, 0)
        }

        askPermission(Manifest.permission.ACCESS_FINE_LOCATION) {
            mMap?.isMyLocationEnabled = true
        }

        mMap?.setMapStyle(MapStyleOptions.loadRawResourceStyle(activity, R.raw.map_style))

        val density = activity!!.resources.displayMetrics.density
        mMap?.setPadding(0, (density * 56).toInt(), 0, (density * 56).toInt())
        mMap?.setInfoWindowAdapter(mInfoWindowAdapter)
    }

    private fun showMarkers(events: List<Any>) {
        val map = mMap ?: return

        val srcBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_marker)
        MarkerAnimator(activity, srcBitmap, 1f, 0f) { it.forEach { it.remove() } }
                .also { it.addMarkers(mMarkers) }
                .also { it.start() }
        mMarkers.clear()

        mInfoWindowAdapter.clear()
        events.mapIndexed { index, it ->
            when (it) {
                is SearchDriver -> map.addMarker(
                        MarkerOptions()
                                .anchor(0.5f, 0.5f)
                                .title(index.toString())
                                .position(it.coordFrom)
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_marker))
                )
                is SearchPassenger -> map.addMarker(
                        MarkerOptions()
                                .anchor(0.5f, 0.5f)
                                .title(index.toString())
                                .position(it.coordFrom)
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_marker))
                )
                is SearchSender -> map.addMarker(
                        MarkerOptions()
                                .anchor(0.5f, 0.5f)
                                .title(index.toString())
                                .position(it.coordFrom)
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_marker))
                )
                else -> null
            }
        }
                .filter { it != null }
                .onEach {
                    if (it != null) {
                        mInfoWindowAdapter.addItem(it)
                    }
                }
                .map { it as Marker }
                .onEach { mMarkers.add(it) }

        MarkerAnimator(activity, srcBitmap, 0f, 1f)
                .also { it.addMarkers(mMarkers) }
                .also { it.start() }
        //mInfoWindowAdapter.setData(mMarkers)
    }

    private fun storeCameraPosition(cameraPosition: CameraPosition) {
        mPrefs.edit()
                .apply { putString("mapfragment_pos", Gson().toJson(cameraPosition.target)) }
                .apply { putFloat("mapfragment_zoom", cameraPosition.zoom) }
                .apply()
    }

    private fun getCameraPosition(): CameraPosition {
        val jsonBounds = mPrefs.getString("mapfragment_pos", null)
        val zoom = mPrefs.getFloat("mapfragment_zoom", DEFAULT_ZOOM)
        return if (jsonBounds != null) {
            val latLng = Gson().fromJson(jsonBounds, LatLng::class.java)
            CameraPosition.fromLatLngZoom(latLng, zoom)
        } else {
            CameraPosition.fromLatLngZoom(LatLng(48.007037, 37.80447), DEFAULT_ZOOM)
        }
    }
}
