package app.wefridge.parse.application.model

import android.util.Log
import app.wefridge.parse.*
import com.parse.ParseGeoPoint
import com.parse.ParseQuery
import com.parse.livequery.ParseLiveQueryClient
import com.parse.livequery.SubscriptionHandling


class ItemController {

    companion object {
        private const val TAG = "ItemController"

        /*
        * The functions deleteItem, overrideItem and addItem are partially
        * based on code snippets provided by the Firebase Documentation:
        * https://firebase.google.com/docs/firestore/manage-data/add-data
        * */
        fun deleteItem(
            item: Item,
            callbackOnSuccess: () -> kotlin.Unit,
            callbackOnFailure: (Exception) -> kotlin.Unit
        ) {
            if (item.objectId == null)
                return callbackOnFailure(Exception())

            item.deleteInBackground {
                if (it == null)
                    return@deleteInBackground callbackOnSuccess()

                callbackOnFailure(it)
            }
        }

        fun saveItem(
            item: Item,
            callbackOnSuccess: () -> kotlin.Unit,
            callbackOnFailure: (Exception) -> kotlin.Unit
        ) {
            item.saveInBackground {
                it?.message?.let { it1 -> Log.d(TAG, it1) }
                Log.d(TAG, "item successfully overridden!")
                callbackOnSuccess()
            }
        }

        fun getItemsSnapshot(
            listener: (Item?, SubscriptionHandling.Event) -> kotlin.Unit
        ) : () -> kotlin.Unit {
            val ownerRef = UserController.getCurrentOwner()
            val parseLiveQueryClient = ParseLiveQueryClient.Factory.getClient()

            val query = ParseQuery.getQuery(Item::class.java)
                .whereEqualTo(ITEM_OWNER, ownerRef)

            parseLiveQueryClient.subscribe(query)
                .handleEvents { query2, event, `object` ->
                    listener(`object`, event)
                }

            query.findInBackground { objects, e ->
                if (e != null)
                    return@findInBackground parseLiveQueryClient.unsubscribe(query)

                objects.forEach { listener(it, SubscriptionHandling.Event.ENTER) }
            }

            return {
                parseLiveQueryClient.unsubscribe(query)
            }
        }

        fun getNearbyItems(
            onSuccess: (MutableList<Item>) -> kotlin.Unit,
            onFailure: (Exception) -> kotlin.Unit,
            radius: Double,
            center: ParseGeoPoint
        ) {
            val ownerRef = UserController.getCurrentOwner()

//            val actualRadius = if (radius == 0.0) 500.0 else radius

            ParseQuery.getQuery(Item::class.java)
                .whereEqualTo(ITEM_IS_SHARED, true)
                .whereNotEqualTo(ITEM_OWNER, ownerRef)
                .whereNear(ITEM_LOCATION, center)
                .setLimit(10)
                .findInBackground { objects, e ->
                    if (e != null)
                        return@findInBackground onFailure(e)
                    objects.forEach { it.distance = it.location?.distanceInKilometersTo(center)
                        ?.times(1000.0)
                        ?: 0.0 }
                    onSuccess(objects)
                }
        }
    }
}