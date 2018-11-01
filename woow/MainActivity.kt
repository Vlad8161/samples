package company.project.ui.activity

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewPager
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.view.View
import android.widget.TextView
import dev.klippe.karma.*
import dev.klippe.karma.core.AuthModel
import dev.klippe.karma.core.PhotoModel
import dev.klippe.karma.core.ProfileModel
import dev.klippe.karma.dataobjects.UserProfile
import dev.klippe.karma.ui.adapter.MainActivityPagerAdapter
import dev.klippe.karma.ui.customview.DTImageView
import dev.klippe.karma.ui.customview.RoundedImageView
import dev.klippe.karma.ui.fragment.MapFragment
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main_drawer.*
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject
import rx.subscriptions.CompositeSubscription
import javax.inject.Inject

class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var mAuthModel: AuthModel

    @Inject
    lateinit var mProfileModel: ProfileModel

    @Inject
    lateinit var mPhotoModel: PhotoModel

    private lateinit var mAdapter: MainActivityPagerAdapter
    private lateinit var mToggle: ActionBarDrawerToggle
    private lateinit var ivAvatar: RoundedImageView
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvLogin: TextView
    private lateinit var tvKarma: TextView
    private lateinit var ivKarmaCircle: View

    private val mCompositeSubscription = CompositeSubscription()
    private var mUserProfileSubscription: Subscription? = null
    private var mSelectedTypeSubscription: Subscription? = null

    private var mProfileObservableSubject = BehaviorSubject.create<Observable<UserProfile>>()

    private val mUiHandler = Handler(Looper.getMainLooper())

    private var mMapFragment: MapFragment? = null

    init {
        App.appComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(activityMainToolbar)
        mToggle = ActionBarDrawerToggle(
                this, activityMainDrawerLayout, activityMainToolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        )
        activityMainDrawerLayout.addDrawerListener(mToggle)
        mToggle.syncState()
        activityMainToolbar.setNavigationOnClickListener { onNavigationBurgerClick() }
        supportActionBar?.setDisplayShowTitleEnabled(false)

        mAdapter = MainActivityPagerAdapter(supportFragmentManager)
//        activityMainNavigationView.setNavigationItemSelectedListener { onNavigationItemClicked(it) }

        val bgImageView = DTImageView(this)
        val size = Point()
        windowManager.defaultDisplay.getSize(size)
        bgImageView.imageBitmap = Bitmap.createScaledBitmap(
                BitmapFactory.decodeResource(resources, R.drawable.bg_login_dark),
                size.x, size.y, true)
        activityMainNavigationView.addView(bgImageView, 0)
        activityMainViewPager.adapter = mAdapter

        activityMainViewPager.addOnPageChangeListener(mPageChangeListener)
        activityMainAvatar.setOnClickListener {
            if (mAdapter.count > 1) {
                activityMainViewPager.currentItem = 1
            }
        }

        activityMainBtnLoadDriver.setOnClickListener {
            mMapFragment?.loadDrivers()
        }
        activityMainBtnLoadPassenger.setOnClickListener {
            mMapFragment?.loadPassengers()
        }
        activityMainBtnLoadSender.setOnClickListener {
            mMapFragment?.loadSenders()
        }

        ivAvatar = findViewById(R.id.activityMainDrawerHeaderAvatar)
        tvEmail = findViewById(R.id.activityMainDrawerHeaderEmail)
        tvName = findViewById(R.id.activityMainDrawerHeaderName)
        tvLogin = findViewById(R.id.activityMainDrawerHeaderBtnLogin)
        tvKarma = findViewById(R.id.activityMainDrawerHeaderKarma)
        ivKarmaCircle = findViewById(R.id.activityMainDrawerHeaderKarmaCircle)
        ivAvatar.setOnClickListener { onClickHeaderAvatar() }

        activityMainDrawerBtnCreateEvent.setOnClickListener {
            startActivity(Intent(this@MainActivity, CreateEventActivity::class.java))
            activityMainDrawerLayout.closeDrawers()
        }
        activityMainDrawerBtnCreateListTickets.setOnClickListener {
            startActivity(Intent(this@MainActivity, ListTicketActivity::class.java))
            activityMainDrawerLayout.closeDrawers()
        }
        activityMainDrawerBtnLogout.setOnClickListener {
            mAuthModel.clearCredentials()
            val intent = Intent(applicationContext, AuthActivity::class.java)
            intent.flags = FLAG_ACTIVITY_NEW_TASK
            applicationContext?.startActivity(intent)
            activityMainDrawerLayout.closeDrawers()
            finish()
        }

        activityMainDrawerBtnAbout.setOnClickListener {
            startActivity(Intent(this@MainActivity, AboutCompanyImage::class.java))
            activityMainDrawerLayout.closeDrawers()
        }

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.addDefaultNetworkActiveListener(onNetworkActiveListener)

        activityMainStatusBarBg.layoutParams
                .also { it.height = getStatusBarHeight() }
                .also { activityMainStatusBarBg.layoutParams = it }

        tvLogin.setOnClickListener { onLogInClicked() }
        activityMainBtnLogin.setOnClickListener { onLogInClicked() }

        if (activityMainViewPager.currentItem == 0) {
            setPanelVisibility(0.0f)
        } else {
            setPanelVisibility(1.0f)
        }

        prepareProfile()

        val profileObservableSubscription = mProfileObservableSubject.subscribe { it ->
            mUserProfileSubscription?.unsubscribe()
            mUserProfileSubscription = it
                    .doOnNext { log("MainActivity: profile loaded subscriber") }
                    .doOnNext { mUiHandler.post { onProfileLoaded(it) } }
                    .onErrorResumeNext { Observable.empty() }
                    .filter { it.photoUrl != null }
                    .map { it.photoUrl as String }
                    .concatMap { mPhotoModel.loadPhoto(it) }
                    .observeOn(AndroidSchedulers.mainThread())
                    .onErrorResumeNext { Observable.empty() }
                    .subscribe { onAvatarLoaded(it) }
        }
        mCompositeSubscription.add(profileObservableSubscription)

        activityMainDrawerBtnListEvents.setOnClickListener {
            startActivity(Intent(this@MainActivity, ListEventsActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.removeDefaultNetworkActiveListener(onNetworkActiveListener)

        mCompositeSubscription.clear()
    }

    fun onMapFragmentViewCreated(mapFragment: MapFragment) {
        mSelectedTypeSubscription?.unsubscribe()
        mSelectedTypeSubscription = mapFragment.getSelectedTypeObservable()
                .subscribe {
                    activityMainIvLoadDriver.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.white))
                    activityMainBtnLoadDriverUnderscore.visibility = View.INVISIBLE
                    activityMainIvLoadPassenger.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.white))
                    activityMainBtnLoadPassengerUnderscore.visibility = View.INVISIBLE
                    activityMainIvLoadSender.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.white))
                    activityMainBtnLoadSenderUnderscore.visibility = View.INVISIBLE
                    when (it) {
                        MapFragment.EVENT_TYPE_DRIVER -> {
                            activityMainIvLoadDriver.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.colorAccent))
                            activityMainBtnLoadDriverUnderscore.visibility = View.VISIBLE
                        }
                        MapFragment.EVENT_TYPE_PASSENGER -> {
                            activityMainIvLoadPassenger.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.colorAccent))
                            activityMainBtnLoadPassengerUnderscore.visibility = View.VISIBLE
                        }
                        MapFragment.EVENT_TYPE_SENDER -> {
                            activityMainIvLoadSender.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.colorAccent))
                            activityMainBtnLoadSenderUnderscore.visibility = View.VISIBLE
                        }
                    }
                }
        mMapFragment = mapFragment
    }

    fun onMapFragmentViewDestroyed() {
        mSelectedTypeSubscription?.unsubscribe()
        mSelectedTypeSubscription = null
        mMapFragment = null
    }

    fun getProfileObservable(): Observable<Observable<UserProfile>> =
            mProfileObservableSubject

    fun loadUserProfile() {
        mProfileObservableSubject.onNext(mProfileModel.getMyProfile())
    }

    override fun onBackPressed() {
        val currItem = activityMainViewPager.currentItem
        if (currItem > 0) {
            activityMainViewPager.setCurrentItem(currItem - 1, true)
        }
    }

    private fun onNavigationBurgerClick() {
        val currItem = activityMainViewPager.currentItem
        if (currItem > 0) {
            activityMainViewPager.setCurrentItem(currItem - 1, true)
        } else {
            activityMainDrawerLayout.openDrawer(Gravity.START)
        }
    }

    private val mPageChangeListener = object : ViewPager.OnPageChangeListener {
        override fun onPageScrollStateChanged(state: Int) {
            this@MainActivity.onPageScrollStateChanged(state)
        }

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            this@MainActivity.onPageScrolled(position, positionOffset)
        }

        override fun onPageSelected(position: Int) {
            this@MainActivity.onPageSelected(position)
        }
    }

    private fun onPageScrollStateChanged(state: Int) {
        if (state == ViewPager.SCROLL_STATE_IDLE) {
            when (activityMainViewPager.currentItem) {
                0 -> {
                    activityMainDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    activityMainDrawerLayout.addDrawerListener(mToggle)
                }
                1 -> activityMainDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
        } else {
            activityMainDrawerLayout.removeDrawerListener(mToggle)
        }
    }

    private fun onPageScrolled(position: Int, positionOffset: Float) {
        if (position == 0) {
            setPanelVisibility(positionOffset)
        } else if (position == 1 && positionOffset < 0) {
            setPanelVisibility(1.0f - positionOffset)
        } else {
            setPanelVisibility(1.0f)
        }
    }

    private fun onPageSelected(position: Int) {
        if (position == 0) {
            mToggle.isDrawerSlideAnimationEnabled = true
            setPanelVisibility(0f)
        } else {
            setPanelVisibility(1f)
        }
    }

    private fun setPanelVisibility(level: Float) {
        val toolbarHeight = (activityMainToolbarBackground.height + getStatusBarHeight())
        val actionPanelHeight = activityMainActionPanel.height
        mToggle.drawerArrowDrawable.progress = level
        activityMainToolbarBackground.translationY = -(level * toolbarHeight)
        activityMainToolbarShadow.translationY = -(level * toolbarHeight)
        activityMainStatusBarBg.translationY = -(level * toolbarHeight)
        activityMainLogoLayout.translationY = -(level * toolbarHeight)
        activityMainActionPanel.translationY = level * actionPanelHeight
        activityMainActionPanelShadow.translationY = level * actionPanelHeight
    }

    private fun onClickHeaderAvatar() {
        activityMainDrawerLayout.closeDrawers()
        if (mAdapter.count > 1) {
            activityMainViewPager.currentItem = 1
        }
    }

    private fun prepareProfile() {
        mAdapter.hasProfile = true
        activityMainAvatar.visibility = View.GONE
        activityMainAvatar.setImageDrawable(null)
        loadUserProfile()
        tvLogin.visibility = View.INVISIBLE
        activityMainDrawerBtnLogout.visibility = View.VISIBLE
        activityMainBtnLogin.visibility = View.INVISIBLE
    }

    private fun onProfileLoaded(profile: UserProfile) {
        log("MainActivity: onProfileLoaded")
        tvName.text = profile.name ?: "Unknown"
        tvEmail.text = profile.email ?: "Unknown"
        tvKarma.text = profile.karma.toString()
        tvName.visibility = View.VISIBLE
        tvEmail.visibility = View.VISIBLE
        tvKarma.visibility = View.VISIBLE
        ivKarmaCircle.visibility = View.VISIBLE
        activityMainAvatar.name = profile.name ?: ""
        log("set ava null")
        activityMainAvatar.alpha = 0f
        activityMainAvatar.visibility = View.VISIBLE
        activityMainAvatar.animate()
                .setDuration(600)
                .alpha(1f)
                .start()
    }

    private fun onAvatarLoaded(avatar: Bitmap) {
        log("MainActivity: onAvatarLoaded")
        ivAvatar.setImageBitmap(avatar.scaleByAspect(100))
        ivAvatar.visibility = View.VISIBLE
        activityMainAvatar.setImageBitmap(avatar.scaleByAspect(100))
    }

    private val onNetworkActiveListener = ConnectivityManager.OnNetworkActiveListener {
        /*        if (mAuthModel.isAuthenticated()) {
                    cancelUserProfileLoading()
                    loadUserProfile()
                }*/
    }

    private fun onLogInClicked() {
        activityMainDrawerLayout.closeDrawers()
    }
}
