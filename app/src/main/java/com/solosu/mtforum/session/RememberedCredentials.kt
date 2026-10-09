package com.solosu.mtforum.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey





object RememberedCredentials {
    private const val PREF_NAME = "mtforum_credentials"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_ENABLED = "enabled"

    class Credentials(@JvmField var username: String, @JvmField var password: String)

    private fun prefs(context: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            
            try {
                context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            } catch (ignored: Exception) {
                null
            }
        }
    }

    @JvmStatic
    fun save(context: Context, username: String, password: String) {
        try {
            prefs(context)?.edit()
                ?.putString(KEY_USERNAME, username)
                ?.putString(KEY_PASSWORD, password)
                ?.putBoolean(KEY_ENABLED, true)
                ?.apply()
        } catch (ignored: Exception) {
        }
    }

    @JvmStatic
    fun load(context: Context): Credentials? {
        return try {
            val p = prefs(context) ?: return null
            if (!p.getBoolean(KEY_ENABLED, false)) return null
            val u = p.getString(KEY_USERNAME, null) ?: return null
            val pw = p.getString(KEY_PASSWORD, null) ?: return null
            Credentials(u, pw)
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun clear(context: Context) {
        try {
            prefs(context)?.edit()?.clear()?.apply()
        } catch (ignored: Exception) {
        }
    }
}
