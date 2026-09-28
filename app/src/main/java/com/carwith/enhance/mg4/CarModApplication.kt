package com.carwith.enhance.mg4

import android.app.Application
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/**
 * 模块 App 侧 Application：注册 XposedServiceHelper，用于跨进程写 RemotePreferences。
 */
class CarModApplication : Application(), XposedServiceHelper.OnServiceListener {

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        Companion.service = service
        Log.i(TAG, "service bound: ${service.frameworkName} ${service.frameworkVersion}")
    }

    override fun onServiceDied(service: XposedService) {
        Companion.service = null
        Log.w(TAG, "service died")
    }

    companion object {
        private const val TAG = "CarWith-MG4"

        @Volatile
        var service: XposedService? = null
            private set
    }
}