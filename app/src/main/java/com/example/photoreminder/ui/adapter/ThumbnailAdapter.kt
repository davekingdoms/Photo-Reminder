package com.example.photoreminder.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.photoreminder.databinding.ItemThumbBinding
import java.io.File

class ThumbnailAdapter(
    private val onClick: ((String) -> Unit)? = null
) : RecyclerView.Adapter<ThumbnailAdapter.VH>() {

    private var items: List<String> = emptyList()

    inner class VH(val binding: ItemThumbBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(path: String) = with(binding) {
            Glide.with(imageView4)
                .load(File(path))
                .placeholder(com.example.photoreminder.R.drawable.ic_launcher_background)
                .into(imageView4)

            root.setOnClickListener { onClick?.invoke(path) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemThumbBinding.inflate(
            LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    /* helpers */
    fun submit(newList: List<String>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o] == newList[n]
            override fun areContentsTheSame(o: Int, n: Int) = true
        })
        items = newList
        diff.dispatchUpdatesTo(this)
    }
}
