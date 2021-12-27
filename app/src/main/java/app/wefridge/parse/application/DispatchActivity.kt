package app.wefridge.parse.application

import com.parse.ui.login.ParseLoginDispatchActivity

class DispatchActivity : ParseLoginDispatchActivity() {
    override fun getTargetClass(): Class<*> {
        return MainActivity::class.java
    }


}