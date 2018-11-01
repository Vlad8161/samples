package company.domain.ui.adapter

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.airbnb.lottie.LottieAnimationView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory
import com.google.android.exoplayer2.util.Util
import org.json.JSONException
import org.json.JSONObject
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import therussianmob.angelbrinks.*
import therussianmob.angelbrinks.R
import therussianmob.angelbrinks.core.MusicPlayer
import therussianmob.angelbrinks.network.ExoPlayerCache
import therussianmob.angelbrinks.network.ImageLoader
import therussianmob.angelbrinks.network.data.User
import therussianmob.angelbrinks.ui.customview.CircleImageView
import therussianmob.angelbrinks.ui.data.Feed
import java.text.SimpleDateFormat
import java.util.*

class VideoDelegateAdapter(
        private val context: Context,
        private val reports: Boolean,
        private val typeWall: String,
        private val titleWall: String,
        private val idWall: String,
        private val isPost: Boolean,
        private val onReportClicked: (pos: Int, selfContent: Boolean, feed: Feed) -> Unit,
        private val onItemClick: ((pos: Int, feed: Feed) -> Unit)? = null,
        private val onUserClick: ((pos: Int, user: User) -> Unit)? = null
) : AbstractDelegateAdapter() {
    private val rootViewWidth: Int
    private val density = context.resources.displayMetrics.density
    private var player: SimpleExoPlayer? = null
    private var playingPos: Int? = null
    private var videoHolder: ViewHolder? = null
    private var compositeAdapter: CompositeAdapter? = null
    private var isShowReplay: Boolean = false
    private var selfContent: Boolean = false
    private var isPauseVideo: Boolean = false

    init {
        val size = Point()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getSize(size)
        rootViewWidth = size.x
    }

    override fun setCompositeAdapter(adapter: CompositeAdapter?) {
        compositeAdapter = adapter
    }

    override fun bind(holder: RecyclerView.ViewHolder, item: Any, pos: Int) {
        val feed = item as Feed
        val myHolder = holder as ViewHolder

        setResources(myHolder)

        myHolder.feed = feed
        myHolder.itemPos = pos
        if (feed.description == "" || feed.description == null)
            myHolder.tvText.visibility = GONE
        myHolder.tvText.text = feed.description
        myHolder.tvLikeCount.text = feed.likes.toString()
        myHolder.tvCommentCount.text = feed.commentCount.toString()
        myHolder.itemView.setOnClickListener { onItemClick?.invoke(pos, feed) }
        myHolder.ivAvatar.setOnClickListener { onUserClick?.invoke(pos, feed.user) }
        myHolder.tvName.setOnClickListener { onUserClick?.invoke(pos, feed.user) }
        myHolder.ivReplay.visibility = GONE

        if (pos == 0) {
            myHolder.itemVideoFeedTopLine.visibility = GONE
        }

        val inFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val outFormat = SimpleDateFormat("dd MMMM 'at' hh:mma", Locale.US)
        myHolder.tvDate.text = outFormat.format(inFormat.parse(feed.created))

        myHolder.nicknameChangeSubscription?.unsubscribe()
        if (item.user.id == AppInfo.id) {
            myHolder.nicknameChangeSubscription = AppInfo.loginObservable
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { myHolder.tvName.text = it }
        } else {
            myHolder.tvName.text = feed.user.username

        }

        holder.videoView.layoutParams = holder.videoView.layoutParams
                .also { it.height = (rootViewWidth * feed.ratio).toInt() }

        myHolder.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.sharingMsg))
            var checkTypeWall = if (typeWall == AppConfig.QUESTIONS) "qstns" else typeWall
            shareIntent.putExtra(Intent.EXTRA_TEXT, AppConfig.SHARED_URL + "$idWall/$checkTypeWall/${feed.id}")
            context.startActivity(Intent.createChooser(shareIntent, "Share post"))
        }

        myHolder.ivPlay.setOnClickListener {
            isPauseVideo = false
            startVideo(feed, myHolder, pos)
        }

        myHolder.ivReplay.setOnClickListener {
            isPauseVideo = false
            startVideo(feed, myHolder, pos)
            isShowReplay = false
        }

        myHolder.likeSubscription?.unsubscribe()
        myHolder.likeSubscription = null
        myHolder.likeCountSubscription?.unsubscribe()
        myHolder.likeCountSubscription = null
        myHolder.likeSubscription = feed.likeObservable.subscribe {
            if (!AppInfo.isDefaultResources) {
                myHolder.ivLike.setImageBitmap(
                        if (it)
                            ResourceManager.instance.getImage(ResourceManager.I_LIKED)
                        else
                            ResourceManager.instance.getImage(ResourceManager.I_LIKE))
            } else {
                myHolder.ivLike.setImageResource(if (it) R.drawable.liked else R.drawable.like)
            }
        }
        myHolder.likeCountSubscription = feed.likeCountObservable.subscribe {
            myHolder.tvLikeCount.text = it.toString()
        }
        myHolder.ivLike.setOnClickListener {
            try {
                val props = JSONObject()
                props.put("wall", titleWall)
                Mixpanel.getInstance().track("videoLike", props)
            } catch (e: JSONException) {
                e.printStackTrace()
            }

            try {
                val props = JSONObject()
                props.put("wall", titleWall)
                Mixpanel.getInstance().track("likes", props)
            } catch (e: JSONException) {
                e.printStackTrace()
            }

            feed.like(typeWall)
        }

        loadAvatar(myHolder, feed)
        loadPreview(myHolder, feed, pos)

        myHolder.btnDots.visibility = if (reports) VISIBLE else GONE
        myHolder.btnDots.setOnClickListener {
            if (myHolder.btnReport.visibility == VISIBLE) {
                myHolder.btnReport.visibility = GONE
            } else {
                myHolder.btnReport.visibility = VISIBLE
            }
        }

        if (item.user.id == AppInfo.id) {
            selfContent = true
            myHolder.btnReport.setText(R.string.item_feed_btn_delete_text)
        } else {
            selfContent = false
            myHolder.btnReport.setText(R.string.item_feed_btn_report_text)
        }
        myHolder.btnReport.visibility = GONE
        myHolder.btnReport.setOnClickListener {
            myHolder.btnReport.visibility = GONE
            onReportClicked(pos, selfContent, item)
        }

        myHolder.videoView.setOnClickListener {
            if (isPauseVideo) {
                player?.playWhenReady = true
                isPauseVideo = false
                myHolder.ivPause.visibility = GONE
            } else {
                player?.playWhenReady = false
                isPauseVideo = true
                myHolder.ivPause.visibility = VISIBLE
            }
        }

    }

    override fun bind(holder: RecyclerView.ViewHolder, item: Any, pos: Int, payload: Any) {
        val myHolder = holder as ViewHolder
        myHolder.tvCommentCount.text = payload.toString()
    }

    private fun startVideo(feed: Feed, myHolder: ViewHolder, pos: Int) {
        MusicPlayer.stop()
        compositeAdapter?.release()

        val bandwidthMeter = DefaultBandwidthMeter()
        val trackSelectionFactory = AdaptiveTrackSelection.Factory(bandwidthMeter)
        val trackSelector = DefaultTrackSelector(trackSelectionFactory)
        val player = ExoPlayerFactory.newSimpleInstance(context, trackSelector)

        val userAgent = Util.getUserAgent(context, "AB")
        val dataSourceFactory = DefaultDataSourceFactory(context, userAgent)
        val cacheDataSourceFactory = CacheDataSourceFactory(ExoPlayerCache.cache, dataSourceFactory, CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val mediaSource = ExtractorMediaSource.Factory(cacheDataSourceFactory)
                .createMediaSource(Uri.parse(AppConfig.MEDIA_URL + feed.mediaLinkUuid), Handler(Looper.getMainLooper()), null)
        player.prepare(mediaSource)
        player.playWhenReady = true
        player.setVideoTextureView(myHolder.videoView)
        player.addListener(playbackListener)
        this.player = player
        playingPos = pos
        videoHolder = myHolder
    }

    override fun release() {
        val oldPlayer = player
        val holder = this.videoHolder ?: return
        if (oldPlayer != null) {
            if (!isPauseVideo) {
                onPlaybackStateChanged(Player.STATE_ENDED)
                playingPos = null
                videoHolder = null
                oldPlayer.setVideoTextureView(null)
                oldPlayer.removeListener(playbackListener)
                oldPlayer.stop()
                oldPlayer.release()
            } else {
                holder.ivPause.visibility = VISIBLE
            }
        }
    }

    private fun loadPreview(holder: ViewHolder, feed: Feed, pos: Int) {
        holder.ivImage.visibility = View.GONE
        holder.ivPlay.visibility = View.GONE
        holder.ivImagePlaceHolder.visibility = View.VISIBLE
        holder.videoView.visibility = View.GONE
        holder.ivImagePlaceHolder.layoutParams = holder.ivImagePlaceHolder.layoutParams
                .also { it.height = (rootViewWidth * feed.ratio).toInt() }
        holder.isPreviewLoaded = false
        val previewUrl = AppConfig.PREVIEW_VIDEO_URL + feed.mediaLinkUuid
        val previewObservable = ImageLoader.loadImage(previewUrl, rootViewWidth)
                .doOnNext { Log.d("LOGI", "image loaded") }
                .doOnError { Log.d("LOGI", "image failed $previewUrl") }
        val previewSubscription = previewObservable.subscribe({
            holder.ivImage.setImageBitmap(it)
            holder.isPreviewLoaded = true
            val playbackState = player?.playbackState ?: Player.STATE_IDLE
            if (!isPlayingHolder(holder) || playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                holder.ivImage.visibility = View.VISIBLE
                holder.ivPlay.visibility = View.VISIBLE
                holder.ivImagePlaceHolder.visibility = View.GONE
                holder.videoView.visibility = View.GONE
            }
            holder.previewSubscription = null
        }, {
            holder.previewSubscription = null
            it.printStackTrace()
        })
        holder.previewSubscription?.unsubscribe()
        holder.previewSubscription = previewSubscription
        if (isPost) {
            val player = player
            if (player != null) {
                if (!player.playWhenReady) {
                    if (isPauseVideo) {
                        holder.ivPause.visibility = VISIBLE
                    } else {
                        holder.ivPause.visibility = GONE
                    }
                }
            } else {
                startVideo(feed, holder, pos)
            }

        }
    }

    private fun loadAvatar(holder: ViewHolder, feed: Feed) {
        val avatarUpdater = {
            holder.ivAvatar.visibility = View.INVISIBLE
            holder.ivAvatarPlaceHolder.visibility = View.VISIBLE
            val avatarUrl = AppConfig.AVATAR_URL + feed.user.avatar
            val avatarObservable = ImageLoader.loadImage(avatarUrl, (45 * density).toInt())
                    .doOnNext { Log.d("LOGI", "image loaded") }
                    .doOnError { Log.d("LOGI", "image failed") }
            val avatarSubscription = avatarObservable.subscribe({
                holder.ivAvatar.setImageBitmap(it)
                holder.ivAvatar.visibility = View.VISIBLE
                holder.ivAvatarPlaceHolder.visibility = View.GONE
                holder.avatarSubscription = null
            }, {
                holder.ivAvatar.visibility = View.VISIBLE
                holder.ivAvatarPlaceHolder.visibility = View.GONE
                holder.avatarSubscription = null
                it.printStackTrace()
            })
            holder.avatarSubscription?.unsubscribe()
            holder.avatarSubscription = avatarSubscription
        }
        holder.avatarChangeSubscription?.unsubscribe()
        if (feed.user.id == AppInfo.id) {
            holder.avatarChangeSubscription = AppInfo.idObservable
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { avatarUpdater() }
        } else {
            avatarUpdater()
        }
    }

    override fun create(parent: ViewGroup): RecyclerView.ViewHolder =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_video_feed, parent, false))

    override fun canHandle(item: Any): Boolean = item is Feed && item.mediatype == AppConfig.TYPE_MEDIA_VIDEO

    override fun viewType(): Int = DelegateAdapterViewTypes.TYPE_IMAGE

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        holder as ViewHolder
        if (isPlayingHolder(holder)) {
            videoHolder = holder
            player?.setVideoTextureView(holder.videoView)
            onPlaybackStateChanged(player?.playbackState ?: Player.STATE_IDLE)
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        holder as ViewHolder
        if (isPlayingHolder(holder)) {
            isShowReplay = true
            onPlaybackStateChanged(Player.STATE_IDLE)
            videoHolder = null
            player?.setVideoTextureView(null)
        }
    }

    private fun onPlaybackStateChanged(playbackState: Int) {
        val holder = this.videoHolder ?: return
        when (playbackState) {
            Player.STATE_BUFFERING, Player.STATE_READY -> {
                holder.videoView.visibility = View.VISIBLE
                holder.ivImage.visibility = View.GONE
                holder.ivPlay.visibility = View.GONE
                holder.ivReplay.visibility = GONE
                holder.ivImagePlaceHolder.visibility = View.GONE
            }
            Player.STATE_ENDED, Player.STATE_IDLE -> {
                holder.videoView.visibility = View.GONE
                if (!holder.isPreviewLoaded) {
                    holder.ivImage.visibility = View.GONE
                    holder.ivImagePlaceHolder.visibility = View.VISIBLE
                } else {
                    holder.ivImage.visibility = View.VISIBLE
                    holder.ivImagePlaceHolder.visibility = View.GONE
                    if (isShowReplay) {
                        holder.ivPause.visibility = GONE
                        holder.ivReplay.visibility = View.VISIBLE
                    }
                    if (playbackState.equals(Player.STATE_ENDED)) {
                        holder.ivReplay.visibility = View.VISIBLE
                        holder.ivPause.visibility = GONE
                    }
                }
            }
        }
    }

    private fun isPlayingHolder(holder: ViewHolder): Boolean {
        val playingPos = this.playingPos
        return playingPos != null && holder.itemPos == playingPos
    }

    private fun showLoading(flag: Boolean) {
        val holder = this.videoHolder ?: return
        if (flag) {
            holder.ivImagePlaceHolder.visibility = GONE
        } else {
            holder.ivImagePlaceHolder.visibility = VISIBLE

        }
    }

    private val playbackListener = object : Player.EventListener {
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {
        }

        override fun onSeekProcessed() {
        }

        override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {
        }

        override fun onPlayerError(error: ExoPlaybackException?) {
        }

        override fun onLoadingChanged(isLoading: Boolean) {
        }

        override fun onPositionDiscontinuity(reason: Int) {
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        }

        override fun onTimelineChanged(timeline: Timeline?, manifest: Any?) {
            showLoading(false)
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            showLoading(true)
            onPlaybackStateChanged(playbackState)
        }
    }

    private fun setResources(holder: ViewHolder) {
        if (!AppInfo.isDefaultResources) {
            holder.ivComment.setImageBitmap(ResourceManager.instance.getImage(ResourceManager.I_COMMENT))
            holder.btnShare.setImageBitmap(ResourceManager.instance.getImage(ResourceManager.I_SHARE))
            holder.btnDots.setImageBitmap(ResourceManager.instance.getImage(ResourceManager.I_DOTS))
            holder.ivPlay.setImageBitmap(ResourceManager.instance.getImage(ResourceManager.I_PLAY_BUTTON))
            holder.ivReplay.setImageBitmap(ResourceManager.instance.getImage(ResourceManager.I_REPLAY))
            holder.ivPause.setImageBitmap(ResourceManager.instance.getImage(ResourceManager.I_PAUSE_PLAYER))
            holder.ivAvatar.setImageBitmap(ResourceManager.instance.getImage(ResourceManager.I_DEFAULT_AVATAR))

            holder.tvName.typeface = ResourceManager.instance.fontsMap[ResourceManager.F_BOLD]
            holder.btnReport.typeface = ResourceManager.instance.fontsMap[ResourceManager.F_BOLD]
            holder.tvDate.typeface = ResourceManager.instance.fontsMap[ResourceManager.F_REGULAR]
            holder.tvText.typeface = ResourceManager.instance.fontsMap[ResourceManager.F_REGULAR]
            holder.tvLikeCount.typeface = ResourceManager.instance.fontsMap[ResourceManager.F_REGULAR]
            holder.tvCommentCount.typeface = ResourceManager.instance.fontsMap[ResourceManager.F_REGULAR]

            holder.btnReport.background = ResourceManager.instance.makeSelector()
            holder.btnReport.setTextColor(Color.parseColor(ResourceManager.instance.colorsMap.textButton))
            holder.tvName.setTextColor(Color.parseColor(ResourceManager.instance.colorsMap.nameUserOnPost))
            holder.tvDate.setTextColor(Color.parseColor(ResourceManager.instance.colorsMap.datePost))
            holder.tvText.setTextColor(Color.parseColor(ResourceManager.instance.colorsMap.descriptionPostPodcast))
            holder.tvLikeCount.setTextColor(Color.parseColor(ResourceManager.instance.colorsMap.countLike))
            holder.tvCommentCount.setTextColor(Color.parseColor(ResourceManager.instance.colorsMap.countComment))
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: CircleImageView = view.findViewById(R.id.itemVideoFeedAvatar)
        val ivAvatarPlaceHolder: LottieAnimationView = view.findViewById(R.id.itemVideoFeedAvatarPlaceholder)
        val ivImage: ImageView = view.findViewById(R.id.itemVideoFeedMediaImage)
        val ivPlay: ImageView = view.findViewById(R.id.itemVideoFeedPlay)
        val ivReplay: ImageView = view.findViewById(R.id.itemVideoFeedReplay)
        val ivPause: ImageView = view.findViewById(R.id.itemVideoFeedPause)
        val ivImagePlaceHolder: ImageView = view.findViewById(R.id.itemVideoFeedMediaPlaceholder)
        val videoView: TextureView = view.findViewById(R.id.itemVideoFeedMediaVideo)
        val tvName: TextView = view.findViewById(R.id.itemVideoFeedUserName)
        val tvDate: TextView = view.findViewById(R.id.itemVideoFeedDate)
        val tvText: TextView = view.findViewById(R.id.itemVideoFeedText)
        val tvLikeCount: TextView = view.findViewById(R.id.itemVideoFeedLikeCount)
        val ivLike: ImageView = view.findViewById(R.id.itemVideoFeedLike)
        val ivComment: ImageView = view.findViewById(R.id.itemVideoFeedComment)
        val tvCommentCount: TextView = view.findViewById(R.id.itemVideoFeedCommentCount)
        val btnShare: ImageView = view.findViewById(R.id.itemVideoFeedShare)
        val btnDots: ImageView = view.findViewById(R.id.itemVideoFeedDots)
        val btnReport: Button = view.findViewById(R.id.itemVideoFeedBtnReport)
        val itemVideoFeedTopLine: View = view.findViewById(R.id.itemVideoFeedTopLine)
        var itemPos: Int? = null
        var avatarSubscription: Subscription? = null
        var previewSubscription: Subscription? = null
        var isPreviewLoaded: Boolean = false
        lateinit var feed: Feed
        var likeSubscription: Subscription? = null
        var likeCountSubscription: Subscription? = null
        var nicknameChangeSubscription: Subscription? = null
        var avatarChangeSubscription: Subscription? = null
    }
}
