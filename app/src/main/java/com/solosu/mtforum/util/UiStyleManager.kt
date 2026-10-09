package com.solosu.mtforum.util

import android.app.Activity
import android.content.Context


object UiStyleManager {
    private const val PREF = "ui_style_prefs"
    private const val KEY = "ui_style"      

    fun isIos(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        
        val migratedKey = "ui_style_migrated_to_ios_v1"
        if (!prefs.getBoolean(migratedKey, false)) {
            prefs.edit()
                .putString(KEY, "ios")
                .putBoolean(migratedKey, true)
                .apply()
            return true
        }
        return prefs.getString(KEY, "ios") == "ios"
    }

    fun setIos(ctx: Context, ios: Boolean) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, if (ios) "ios" else "material").apply()
    }

    
    fun applyTheme(activity: Activity) {
        val target = if (isIos(activity)) {
            com.solosu.mtforum.R.style.Theme_AppTheme_iOS
        } else {
            com.solosu.mtforum.R.style.Theme_AppTheme
        }
        activity.setTheme(target)
    }
}
