package app.wefridge.parse.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import app.wefridge.parse.application.model.Item
import app.wefridge.parse.databinding.FragmentPantryBinding
import app.wefridge.parse.getBestByString

const val ARG_MODEL = "model"
/**
 * [RecyclerView.Adapter] that can display a [Item].
 * TODO: Replace the implementation with code for your data type.
 */
class ItemRecyclerViewAdapter(
    private val values: List<Item>,
    @IdRes private val clickAction: Int? = null
) : RecyclerView.Adapter<ItemRecyclerViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val viewHolder = ViewHolder(
            FragmentPantryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
        if (clickAction != null) {
            viewHolder.itemView.setOnClickListener {
                val pos = viewHolder.absoluteAdapterPosition
                val bundle = Bundle()
                bundle.putParcelable(ARG_MODEL, values[pos])

                parent.findNavController().navigate(clickAction, bundle)
            }
        }

        return viewHolder

    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = values[position]
        holder.contentView.text = item.name
        holder.bestByView.text = getBestByString(item.bestByDate, holder.itemView.context)
        holder.sharedIcon.visibility = if (item.isShared) View.VISIBLE else View.INVISIBLE
    }

    override fun getItemCount(): Int = values.size

    inner class ViewHolder(binding: FragmentPantryBinding) : RecyclerView.ViewHolder(binding.root) {
        val contentView: TextView = binding.content
        val bestByView: TextView = binding.bestByDate
        val sharedIcon: ImageView = binding.sharedIcon


        override fun toString(): String {
            return super.toString() + " '" + contentView.text + "'"
        }
    }

}