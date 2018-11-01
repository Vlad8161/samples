package company.domain.ui.fragment

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.support.v4.app.Fragment
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.util.DiffUtil
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.json.JSONException
import org.json.JSONObject
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import smartdevelop.ir.eram.showcaseviewlib.GuideView
import therussianmob.angelbrinks.*
import therussianmob.angelbrinks.AppConfig.ADMINPOSTS
import therussianmob.angelbrinks.AppConfig.CREATE_POST
import therussianmob.angelbrinks.core.FeedStorage
import therussianmob.angelbrinks.network.NetworkManager
import therussianmob.angelbrinks.ui.activity.*
import therussianmob.angelbrinks.ui.adapter.*
import therussianmob.angelbrinks.ui.data.Feed
import therussianmob.angelbrinks.ui.data.FooterStub
import java.util.*

class WallFeedFragment : Fragment() {
    private var mAdapter: CompositeAdapter? = null
    private lateinit var mVideoDelegateAdapter: VideoDelegateAdapter
    private lateinit var mImageDelegateAdapter: ImageDelegateAdapter
    private lateinit var mGridDelegateAdapter: GridDelegateAdapter
    private lateinit var mStubDelegateAdapter: StubDelegateAdapter
    private lateinit var mPodcastsListDelegateAdapter: PodcastsListDelegateAdapter
    private lateinit var mViewModel: ViewModel
    private lateinit var mLayoutManager: RecyclerView.LayoutManager
    private lateinit var mTitle: String
    private var mWallId: Int = 0
    private var mPresentationType: Int = 0
    private var mReports: Boolean = false
    private var mCanCreate: Boolean = false
    private var subscription: Subscription? = null
    private var mStubHeight: Int = 0
    private var mTypeWall: String = ""

    companion object {
        const val PAGE_SIZE = 10
        fun createFragment(wallId: Int, title: String, type: String, presentationType: Int,
                           reports: Boolean, canCreate: Boolean): Fragment = WallFeedFragment().also {
            it.arguments = Bundle()
                    .also { it.putInt("wallId", wallId) }
                    .also { it.putString("title", title) }
                    .also { it.putString("type", type) }
                    .also { it.putInt("presentationType", presentationType) }
                    .also { it.putBoolean("reports", reports) }
                    .also { it.putBoolean("canCreate", canCreate) }
        }
    }

    lateinit var fragmentStarFeedBtnCreate: Button
    lateinit var fragmentStarFeedRecyclerView: RecyclerView
    lateinit var fragmentStarFeedRefreshLayout: SwipeRefreshLayout
    lateinit var fragmentStarFeedTvComingSoon: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_star_feed, null)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentStarFeedBtnCreate = view.findViewById(R.id.fragmentStarFeedBtnCreate)
        fragmentStarFeedRecyclerView = view.findViewById(R.id.fragmentStarFeedRecyclerView)
        fragmentStarFeedRefreshLayout = view.findViewById(R.id.fragmentStarFeedRefreshLayout)
        fragmentStarFeedTvComingSoon = view.findViewById(R.id.fragmentStarFeedTvComingSoon)

        mWallId = arguments?.getInt("wallId") ?: AppConfig.ID_ON_THE_BRINKS_FEED
        mPresentationType = arguments?.getInt("presentationType") ?: 0
        mTitle = arguments?.getString("title") ?: AppConfig.ON_THE_BRINKS_TITLE
        mTypeWall = arguments?.getString("type") ?: AppConfig.ON_THE_BRINKS_TITLE
        mCanCreate = arguments?.getBoolean("canCreate") == true
        mReports = arguments?.getBoolean("reports") == true

        setResource()

        mViewModel = (activity as MainActivity).router.getViewModel(mWallId) { ViewModel() }

        mReports = mTypeWall != AppConfig.QUESTIONS && mTypeWall != ADMINPOSTS
        if (AppInfo.isAdmin) {
            mReports = true
        }

        mPodcastsListDelegateAdapter = PodcastsListDelegateAdapter(activity!!, mTypeWall, mTitle, mWallId.toString(), { _, feed ->
            try {
                val props = JSONObject()
                props.put("wall", mTitle)
                Mixpanel.getInstance().track("clickPodcastsPost", props)
            } catch (e: JSONException) {
                e.printStackTrace()
            }

            try {
                val props = JSONObject()
                props.put("wall", mTitle)
                Mixpanel.getInstance().track("clickPost", props)
            } catch (e: JSONException) {
                e.printStackTrace()
            }

            PostActivity.startActivity(activity!!, mWallId, feed.id, mReports, mTypeWall, mTitle, -1)
        })

        mVideoDelegateAdapter = VideoDelegateAdapter(activity!!, mReports, mTypeWall, mTitle, mWallId.toString(), false, { pos, selfContent, feed ->
            mAdapter?.removeItem(pos)
            if (selfContent) {
                NetworkManager.deletePost(mTypeWall, feed.id.toString()).subscribe()
            } else {
                NetworkManager.reportArticle(feed.id.toString()).subscribe()
            }
        }, { _, feed ->
            try {
                val props = JSONObject()
                props.put("wall", mTitle)
                Mixpanel.getInstance().track("clickVideoPost", props)
            } catch (e: JSONException) {
                e.printStackTrace()
            }

            try {
                val props = JSONObject()
                props.put("wall", mTitle)
                Mixpanel.getInstance().track("clickPost", props)
            } catch (e: JSONException) {
                e.printStackTrace()
            }

            PostActivity.startActivity(activity!!, mWallId, feed.id, mReports, mTypeWall, mTitle, -1)
        }, { _, user ->
            if (user.id != AppInfo.id && user.id != 0L)
                UserActivity.startActivity(activity!!, user.username, user.avatar, user.id.toString())
        })

        mImageDelegateAdapter = ImageDelegateAdapter(activity!!, mReports, mTypeWall, mTitle, mWallId.toString(), { pos, selfContent, feed ->
            mAdapter?.removeItem(pos)
            if (selfContent) {
                NetworkManager.deletePost(mTypeWall, feed.id.toString()).subscribe()
            } else {
                NetworkManager.reportArticle(feed.id.toString()).subscribe()
            }
        }, { _, feed ->
            try {
                val props = JSONObject()
                props.put("wall", mTitle)
                Mixpanel.getInstance().track("clickPhotoPost", props)
            } catch (e: JSONException) {
                e.printStackTrace()
            }

            try {
                val props = JSONObject()
                props.put("wall", mTitle)
                Mixpanel.getInstance().track("clickPost", props)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            PostActivity.startActivity(activity!!, mWallId, feed.id, mReports, mTypeWall, mTitle, -1)
        }, { _, user ->
            if (user.id != AppInfo.id && user.id != 0L)
                UserActivity.startActivity(activity!!, user.username, user.avatar, user.id.toString())
        })

        mGridDelegateAdapter = GridDelegateAdapter(activity!!, mTitle) { _, feed ->
            try {
                val props = JSONObject()
                props.put("wall", mTitle)
                if (feed.mediatype.equals("image"))
                    Mixpanel.getInstance().track("clickPhotoPost", props)
                else
                    Mixpanel.getInstance().track("clickVideoPost", props)
            } catch (e: JSONException) {
                e.printStackTrace()
            }

            try {
                val props = JSONObject()
                props.put("wall", mTitle)
                Mixpanel.getInstance().track("clickPost", props)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            PostActivity.startActivity(activity!!, mWallId, feed.id, mReports, mTypeWall, mTitle, -1)
        }

        mStubDelegateAdapter = StubDelegateAdapter()

        mLayoutManager = LinearLayoutManager(activity)

        fragmentStarFeedRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = mLayoutManager
                val visibleItemCount = mLayoutManager.childCount
                val totalItemCount = mLayoutManager.itemCount
                val firstVisibleItemPosition = when (layoutManager) {
                    is LinearLayoutManager -> layoutManager.findFirstVisibleItemPosition()
                    is GridLayoutManager -> layoutManager.findFirstVisibleItemPosition()
                    else -> 0
                }

                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                        && totalItemCount >= PAGE_SIZE) {
                    loadMoreItems()
                }
            }
        })

        fragmentStarFeedRefreshLayout.setOnRefreshListener { onRefresh() }

        fragmentStarFeedBtnCreate.visibility = if (AppInfo.isAdmin || mCanCreate) View.VISIBLE else View.GONE
        if (AppConfig.PODCASTS == mTypeWall && AppInfo.isAdmin) {
            fragmentStarFeedBtnCreate.visibility = View.GONE
        }

        val feedList = FeedStorage.getWallFeeds(mWallId)
        if (feedList == null) {
            fragmentStarFeedRefreshLayout.isRefreshing = true
            loadFirstPage()
        } else {
            initAdapter(FeedStorage.getWallType(mWallId))
            mAdapter?.addItems(feedList)
            if (mCanCreate) {
                mAdapter?.addItem(FooterStub(mStubHeight, false))
            }
            val scrollPosition = mViewModel.scrollPosition
            if (scrollPosition != null) {
                mLayoutManager.onRestoreInstanceState(scrollPosition)
            }
        }

        when (mTypeWall) {
            AppConfig.QUESTIONS -> {
                if (!AppInfo.isIntroAskMe) {
                    introAskMe()
                }
                fragmentStarFeedBtnCreate.setText(R.string.ask_a_question)
                fragmentStarFeedBtnCreate.setOnClickListener {
                    startActivity(Intent(activity!!, CreateQuestionActivity::class.java)
                            .putExtra("wallId", mWallId)
                            .putExtra("typeWall", mTypeWall))
                }
            }
            else -> {
                fragmentStarFeedBtnCreate.setText(R.string.create_post)
                fragmentStarFeedBtnCreate.setOnClickListener {
                    startActivityForResult(Intent(activity!!, CreatePostActivity::class.java)
                            .putExtra("wallId", mWallId)
                            .putExtra("typeWall", mTypeWall), CREATE_POST)
                }
            }
        }

        mStubHeight = (activity!!.resources.displayMetrics.density * 88).toInt()
    }

    private fun setResource() {
        if (!AppInfo.isDefaultResources) {
            fragmentStarFeedBtnCreate.background = ResourceManager.instance.makeSelector()
            fragmentStarFeedBtnCreate.setTextColor(Color.parseColor(ResourceManager.instance.colorsMap.textButton))
            fragmentStarFeedBtnCreate.typeface = ResourceManager.instance.fontsMap[ResourceManager.F_BOLD]
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK)
            if (requestCode == CREATE_POST) {
                onRefresh()
//                val layoutManager = mLayoutManager
//                layoutManager.scrollToPosition(0)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        subscription?.unsubscribe()
        mViewModel.scrollPosition = mLayoutManager.onSaveInstanceState()
        mVideoDelegateAdapter.release()
    }

    override fun onStart() {
        super.onStart()
        val adapter = mAdapter ?: return
        val oldData = adapter.data
        val newData = ArrayList<Any>(FeedStorage.getWallFeeds(mWallId) ?: return)
        if (mCanCreate) {
            newData.add(FooterStub(mStubHeight, false))
        }
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val item1 = oldData[oldItemPosition]
                val item2 = newData[newItemPosition]
                if (item1::class.java != item2::class.java) {
                    return false
                }

                return when {
                    item1 is Feed && item2 is Feed -> item1.id == item2.id
                    else -> true
                }
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldData[oldItemPosition] == newData[newItemPosition]
            }

            override fun getOldListSize(): Int = oldData.size

            override fun getNewListSize(): Int = newData.size
        })
        adapter.data.clear()
        adapter.data.addAll(newData)
        diffResult.dispatchUpdatesTo(adapter)
        adapter.data.forEachIndexed { index, feed ->
            feed as Feed
            adapter.notifyItemChanged(index, feed.commentCount)
        }
    }

    override fun onStop() {
        super.onStop()
        mAdapter?.release()
    }

    private fun introAskMe() {
        GuideView.Builder(activity!!)
                .setContentText(resources.getString(R.string.introAskMeAnything1))
                .setGravity(GuideView.Gravity.auto)
                .setDismissType(GuideView.DismissType.anywhere)
                .setTargetView(fragmentStarFeedBtnCreate)
                .setContentTextSize(14)
                .setGuideListener {
                    AppInfo.isIntroAskMe = true
                }
                .build()
                .show()

    }

    private fun onRefresh() {
        FeedStorage.clearWallFeeds(mWallId)
        mViewModel.scrollPosition = null
        mViewModel.isLastPage = false
        mAdapter?.clearItems()
        loadFirstPage()
    }

    private fun loadFirstPage() {
        subscription?.unsubscribe()
        subscription = NetworkManager.getFeedList(mWallId, mTypeWall, "0")
                .observeOn(AndroidSchedulers.mainThread())
                .doOnUnsubscribe { subscription = null }
                .doOnUnsubscribe { fragmentStarFeedRefreshLayout.isRefreshing = false }
                .subscribe({
                    if (it.isNotEmpty()) {
                        fragmentStarFeedRecyclerView.visibility = View.VISIBLE
                        fragmentStarFeedTvComingSoon.visibility = View.GONE
                        val feeds = it.map { Feed(it) }
                        FeedStorage.addWallFeeds(mWallId, feeds, mPresentationType)
                        initAdapter(mPresentationType)
                        mAdapter?.addItems(feeds)
                        if (mCanCreate) {
                            mAdapter?.addItem(FooterStub(mStubHeight, false))
                        }
                    } else {
                        mViewModel.isLastPage = true
                        fragmentStarFeedRecyclerView.visibility = View.GONE
                        fragmentStarFeedTvComingSoon.visibility = View.VISIBLE
                    }
                }, {
                    it.printStackTrace()
                })
    }

    private fun loadMoreItems() {
        if (subscription != null) {
            return
        }

        val feedList = FeedStorage.getWallFeeds(mWallId) ?: return
        if (!mViewModel.isLastPage) {
            subscription = NetworkManager.getFeedList(mWallId, mTypeWall, (feedList.size).toString())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnUnsubscribe { subscription = null }
                    .doOnUnsubscribe { fragmentStarFeedRefreshLayout.isRefreshing = false }
                    .concatMap { Observable.from(it) }
                    .map { Feed(it) }
                    .toList()
                    .subscribe({
                        if (!it.isEmpty()) {
                            FeedStorage.addWallFeeds(mWallId, it)
                            val adapter = mAdapter ?: return@subscribe
                            if (mCanCreate && adapter.itemCount > 0) {
                                mAdapter?.insertItems(adapter.itemCount - 1, it)
                            } else {
                                mAdapter?.addItems(it)
                            }
                        } else {
                            mViewModel.isLastPage = true
                        }
                    }, {
                        it.printStackTrace()
                    })
        }
    }

    private fun initAdapter(wallType: Int) {
        mAdapter?.release()
        if (wallType == 0) {
            mAdapter = CompositeAdapter()
                    .also { it.addAdapter(mVideoDelegateAdapter) }
                    .also { it.addAdapter(mImageDelegateAdapter) }
                    .also { it.addAdapter(mPodcastsListDelegateAdapter) }
                    .also { if (mCanCreate) it.addAdapter(mStubDelegateAdapter) }
            fragmentStarFeedRecyclerView.adapter = mAdapter
            fragmentStarFeedRecyclerView.layoutManager = LinearLayoutManager(activity!!)
                    .also { mLayoutManager = it }
        } else {
            mAdapter = CompositeAdapter()
                    .also { it.addAdapter(mGridDelegateAdapter) }
                    .also { if (mCanCreate) it.addAdapter(mStubDelegateAdapter) }
            fragmentStarFeedRecyclerView.adapter = mAdapter
            fragmentStarFeedRecyclerView.layoutManager = GridLayoutManager(activity!!, 3)
                    .also { mLayoutManager = it }
                    .also {
                        it.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                            override fun getSpanSize(position: Int): Int = when {
                                position == 0 -> 3
                                mCanCreate && position == ((mAdapter?.size ?: 0) - 1) -> 3
                                else -> 1
                            }
                        }
                    }
        }
    }

    class ViewModel {
        var scrollPosition: Parcelable? = null
        var isLastPage: Boolean = false
    }
}
