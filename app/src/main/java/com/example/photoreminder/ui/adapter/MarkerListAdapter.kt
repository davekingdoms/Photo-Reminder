package com.example.photoreminder.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.photoreminder.data.local.MarkerEntity
import com.example.photoreminder.databinding.ItemMarkerListBinding
import java.text.SimpleDateFormat
import java.util.*

class MarkerListAdapter(
    private var items: List<MarkerEntity>,
    private val onClick: (MarkerEntity) -> Unit
) : RecyclerView.Adapter<MarkerListAdapter.MarkerVH>() {

    private val df = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())

    inner class MarkerVH(val binding: ItemMarkerListBinding)
        : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MarkerEntity, pos: Int) = with(binding) {
            numberItem.text = (pos + 1).toString()
            itemNameTextView.text = item.title
            dateTextView.text = df.format(Date(item.updatedAt))
            latTextView.text = "lat: %.5f".format(item.lat)
            longTextView.text = "lng: %.5f".format(item.lng)
            itemTagValueTextView.text = item.tag ?: "—"

            root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MarkerVH =
        MarkerVH(ItemMarkerListBinding.inflate(
            LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: MarkerVH, position: Int) =
        holder.bind(items[position], position)

    override fun getItemCount(): Int = items.size

    fun submit(newList: List<MarkerEntity>) {
        items = newList
        notifyDataSetChanged()
    }
}
