package company.domain.ui.adapter

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.airbnb.lottie.LottieAnimationView
import org.json.JSONException
import org.json.JSONObject
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import therussianmob.angelbrinks.*
import therussianmob.angelbrinks.network.ImageLoader
import therussianmob.angelbrinks.network.data.User
import therussianmob.angelbrinks.ui.customview.CircleImageView
import therussianmob.angelbrinks.ui.data.Feed
import java.text.SimpleDateFormat
import java.util.*

class ImageDelegateAdapter(
        private val context: Context,
        private val reports: Boolean,
        private val typeWall: String,
        private val titleWall: String,
        private val idWall: String,
        private val onReportClicked: (pos: Int, selfContent: Boolean, feed: Feed) -> Unit,
        private val onItemClick: ((pos: Int, feed: Feed) -> Unit)? = null,
        private val onUserClick: ((pos: Int, user: User) -> Unit)? = null
) : AbstractDelegateAdapter() {
    private val rootViewWidth: Int
    private val density = context.resources.displayMetrics.density
    private var compositeAdapter: CompositeAdapter? = null
    private var selfContent: Boolean = false;

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

        setResources(holder)

        myHolder.itemPos = pos
        if (feed.description == "" || feed.description == null)
            myHolder.tvText.visibility = GONE
        myHolder.tvText.text = feed.description
        myHolder.tvCommentCount.text = feed.commentCount.toString()
        myHolder.itemView.setOnClickListener { onItemClick?.invoke(pos, feed) }
        myHolder.ivAvatar.setOnClickListener { onUserClick?.invoke(pos, feed.user) }
        myHolder.tvName.setOnClickListener { onUserClick?.invoke(pos, feed.user) }

        if (pos == 0) {
            myHolder.itemImageFeedTopLine.visibility = GONE
        }

        myHolder.nicknameChangeSubscription?.unsubscribe()
        if (item.user.id == AppInfo.id) {
            myHolder.nicknameChangeSubscription = AppInfo.loginObservable
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { myHolder.tvName.text = it }
        } else {
            myHolder.tvName.text = feed.user.username
        }

        val inFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val outFormat = SimpleDateFormat("dd MMMM 'at' hh:mma", Locale.US)
        myHolder.tvDate.text = outFormat.format(inFormat.parse(feed.created))

        myHolder.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.sharingMsg))
            shareIntent.putExtra(Intent.EXTRA_TEXT, AppConfig.SHARED_URL + "$idWall/$typeWall/${feed.id}")
            context.startActivity(Intent.createChooser(shareIntent, "Share post"))
        }

        myHolder.ivImage.visibility = View.GONE
        myHolder.ivImagePlaceHolder.visibility = View.VISIBLE
        myHolder.ivImagePlaceHolder.layoutParams = myHolder.ivImagePlaceHolder.layoutParams
                .also { it.height = (rootViewWidth * feed.ratio).toInt() }
        val imageUrl = AppConfig.MEDIA_URL + feed.mediaLinkUuid
        val imgObservable = ImageLoader.loadImage(imageUrl, rootViewWidth)
                .map { it.scaleWithRatio(rootViewWidth) }
                .doOnNext { Log.d("LOGI", "pos $pos image loaded") }
                .doOnError { Log.d("LOGI", "pos $pos image failed") }
        val imgSubscription = imgObservable.subscribe({
            myHolder.ivImage.setImageBitmap(it)
            myHolder.ivImage.visibility = View.VISIBLE
            myHolder.ivImagePlaceHolder.visibility = View.GONE
            myHolder.imageSubscription = null
        }, {
            myHolder.imageSubscription = null
            it.printStackTrace()
        })
        myHolder.imageSubscription?.unsubscribe()
        myHolder.imageSubscription = imgSubscription

        val avatarUpdater = {
            myHolder.ivAvatar.visibility = View.INVISIBLE
            myHolder.ivAvatarPlaceHolder.visibility = View.VISIBLE
            val avatarUrl = AppConfig.AVATAR_URL + feed.user.avatar
            val avatarObservable = ImageLoader.loadImage(avatarUrl, (45 * density).toInt())
                    .map { it.scaleWithRatio(45 * density.toInt()) }
                    .doOnNext { Log.d("LOGI", "pos $pos image loaded") }
                    .doOnError { Log.d("LOGI", "pos $pos image failed") }
            val avatarSubscription = avatarObservable.subscribe({
                myHolder.ivAvatar.setImageBitmap(it)
                myHolder.ivAvatar.visibility = View.VISIBLE
                myHolder.ivAvatarPlaceHolder.visibility = View.GONE
                myHolder.avatarSubscription = null
            }, {
                myHolder.ivAvatar.visibility = View.VISIBLE
                myHolder.ivAvatarPlaceHolder.visibility = View.GONE
                myHolder.avatarSubscription = null
                it.printStackTrace()
            })
            myHolder.avatarSubscription?.unsubscribe()
            myHolder.avatarSubscription = avatarSubscription
        }
        myHolder.avatarChangeSubscription?.unsubscribe()
        if (item.user.id == AppInfo.id) {
            myHolder.avatarChangeSubscription = AppInfo.idObservable
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { avatarUpdater() }
        } else {
            avatarUpdater()
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
                Mixpanel.getInstance().track("photoLike", props)
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
    }

    override fun create(parent: ViewGroup): RecyclerView.ViewHolder =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_image_feed, parent, false))

    override fun canHandle(item: Any): Boolean = item is Feed && item.mediatype == AppConfig.TYPE_MEDIA_IMAGE

    override fun viewType(): Int = DelegateAdapterViewTypes.TYPE_VIDEO

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
    }

    override fun release() {
    }

    private fun setResources(holder: ViewHolder) {
        if (!AppInfo.isDefaultResources) {
            holder.ivComment.setImageBitmap(ResourceManager.instance.getImage(ResourceManager.I_COMMENT))
            holder.btnShare.setImageBitmap(ResourceManager.instance.getImage(ResourceManager.I_SHARE))
            holder.btnDots.setImageBitmap(ResourceManager.instance.getImage(ResourceManager.I_DOTS))
            holder.ivAvatar.setImageBitmap(ResourceManager.instance.getImage(ResourceManager.I_DEFAULT_AVATAR))

            holder.tvName.typeface = ResourceManager.instance.fontsMap[ResourceManager.F_BOLD]
            holder.btnReport.typeface = ResourceManager.instance.fontsMap[ResourceManager.F_BOLD]
            holder.tvDate.typeface = ResourceManager.instance.fontsMap[ResourceManager.F_REGULAR]
            holder.tvText.typeface = ResourceManager.instance.fontsMap[ResourceManager.F_REGULAR]
            holder.tvLikeCount.typeface = ResourceManager.instance.fontsMap[ResourceManager.F_REGULAR]
            holder.tvCommentCount.typeface = ResourceManager.instance.fontsMap[ResourceManager.F_REGULAR]

            holder.btnReport.background = ResourceManager.instance.makeSelector()
            holder.tvName.setTextColor(Color.parseColor(ResourceManager.instance.colorsMap.nameUserOnPost))
            holder.btnReport.setTextColor(Color.parseColor(ResourceManager.instance.colorsMap.textButton))
            holder.tvDate.setTextColor(Color.parseColor(ResourceManager.instance.colorsMap.datePost))
            holder.tvText.setTextColor(Color.parseColor(ResourceManager.instance.colorsMap.descriptionPostPodcast))
            holder.tvLikeCount.setTextColor(Color.parseColor(ResourceManager.instance.colorsMap.countLike))
            holder.tvCommentCount.setTextColor(Color.parseColor(ResourceManager.instance.colorsMap.countComment))
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: CircleImageView = view.findViewById(R.id.itemImageFeedAvatar)
        val ivAvatarPlaceHolder: LottieAnimationView = view.findViewById(R.id.itemImageFeedAvatarPlaceholder)
        val ivImage: ImageView = view.findViewById(R.id.itemImageFeedMediaImage)
        val ivImagePlaceHolder: ImageView = view.findViewById(R.id.itemImageFeedMediaPlaceholder)
        val tvName: TextView = view.findViewById(R.id.itemImageFeedUserName)
        val tvDate: TextView = view.findViewById(R.id.itemImageFeedDate)
        val tvText: TextView = view.findViewById(R.id.itemImageFeedText)
        val ivLike: ImageView = view.findViewById(R.id.itemImageFeedLike)
        val ivComment: ImageView = view.findViewById(R.id.itemImageFeedComment)
        val tvLikeCount: TextView = view.findViewById(R.id.itemImageFeedLikeCount)
        val tvCommentCount: TextView = view.findViewById(R.id.itemImageFeedCommentCount)
        val btnShare: ImageView = view.findViewById(R.id.itemImageFeedShare)
        val btnDots: ImageView = view.findViewById(R.id.itemImageFeedDots)
        val btnReport: Button = view.findViewById(R.id.itemImageFeedsBtnReports)
        val itemImageFeedTopLine: View = view.findViewById(R.id.itemImageFeedTopLine)
        var itemPos: Int? = null
        var avatarSubscription: Subscription? = null
        var imageSubscription: Subscription? = null
        var likeSubscription: Subscription? = null
        var likeCountSubscription: Subscription? = null
        var nicknameChangeSubscription: Subscription? = null
        var avatarChangeSubscription: Subscription? = null
    }
}
