package com.example.photoreminder.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.photoreminder.R
import com.example.photoreminder.data.model.PhotoRef
import com.example.photoreminder.databinding.ItemThumbBinding
import java.io.File

class ThumbnailAdapter(
    private val onClick: (photo: PhotoRef, thumbView: ImageView) -> Unit
) : RecyclerView.Adapter<ThumbnailAdapter.VH>() {

    private var items: List<PhotoRef> = emptyList()

    inner class VH(val binding: ItemThumbBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(pr: PhotoRef) = with(binding) {
            Glide.with(imageView4)
                .load(File(pr.thumbPath))
                .placeholder(R.drawable.ic_launcher_background)
                .into(imageView4)

            root.setOnClickListener { onClick(pr, imageView4) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemThumbBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    fun submit(newList: List<PhotoRef>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(o: Int, n: Int) =
                items[o].remoteId == newList[n].remoteId &&
                        items[o].thumbPath == newList[n].thumbPath
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == newList[n]
        })
        items = newList
        diff.dispatchUpdatesTo(this)
    }
}