package app.wefridge.parse.application.model

import android.os.Bundle
import app.wefridge.parse.*
import com.parse.ParseClassName
import com.parse.ParseGeoPoint
import com.parse.ParseObject
import com.parse.ParseUser
import com.parse.ktx.getIntOrNull
import java.util.*

@ParseClassName("Items")
class Item : ParseObject() {
    var name: String
        get() = getString(ITEM_NAME) ?: ""
        set(value) = put(ITEM_NAME, value)

    var description: String?
        get() = getString(ITEM_DESCRIPTION)
        set(value) = put(ITEM_DESCRIPTION, value!!)

    var isShared: Boolean
        get() = getBoolean(ITEM_IS_SHARED)
        set(value) = put(ITEM_IS_SHARED, value)

    var quantity: Long
        get() = getLong(ITEM_QUANTITY)
        set(value) = put(ITEM_QUANTITY, value)

    var unit: Unit
        get() = Unit.getByValue(getIntOrNull(ITEM_UNIT) ?: Unit.PIECE.value)!!
        set(value) = put(ITEM_UNIT, value.value)

    var bestByDate: Date?
        get() = getDate(ITEM_BEST_BY)
        set(value) = put(ITEM_BEST_BY, value!!)

    var location: ParseGeoPoint?
        get() = getParseGeoPoint(ITEM_LOCATION)
        set(value) = put(ITEM_LOCATION, value!!)

    var contactName: String?
        get() = getString(ITEM_CONTACT_NAME)
        set(value) = put(ITEM_CONTACT_NAME, value!!)

    var contactEmail: String?
        get() = getString(ITEM_CONTACT_EMAIL)
        set(value) = put(ITEM_CONTACT_EMAIL, value!!)

    var owner: ParseUser
        get() = getParseUser(ITEM_OWNER)!!
        set(value) = put(ITEM_OWNER, value)

    var distance: Double = 0.0

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.putDouble("_distance", distance)
    }

    override fun onRestoreInstanceState(savedState: Bundle?) {
        super.onRestoreInstanceState(savedState)
        distance = savedState?.getDouble("_distance") ?: 0.0
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Item)
            return false

        if (other.objectId == null || objectId == null)
            return false

        return other.objectId == objectId
    }
}