package company.domain.ui.adapter

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import therussianmob.angelbrinks.logE
import java.util.*

class CompositeAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val adapters: MutableMap<Int, BaseDelegateAdapter> = TreeMap()
    val data: MutableList<Any> = ArrayList()
    val size: Int
        get() = data.size

    fun addAdapter(adapter: BaseDelegateAdapter) {
        val type = adapter.viewType()
        if (!adapters.contains(type)) {
            adapters[type] = adapter
            adapter.setCompositeAdapter(this)
        } else {
            throw RuntimeException("Adapter with viewType=$type already exists")
        }
    }

    fun addItems(newItems: List<Any>) {
        val insertPos = data.size
        data.addAll(newItems)
        notifyItemRangeInserted(insertPos, newItems.size)
    }

    fun addItem(newItem: Any) {
        val insertPos = data.size
        data.add(newItem)
        notifyItemInserted(insertPos)
    }

    fun insertItems(at: Int, newItems: List<Any>) {
        val insertPos = data.size
        data.addAll(at, newItems)
        notifyItemRangeInserted(insertPos, newItems.size)
    }

    fun insertItem(at: Int, newItem: Any) {
        data.add(at, newItem)
        notifyItemInserted(at)
    }

    fun removeItems(pos: Int, size: Int) {
        val itemsToRemove = Math.min(size, data.size - pos)
        if (itemsToRemove == 0) {
            return
        }

        (0 until itemsToRemove).forEach { data.removeAt(pos) }
        notifyItemRangeRemoved(pos, itemsToRemove)
    }

    fun addListTop(list: List<Any>) {
        for (i in list.indices) {
            data.add(i, list[i])
            notifyItemInserted(i)
        }
    }

    fun addItemTop(item: Any) {
        data.add(0, item)
        notifyItemInserted(0)
    }

    fun removeItem(pos: Int) {
        if (pos < data.size) {
            data.removeAt(pos)
            notifyItemRemoved(pos)
        }
    }

    fun clearItems() {
        val oldSize = data.size
        data.clear()
        notifyItemRangeRemoved(0, oldSize)
    }

    fun release() {
        adapters.values.forEach { it.release() }
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        getAdapterForViewType(holder.itemViewType).bind(holder, data[position], position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            getAdapterForViewType(viewType).create(parent)

    override fun getItemViewType(position: Int): Int =
            adapters.values.find { it.canHandle(data[position]) }
                    ?.viewType() ?: throw RuntimeException("Can not handle item at $position")

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        getAdapterForViewType(holder.itemViewType).onViewAttachedToWindow(holder)
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        getAdapterForViewType(holder.itemViewType).onViewDetachedFromWindow(holder)
    }

    private fun getAdapterForViewType(viewType: Int): BaseDelegateAdapter =
            adapters[viewType] ?: throw RuntimeException("Can not handle viewType=$viewType")

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>?) {
        if (payloads == null || payloads.size == 0) {
            getAdapterForViewType(holder.itemViewType).bind(holder, data[position], position)
        } else {
            payloads.forEach {
                getAdapterForViewType(holder.itemViewType).bind(holder, data[position], position, it)
            }
        }
    }
}

