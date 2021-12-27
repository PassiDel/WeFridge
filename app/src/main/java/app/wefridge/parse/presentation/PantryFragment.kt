package app.wefridge.parse.presentation

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import app.wefridge.parse.R
import app.wefridge.parse.application.model.Item
import app.wefridge.parse.application.model.ItemController
import app.wefridge.parse.databinding.FragmentPantryListBinding
import app.wefridge.parse.displayToastOnInternetUnavailable
import com.parse.ParseUser
import com.parse.livequery.SubscriptionHandling


/**
 * A fragment representing a list of Foodstuff items.
 */
class PantryFragment : Fragment() {

    private var _binding: FragmentPantryListBinding? = null
    private val binding get() = _binding!!
    private val values = ArrayList<Item>()
    private var snapshotListener: (() -> kotlin.Unit)? = null
    private val recyclerViewAdapter = ItemRecyclerViewAdapter(values, R.id.action_from_list_to_edit)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPantryListBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = binding.list

        with(recyclerView) {
            layoutManager = LinearLayoutManager(context)
            adapter = recyclerViewAdapter
        }

        binding.fab.setOnClickListener {
            findNavController().navigate(R.id.action_from_list_to_edit)
        }

        val itemSwipeTouchHelper =
            ItemTouchHelper(SwipeToDeleteCallback(onSwipedToDelete = { position ->
                val deletedItem = values[position]
                ItemController.deleteItem(deletedItem, {
                    if (context != null)
                        Toast.makeText(context, "Item deleted", Toast.LENGTH_SHORT).show()
                }, { })
            }, requireContext()))

        itemSwipeTouchHelper.attachToRecyclerView(recyclerView)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            displayToastOnInternetUnavailable(requireContext())
    }


    override fun onStart() {
        super.onStart()

        ParseUser.getCurrentUser() ?: return

        loadItems()
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotListener?.invoke()
        snapshotListener = null
    }

    private fun loadItems() {
        if (snapshotListener != null)
            return
//        snapshotListener?.invoke()
        with(values) {
            val oldSize = size
            clear()
            recyclerViewAdapter.notifyItemRangeRemoved(0, oldSize)
        }

        snapshotListener = ItemController.getItemsSnapshot { item, type ->
            activity?.runOnUiThread {
                when (type) {
                    SubscriptionHandling.Event.CREATE, SubscriptionHandling.Event.ENTER -> {
                        values.add(item!!)
                        recyclerViewAdapter.notifyItemInserted(values.size - 1)
                    }
                    SubscriptionHandling.Event.UPDATE -> run {
                        val index = values.indexOf(item!!)
                        if (index < 0)
                            return@run
                        values[index] = item
                        recyclerViewAdapter.notifyItemChanged(index)
                    }
                    SubscriptionHandling.Event.DELETE, SubscriptionHandling.Event.LEAVE -> run {
                        val index = values.indexOf(item!!)
                        if (index < 0)
                            return@run
                        values.removeAt(index)
                        recyclerViewAdapter.notifyItemRemoved(index)
                    }
                }
            }
        }
    }
}