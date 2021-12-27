package app.wefridge.parse.application.model

import android.content.SharedPreferences
import app.wefridge.parse.ITEM_OWNER
import app.wefridge.parse.presentation.SETTINGS_EMAIL
import app.wefridge.parse.presentation.SETTINGS_NAME
import com.parse.ParseUser

const val SETTINGS_TOPIC = "SETTINGS_TOPIC"

class UserController {
    companion object {

        fun getLocalEmail(sp: SharedPreferences): String {
            return sp.getString(SETTINGS_EMAIL, getCurrentUser().email)!!
        }

        fun getLocalName(sp: SharedPreferences): String {
            return sp.getString(SETTINGS_NAME, getCurrentUser().getString("name") ?: "")!!
        }

        fun getCurrentUser() = ParseUser.getCurrentUser()!!

        fun getCurrentOwner() = getCurrentUser().getParseObject("owner") as ParseUser? ?: getCurrentUser()

        fun getUserFromEmail(
            email: String,
            onSuccess: (ParseUser?) -> kotlin.Unit,
            onFailure: (Exception) -> kotlin.Unit
        ) {
            ParseUser.getQuery()
                .whereEqualTo("email", email)
                .setLimit(1)
                .findInBackground { users, e ->
                    if (e != null)
                        return@findInBackground onFailure(e)
                    if (users.isEmpty())
                        return@findInBackground onSuccess(null)

                    onSuccess(users[0])
                }
        }

        fun setOwner(
            user: String,
            onSuccess: () -> kotlin.Unit,
            onFailure: (Exception) -> kotlin.Unit
        ) {
            // TODO
//            val ownerField = hashMapOf<String, Any>(
//                "owner" to (owner ?: getCurrentUserRef())
//            )
//
//            user.update(ownerField)
//                .addOnSuccessListener { onSuccess() }
//                .addOnFailureListener(onFailure)
        }

        fun removeOwner(
            user: String,
            onSuccess: () -> kotlin.Unit,
            onFailure: (Exception) -> kotlin.Unit
        ) {
            // TODO
//            val ownerField = hashMapOf<String, Any>(
//                "owner" to FieldValue.delete()
//            )
//
//            user.update(ownerField)
//                .addOnSuccessListener { onSuccess() }
//                .addOnFailureListener(onFailure)
        }

        fun getUsersParticipants(
            onSuccess: (List<ParseUser>?) -> kotlin.Unit,
            onFailure: (Exception) -> kotlin.Unit
        ) {
            ParseUser.getQuery()
                .whereEqualTo(ITEM_OWNER, getCurrentUser())
                .findInBackground { objects, e ->
                    if (e != null)
                        return@findInBackground onFailure(e)
                    if (objects.isEmpty())
                        return@findInBackground onSuccess(null)

                    onSuccess(objects)
                }
        }
    }
}