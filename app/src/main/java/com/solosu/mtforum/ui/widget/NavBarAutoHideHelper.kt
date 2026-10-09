package com.solosu.mtforum.ui.widget

import android.app.Activity







object NavBarAutoHideHelper {

    


    @JvmStatic
    fun onScrolled(activity: Activity?, dy: Int) {
        if (activity is com.solosu.mtforum.MainActivity) {
            activity.onNavScroll(dy)
        }
    }
}
