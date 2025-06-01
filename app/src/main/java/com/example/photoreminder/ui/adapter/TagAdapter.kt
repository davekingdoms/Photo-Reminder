package com.example.photoreminder.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.photoreminder.R
import java.util.*

/**
 * Adapter per mostrare la lista orizzontale di “tag”.
 *
 * Il primo elemento di [tags] sarà sempre "All". Gli altri sono tutti i tag/ generi unici.
 * Quando si clicca un tag diverso dall’attuale, aggiorna il “selectedTag” e notifica il callback.
 */
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

        // Imposta il background a seconda se questo è il tag selezionato
        val colorRes = if (tag.equals(selectedTag, ignoreCase = true)) {
            R.color.colorOrange
        } else {
            R.color.colorPrimaryDark
        }
        holder.button.backgroundTintList =
            ContextCompat.getColorStateList(holder.button.context, colorRes)

        // Se clicchiamo su un tag già selezionato, non facciamo nulla
        holder.button.setOnClickListener {
            if (!tag.equals(selectedTag, ignoreCase = true)) {
                val oldSelected = selectedTag
                selectedTag = tag
                notifyItemChanged(tags.indexOf(oldSelected)) // aggiorna vecchio selezionato
                notifyItemChanged(position)                  // aggiorna nuovo selezionato
                onTagSelected(tag)
            }
        }
    }

    override fun getItemCount(): Int = tags.size

    /**
     * Aggiorna la lista di tag (es. quando cambia viewModel.markers).
     *
     * Se il tag precedentemente selezionato non c’è più nella nuova lista,
     * torna a "All" (e notifica onTagSelected("All")).
     */
    fun updateTags(newTags: List<String>) {
        // Se il tag selezionato non c’è più, resettiamo a "All"
        if (!newTags.any { it.equals(selectedTag, ignoreCase = true) }) {
            selectedTag = "All"
        }
        tags = newTags
        notifyDataSetChanged()
        // Se appena abbiamo impostato a "All", notifico subito la callback:
        if (selectedTag.equals("All", ignoreCase = true)) {
            onTagSelected("All")
        }
    }
}
