package app.wefridge.parse.application

import android.app.Application
import app.wefridge.parse.R
import app.wefridge.parse.application.model.Item
import com.parse.Parse
import com.parse.ParseInstallation
import com.parse.ParseObject


class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Parse.setLogLevel(Parse.LOG_LEVEL_DEBUG)
        ParseObject.registerSubclass(Item::class.java)
        Parse.initialize(
            Parse.Configuration.Builder(this)
                .applicationId(getString(R.string.parse_app_id))
                .clientKey(getString(R.string.parse_client_key))
                .server(getString(R.string.parse_app_url))
                .build()
        )
        val installation = ParseInstallation.getCurrentInstallation()
        installation.put("GCMSenderId", getString(R.string.GCMSenderId))
        installation.saveInBackground()
    }
}