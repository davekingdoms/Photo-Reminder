package com.example.photoreminder.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.photoreminder.R


class TagAdapter(
    private var tags: List<String>,
    private var selectedTag: String = "All",
    private val onTagSelected: (String) -> Unit
) : RecyclerView.Adapter<TagAdapter.TagViewHolder>() {

    inner class TagViewHolder(parent: ViewGroup) :
        RecyclerView.ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tag, parent, false)
        ) {
        val button: Button = itemView.findViewById(R.id.tagButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        return TagViewHolder(parent)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        val tag = tags[position]
        holder.button.text = tag

        val colorRes = if (tag.equals(selectedTag, ignoreCase = true)) {
            R.color.colorOrange
        } else {
            R.color.colorPrimaryDark
        }
        holder.button.backgroundTintList =
            ContextCompat.getColorStateList(holder.button.context, colorRes)

        holder.button.setOnClickListener {
            if (!tag.equals(selectedTag, ignoreCase = true)) {
                val oldSelected = selectedTag
                selectedTag = tag
                notifyItemChanged(tags.indexOf(oldSelected))
                notifyItemChanged(position)
                onTagSelected(tag)
            }
        }
    }

    override fun getItemCount(): Int = tags.size


    fun updateTags(newTags: List<String>) {
        val oldTags = tags
        val oldSelected = selectedTag
        var shouldNotifyAllSelected = false

        // Determine if selected tag is still valid in new list
        if (!newTags.any { it.equals(selectedTag, ignoreCase = true) }) {
            selectedTag = "All"
            shouldNotifyAllSelected = true
        }

        tags = newTags

        // Calculate diff and dispatch updates
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldTags.size
            override fun getNewListSize(): Int = tags.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldTags[oldItemPosition] == tags[newItemPosition]
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                // For tags, content is the same if the tag string is the same
                return oldTags[oldItemPosition] == tags[newItemPosition] &&
                        (oldTags[oldItemPosition].equals(oldSelected, ignoreCase = true) == tags[newItemPosition].equals(selectedTag, ignoreCase = true))
            }
        })
        diffResult.dispatchUpdatesTo(this)

        if (shouldNotifyAllSelected) {
            onTagSelected("All")
        }
    }
}