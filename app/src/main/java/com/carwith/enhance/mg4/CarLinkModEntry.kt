package com.carwith.enhance.mg4

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CarWith 增强 · MG4 —— LSPosed 模块入口
 *
 * 目标：com.miui.carlink 4.0.14（仅主进程）
 * 功能：①手机投屏卡片 ②极简/镜像切换 ③经典卡片桌面图标(可开关) ④地图DPI放大(可调) ⑤车机截屏按钮
 *
 * 移植自 LSPilot v2.5.0 脚本（已验证稳定）。
 */
class CarLinkModEntry : XposedModule() {

    private val installed = AtomicBoolean(false)

    @Volatile
    private var loadedProcess: String? = null

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        loadedProcess = param.processName
        log(Log.INFO, "event=module_loaded process=${param.processName} api=${apiVersion} framework=${frameworkName} version=${frameworkVersion}")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        // 只 hook 主进程；:local / :remote 辅助进程忽略
        if (param.packageName != TARGET_PACKAGE) return
        if (loadedProcess != TARGET_PROCESS) {
            log(Log.INFO, "event=skip_process process=$loadedProcess")
            return
        }
        installHooks(param.classLoader)
    }

    private fun installHooks(classLoader: ClassLoader) {
        if (!installed.compareAndSet(false, true)) {
            log(Log.INFO, "event=install_skipped reason=already_installed")
            return
        }

        // 读取跨进程配置（模块 App 侧通过 RemotePreferences 写入）
        val prefs = try {
            getRemotePreferences(PREFS_NAME)
        } catch (t: Throwable) {
            log(Log.WARN, "event=remote_prefs_unavailable", t)
            null
        }

        try {
            HookInstaller.install(this, classLoader, prefs)
            log(Log.INFO, "event=hooks_installed")
        } catch (t: Throwable) {
            installed.set(false)
            log(Log.ERROR, "event=install_failed", t)
        }
    }

    private fun log(level: Int, msg: String, t: Throwable? = null) {
        val line = "[CarMod] $msg"
        if (t != null) {
            Log.println(level, TAG, line + " :: " + Log.getStackTraceString(t))
        } else {
            Log.println(level, TAG, line)
        }
    }

    companion object {
        const val TAG = "CarWith-MG4"
        const val TARGET_PACKAGE = "com.miui.carlink"
        const val TARGET_PROCESS = "com.miui.carlink"
        const val PREFS_NAME = "default"

        // 配置 key（与模块 App 侧保持一致）
        const val KEY_DPI_SCALE = "map_dpi_scale"
        const val KEY_CLASSIC_CARD = "classic_card_enabled"
        const val KEY_CAST_FPS = "cast_fps"
        const val KEY_PHONE_CAST_CARD = "phone_cast_card"

        // 默认值
        const val DEFAULT_DPI_SCALE = 1.3f
        const val DEFAULT_CLASSIC_CARD = true
        const val DEFAULT_CAST_FPS = -1  // -1 = 跟随 CarWith，不干预
        const val DEFAULT_PHONE_CAST_CARD = true
    }
}