package app.wefridge.parse.application.model

import app.wefridge.parse.md5
import com.parse.ParseUser

val ParseUser.image: String
    get() {
        return getString("image") ?: "https://www.gravatar.com/avatar/${email.md5()}?s=64&d=wavatar"
    }

var ParseUser.owner: ParseUser?
    get() = getParseUser("owner")
    set(value) = put("owner", value!!)

val ParseUser.name: String
    get() = getString("name") ?: ""