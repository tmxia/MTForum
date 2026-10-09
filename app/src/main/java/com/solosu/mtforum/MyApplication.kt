package com.solosu.mtforum

import android.app.Application

import com.solosu.mtforum.util.AiLog
import com.solosu.mtforum.network.HttpClient
import com.solosu.mtforum.util.CrashHandler





class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        
        com.solosu.mtforum.util.ThemeManager.init(this)

        
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                com.solosu.mtforum.util.ToastUtil.setTopActivity(activity)
            }
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {
                com.solosu.mtforum.util.ToastUtil.setTopActivity(activity)
            }
            override fun onActivityStarted(activity: android.app.Activity) {
                com.solosu.mtforum.util.ToastUtil.setTopActivity(activity)
            }
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {
                com.solosu.mtforum.util.ToastUtil.clearIfCurrent(activity)
            }
        })

        
        
        HttpClient.getInstance().init(this)

        
        AiLog.attach(this)
        
        AiLog.i("app", "应用已启动，运行日志开始记录")

        
        CrashHandler.getInstance().init(this)
    }
}
