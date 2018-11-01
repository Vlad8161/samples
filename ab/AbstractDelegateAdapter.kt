package company.domain.ui.adapter

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup

abstract class AbstractDelegateAdapter : BaseDelegateAdapter {

    override fun bind(holder: RecyclerView.ViewHolder, item: Any, pos: Int, payload: Any) {
        bind(holder, item, pos)
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
    }

    override fun release() {
    }

    override fun setCompositeAdapter(adapter: CompositeAdapter?) {
    }
}
