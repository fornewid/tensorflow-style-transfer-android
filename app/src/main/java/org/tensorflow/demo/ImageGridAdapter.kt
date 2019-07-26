package org.tensorflow.demo

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter

class ImageGridAdapter(
    context: Context,
    private val thumbnailList: List<Int>
) : BaseAdapter() {

    val items: List<ImageSlider> = thumbnailList.map {
        ImageSlider(context).apply {
            setImageResource(it)
        }
    }

    override fun getCount(): Int {
        return thumbnailList.size
    }

    override fun getItem(position: Int): ImageSlider {
        return items[position]
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).hashCode().toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return convertView ?: getItem(position) as View
    }
}
