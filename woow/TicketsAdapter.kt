package company.project.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import dev.klippe.karma.R
import dev.klippe.karma.core.PhotoModel
import dev.klippe.karma.dataobjects.TicketDelivery
import dev.klippe.karma.dataobjects.TicketPromotion
import dev.klippe.karma.dataobjects.TicketTrip
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers

class TicketsAdapter(
        private val mContext: Context,
        private val mPhotoModel: PhotoModel,
        private val mEventOwner: Boolean,
        private val mOnTripClickListener: (TicketTrip) -> Unit,
        private val mOnDeliveryClickListener: (TicketDelivery) -> Unit,
        private val mOnPromotionClickListener: (TicketPromotion) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var data: MutableList<Any> = ArrayList()

    companion object {
        const val TYPE_ITEM_TRIP = 1
        const val TYPE_ITEM_DELIVERY = 2
        const val TYPE_ITEM_PROMOTION = 3

        private val TAG = "RecyclerView"
    }

    fun updateData(newData: List<Any>) {
        data.clear()
        data.addAll(newData)
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            TYPE_ITEM_TRIP -> (holder as TripItemHolder).bind(position, data[position] as TicketTrip)
            TYPE_ITEM_DELIVERY -> (holder as DeliveryItemHolder).bind(position, data[position] as TicketDelivery)
            TYPE_ITEM_PROMOTION -> (holder as PromotionItemHolder).bind(position, data[position] as TicketPromotion)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            when (viewType) {
                TYPE_ITEM_TRIP -> TripItemHolder(LayoutInflater.from(mContext).inflate(R.layout.adapter_trip_ticket, parent, false))
                TYPE_ITEM_DELIVERY -> DeliveryItemHolder(LayoutInflater.from(mContext).inflate(R.layout.adapter_trip_ticket, parent, false))
                TYPE_ITEM_PROMOTION -> PromotionItemHolder(LayoutInflater.from(mContext).inflate(R.layout.adapter_trip_ticket, parent, false))
                else -> throw RuntimeException("No such viewType $viewType")
            }


    override fun getItemViewType(position: Int): Int = when {
        data[position] is TicketTrip -> TYPE_ITEM_TRIP
        data[position] is TicketDelivery -> TYPE_ITEM_DELIVERY
        data[position] is TicketPromotion -> TYPE_ITEM_PROMOTION
        else -> -1
    }

    override fun getItemCount(): Int = data.size

    inner class DeliveryItemHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val root = v
        private val tvName = v.findViewById<TextView>(R.id.name)
        private val tvStatus = v.findViewById<TextView>(R.id.status)
        private val ivAvatar = v.findViewById<ImageView>(R.id.avatar)
        private var avatarSubscription: Subscription? = null

        @SuppressLint("SetTextI18n", "SimpleDateFormat")
        fun bind(position: Int, item: TicketDelivery) {
            tvName.text = if (mEventOwner) item.senderName else item.driverName
            tvStatus.text = item.status
            avatarSubscription?.unsubscribe()
            (if (mEventOwner) item.senderAvatarUrl else item.driverAvatarUrl)?.let { photoUrl ->
                avatarSubscription = mPhotoModel.loadPhoto(photoUrl)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            ivAvatar.setImageBitmap(it)
                        }, {
                            it.printStackTrace()
                        })
            }
            root.setOnClickListener { mOnDeliveryClickListener(item) }
        }
    }

    inner class TripItemHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val root = v
        private val tvName = v.findViewById<TextView>(R.id.name)
        private val tvStatus = v.findViewById<TextView>(R.id.status)
        private val ivAvatar = v.findViewById<ImageView>(R.id.avatar)
        private var avatarSubscription: Subscription? = null

        @SuppressLint("SetTextI18n", "SimpleDateFormat")
        fun bind(position: Int, item: TicketTrip) {
            tvName.text = if (mEventOwner) item.passengerName else item.driverName
            tvStatus.text = item.status
            avatarSubscription?.unsubscribe()
            (if (mEventOwner) item.passengerAvatarUrl else item.driverAvatarUrl)?.let { photoUrl ->
                avatarSubscription = mPhotoModel.loadPhoto(photoUrl)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            ivAvatar.setImageBitmap(it)
                        }, {
                            it.printStackTrace()
                        })
            }
            root.setOnClickListener { mOnTripClickListener(item) }
        }
    }

    inner class PromotionItemHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val root = v
        private val tvName = v.findViewById<TextView>(R.id.name)
        private val tvStatus = v.findViewById<TextView>(R.id.status)
        private val ivAvatar = v.findViewById<ImageView>(R.id.avatar)
        private var avatarSubscription: Subscription? = null

        @SuppressLint("SetTextI18n", "SimpleDateFormat")
        fun bind(position: Int, item: TicketPromotion) {
            tvName.text = if (mEventOwner) item.customerName else item.vendorName
            tvStatus.text = item.status
            avatarSubscription?.unsubscribe()
            (if (mEventOwner) item.customerAvatarUrl else item.vendorAvatarUrl)?.let { photoUrl ->
                avatarSubscription = mPhotoModel.loadPhoto(photoUrl)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            ivAvatar.setImageBitmap(it)
                        }, {
                            it.printStackTrace()
                        })
            }
            root.setOnClickListener { mOnPromotionClickListener(item) }
        }
    }
}
