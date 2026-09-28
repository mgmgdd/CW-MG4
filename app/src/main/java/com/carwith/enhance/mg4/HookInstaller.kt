package com.carwith.enhance.mg4

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hook 安装器：移植自 LSPilot v2.5.0 脚本的全部 hook 点。
 *
 * 核心机制（两条铁律）：
 * 1. v() 全局强制 true（已证伪无副作用）
 * 2. q() 只在「标志位窗口」内强制 false，绝不全局 hook
 *
 * 双标志位：
 * - cardFilterFlag：卡片过滤/应用列表期间 true，此时 q() -> false
 * - mirrFilterFlag：filterMirrCastMode 期间 true，此时 q() 保持真实（保切换）
 */
object HookInstaller {

    private const val TAG = "CarWith-MG4"

    // 三个 AtomicBoolean 标志位（核心机制，照搬 v2.5.0）
    private val cardFilterFlag = AtomicBoolean(false)
    private val mirrFilterFlag = AtomicBoolean(false)
    private val captureScreenFlag = AtomicBoolean(false)

    fun install(module: XposedModule, classLoader: ClassLoader, prefs: SharedPreferences?) {
        // 读取配置（DPI 倍数 + 经典卡片图标开关 + 投屏帧率档位 + 手机投屏卡片开关）
        val dpiScale = prefs?.getFloat(CarLinkModEntry.KEY_DPI_SCALE, CarLinkModEntry.DEFAULT_DPI_SCALE)
            ?: CarLinkModEntry.DEFAULT_DPI_SCALE
        val classicCardEnabled = prefs?.getBoolean(CarLinkModEntry.KEY_CLASSIC_CARD, CarLinkModEntry.DEFAULT_CLASSIC_CARD)
            ?: CarLinkModEntry.DEFAULT_CLASSIC_CARD
        val castFps = prefs?.getInt(CarLinkModEntry.KEY_CAST_FPS, CarLinkModEntry.DEFAULT_CAST_FPS)
            ?: CarLinkModEntry.DEFAULT_CAST_FPS
        val phoneCastCardEnabled = prefs?.getBoolean(CarLinkModEntry.KEY_PHONE_CAST_CARD, CarLinkModEntry.DEFAULT_PHONE_CAST_CARD)
            ?: CarLinkModEntry.DEFAULT_PHONE_CAST_CARD

        log("install start dpi=$dpiScale classicCard=$classicCardEnabled castFps=$castFps phoneCastCard=$phoneCastCardEnabled")

        if (phoneCastCardEnabled) {
            hookMinWindowsUtilsV(module, classLoader)
        } else {
            log("phone_cast_card disabled, skip MinWindowsUtils.v hook")
        }
        hookA3Q(module, classLoader)
        hookA3M(module, classLoader)
        hookCardFilterFlags(module, classLoader)
        if (classicCardEnabled) {
            hookControlChannel(module, classLoader)
        } else {
            log("classic_card disabled, skip ControlChannel hooks")
        }
        hookMapDpi(module, classLoader, dpiScale)
        hookCaptureScreen(module, classLoader)
        hookCastFps(module, classLoader, castFps)
        hookScreenshotFix(module, classLoader)

        log("install done")
    }

    // ========== 1. MinWindowsUtils.v() -> 强制 true（全局） ==========
    private fun hookMinWindowsUtilsV(module: XposedModule, cl: ClassLoader) {
        val clazz = findClass(cl, "com.carwith.launcher.minwindows.MinWindowsUtils") ?: run {
            log("FAIL MinWindowsUtils not found")
            return
        }
        val method = findMethodByName(clazz, "v") ?: run {
            log("FAIL MinWindowsUtils.v not found")
            return
        }
        module.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
            .intercept { chain ->
                // before：强制返回 true，不调用原方法
                log("hook v() -> true")
                true
            }
        log("OK hooked MinWindowsUtils.v() -> true")
    }

    // ========== 2. a3.q() -> 条件 false（仅标志位窗口） ==========
    private fun hookA3Q(module: XposedModule, cl: ClassLoader) {
        val clazz = findClass(cl, "com.carwith.common.utils.a3") ?: run {
            log("FAIL a3 not found")
            return
        }
        val method = findMethodByName(clazz, "q") ?: run {
            log("FAIL a3.q not found")
            return
        }
        module.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
            .intercept { chain ->
                // 仅当 cardFilterFlag 为 true 且 mirrFilterFlag 为 false 时强制 false
                if (cardFilterFlag.get() && !mirrFilterFlag.get()) {
                    log("hook q() -> false (filter window)")
                    false
                } else {
                    chain.proceed()
                }
            }
        log("OK hooked a3.q() conditional")
    }

    // ========== 2b. a3.m() -> 截屏标志窗口内强制 true ==========
    private fun hookA3M(module: XposedModule, cl: ClassLoader) {
        val clazz = findClass(cl, "com.carwith.common.utils.a3") ?: run {
            log("FAIL a3 not found (for m())")
            return
        }
        val method = findMethodByName(clazz, "m") ?: run {
            log("FAIL a3.m not found")
            return
        }
        module.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
            .intercept { chain ->
                // 仅在通知构建截图按钮期间强制 true，其余调用不受影响
                if (captureScreenFlag.get()) {
                    log("hook a3.m() -> true (capture window)")
                    true
                } else {
                    chain.proceed()
                }
            }
        log("OK hooked a3.m() capture conditional")
    }

    // ========== 3. 卡片过滤三处设置 cardFilterFlag ==========
    private fun hookCardFilterFlags(module: XposedModule, cl: ClassLoader) {
        // 3a. AddCardFragment.g0
        flagWindow(module, cl, "com.carwith.launcher.settings.car.fragment.AddCardFragment", "g0", "AddCardFragment.g0")
        // 3b. CustomCardViewModel.d
        flagWindow(module, cl, "com.carwith.launcher.settings.car.activity.bean.viewmodel.CustomCardViewModel", "d", "CustomCardViewModel.d")
        // 3c. d4.j.b（混淆）
        flagWindow(module, cl, "d4.j", "b", "d4.j.b")
    }

    private fun flagWindow(module: XposedModule, cl: ClassLoader, className: String, methodName: String, label: String) {
        val clazz = findClass(cl, className) ?: run {
            log("FAIL $label class not found")
            return
        }
        val method = findMethodByName(clazz, methodName) ?: run {
            log("FAIL $label method not found")
            return
        }
        module.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
            .intercept { chain ->
                cardFilterFlag.set(true)
                try {
                    chain.proceed()
                } finally {
                    cardFilterFlag.set(false)
                }
            }
        log("OK flag window $label")
    }

    // ========== 4. ControlChannel（经典卡片图标 + 覆盖标志） ==========
    private fun hookControlChannel(module: XposedModule, cl: ClassLoader) {
        val clazz = findClass(cl, "com.miui.carlink.databus.ControlChannel") ?: run {
            log("FAIL ControlChannel not found")
            return
        }

        // 4a. getAllAppData -> cardFilterFlag 窗口（图标出现）
        val getAllAppData = findMethodByName(clazz, "getAllAppData")
        if (getAllAppData != null) {
            module.hook(getAllAppData)
                .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                .intercept { chain ->
                    cardFilterFlag.set(true)
                    try {
                        chain.proceed()
                    } finally {
                        cardFilterFlag.set(false)
                    }
                }
            log("OK flag window ControlChannel.getAllAppData")
        } else {
            log("FAIL ControlChannel.getAllAppData not found")
        }

        // 4b. filterMirrCastMode -> mirrFilterFlag 窗口（保切换）
        val filterMirr = findMethodByName(clazz, "filterMirrCastMode")
        if (filterMirr != null) {
            module.hook(filterMirr)
                .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                .intercept { chain ->
                    mirrFilterFlag.set(true)
                    try {
                        chain.proceed()
                    } finally {
                        mirrFilterFlag.set(false)
                    }
                }
            log("OK flag window ControlChannel.filterMirrCastMode")
        } else {
            log("FAIL ControlChannel.filterMirrCastMode not found")
        }
    }

    // ========== 5. MapConfigManager.m() -> DPI 放大 ==========
    private fun hookMapDpi(module: XposedModule, cl: ClassLoader, dpiScale: Float) {
        val clazz = findClass(cl, "com.carwith.launcher.map.MapConfigManager") ?: run {
            log("FAIL MapConfigManager not found")
            return
        }
        val method = findMethodByName(clazz, "m") ?: run {
            log("FAIL MapConfigManager.m not found")
            return
        }
        module.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
            .intercept { chain ->
                val original = chain.proceed()
                if (original is Number) {
                    val oi = original.toInt()
                    val ni = (oi * dpiScale).toInt()
                    if (ni != oi) {
                        log("DPI m()=$oi -> $ni (scale=$dpiScale)")
                        return@intercept ni
                    }
                }
                original
            }
        log("OK hooked MapConfigManager.m() dpi x$dpiScale")
    }

    // ========== 6. 车机截屏按钮（通知构建期间放行 a3.m()） ==========
    private fun hookCaptureScreen(module: XposedModule, cl: ClassLoader) {
        // 6a. b0.a(Context, Builder)：普通投屏通知构建截图按钮
        val b0 = findClass(cl, "com.miui.carlink.castfwk.b0")
        if (b0 != null) {
            val method = findMethodByName(b0, "a")
            if (method != null) {
                module.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                    .intercept { chain ->
                        captureScreenFlag.set(true)
                        try {
                            chain.proceed()
                        } finally {
                            captureScreenFlag.set(false)
                        }
                    }
                log("OK flag window b0.a (capture)")
            } else {
                log("FAIL b0.a not found")
            }
        } else {
            log("FAIL b0 not found")
        }

        // 6b. l0.m()：焦点通知写截图 action
        val l0 = findClass(cl, "com.miui.carlink.castfwk.l0")
        if (l0 != null) {
            val method = findMethodByName(l0, "m")
            if (method != null) {
                module.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                    .intercept { chain ->
                        captureScreenFlag.set(true)
                        try {
                            chain.proceed()
                        } finally {
                            captureScreenFlag.set(false)
                        }
                    }
                log("OK flag window l0.m (capture)")
            } else {
                log("FAIL l0.m not found")
            }
        } else {
            log("FAIL l0 not found")
        }
    }

    // ========== 7. CastFrameRateLimiter.run() -> 投屏帧率档位 ==========
    // 逆向结论（2026-09-28 MCP）：
    //   f0$a.run() = CastFrameRateLimiter.run()，投屏时把 persist.sys.carlink.fps 写为 120
    //   （smali: const/16 p0, 0x78 ; u1.j0("persist.sys.carlink.fps", "120")）
    // 方案：hook run() 的调用者 f0$a.run()，before 里按配置档位替换原方法返回值，
    //   「跟随 CarWith」（-1）时不干预，其余档位强制返回对应值。
    private fun hookCastFps(module: XposedModule, cl: ClassLoader, castFps: Int) {
        if (castFps <= 0) {
            log("cast_fps follow mode (-1), skip hook")
            return
        }
        val clazz = findClass(cl, "com.carwith.common.utils.f0\$a") ?: run {
            log("FAIL f0\$a (CastFrameRateLimiter) not found")
            return
        }
        val method = findMethodByName(clazz, "run") ?: run {
            log("FAIL f0\$a.run not found")
            return
        }
        module.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
            .intercept { chain ->
                // before：run() 无返回值（void），这里不拦截返回值；
                // 实际生效点是把 u1.j0 的写入替换——但因为 run() 内部直接写死 120，
                // 更精确的做法是 hook u1.j0 写属性处，见下方 hookSystemPropertyFps。
                log("cast_fps: f0\$a.run() called, mode=$castFps")
                chain.proceed()
            }
        log("OK hooked f0\$a.run() fps=$castFps (log only)")
        // 真正改值的 hook：u1.j0("persist.sys.carlink.fps", "120") 写属性时替换
        hookSystemPropertyFps(module, cl, castFps)
    }

    // ========== 7b. u1.j0 -> 写 persist.sys.carlink.fps 时替换值 ==========
    // u1.j0(key, value) 是 SystemProperties.set 的反射封装（Reflector.mSetSystemProperty）
    // 只在 key == "persist.sys.carlink.fps" 且配置了固定档位时替换 value。
    // 实现：构造替换后的参数数组传给 chain.proceed()，不改动原 args（libxposed 兼容写法）。
    private fun hookSystemPropertyFps(module: XposedModule, cl: ClassLoader, castFps: Int) {
        val clazz = findClass(cl, "com.carwith.common.utils.u1") ?: run {
            log("FAIL u1 not found")
            return
        }
        val method = findMethodByName(clazz, "j0") ?: run {
            log("FAIL u1.j0 not found")
            return
        }
        module.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
            .intercept { chain ->
                val args = chain.args
                if (args != null && args.size >= 2) {
                    val key = args[0] as? String
                    if (key == "persist.sys.carlink.fps") {
                        val oldVal = args[1] as? String ?: ""
                        log("cast_fps: set $key=$oldVal -> $castFps")
                        // 直接改 args 数组（libxposed args 是可写数组），再原样 proceed
                        args[1] = castFps.toString()
                    }
                }
                chain.proceed()
            }
        log("OK hooked u1.j0() fps rewrite -> $castFps")
    }

    // ========== 8. 截屏修复：全屏经典卡片模式下 PixelCopy 截 Surface 不可靠 ==========
    // 逆向结论（2026-09-28 MCP）：
    //   截图链路 CaptureScreenReceiver.a() → t5/f.m() → 按 SDK 分流：
    //     SDK==34 → j(宽,高) → u1.b0() → android.window.ScreenCapture.captureDisplay（官方，可靠）
    //     SDK!=34 → i(1000) → d2/b PixelCopy.request(Surface)（依赖缓存 Surface，全屏经典卡片模式
    //               下 Surface 尺寸/内容与虚拟显示不匹配 → 截图异常）
    //   本机 Android 17(SDK37) 走 i()。修复：hook t5/f.i()，拦截后转调 t5/f.j(宽,高) 走官方截屏，
    //   失败回退原逻辑（chain.proceed），绝不恶化。
    // 第二次根因（2026-09-29 MCP 复查 u1.b0() smali）：
    //   u1.b0() 内部用 Rect(0,0,w,h) 作 setSourceCrop 再 captureDisplay；
    //   若 w/h 取 d2/b（CastCaptureConfig）竖屏 Surface 尺寸 → 横屏 display 只截左上角、竖屏比例。
    //   正确宽高来源：e4/a（CarlinkServiceConnector）g() 单例 → f() 拿 Display → getRealSize()。
    private fun hookScreenshotFix(module: XposedModule, cl: ClassLoader) {
        // 目标类：t5/f（CaptureScreenshotManager）
        val clazz = findClass(cl, "t5.f") ?: run {
            log("FAIL t5.f (CaptureScreenshotManager) not found")
            return
        }
        // i(J)Landroid/graphics/Bitmap; —— PixelCopy 截图（被 hook 目标）
        val methodI = findMethodByName(clazz, "i") ?: run {
            log("FAIL t5.f.i not found")
            return
        }
        // j(II)Landroid/graphics/Bitmap; —— ScreenCapture 官方截图
        val methodJ = findMethodByName(clazz, "j") ?: run {
            log("FAIL t5.f.j not found")
            return
        }
        // h()Lt5/f; —— 静态单例方法（不依赖 thisObject，稳定）
        val methodH = findMethodByName(clazz, "h") ?: run {
            log("FAIL t5.f.h (singleton) not found")
            return
        }
        // e4/a（CarlinkServiceConnector）单例：g() 静态拿实例，f() 拿 Display
        val connectorClazz = findClass(cl, "e4.a") ?: run {
            log("FAIL e4.a (CarlinkServiceConnector) not found")
            return
        }
        val methodG = findMethodByName(connectorClazz, "g") ?: run {
            log("FAIL e4.a.g not found")
            return
        }
        val methodF = findMethodByName(connectorClazz, "f") ?: run {
            log("FAIL e4.a.f (getDisplay) not found")
            return
        }

        module.hook(methodI)
            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
            .intercept { chain ->
                if (methodJ == null || methodH == null || methodG == null || methodF == null) {
                    return@intercept chain.proceed()
                }
                try {
                    // 拿 t5/f 单例（h() 静态方法），避免依赖 thisObject
                    val singleton = methodH.invoke(null)
                    if (singleton == null) return@intercept chain.proceed()

                    // 拿 CarlinkServiceConnector 单例 → Display → 真实尺寸（横屏完整画面）
                    val connector = methodG.invoke(null)
                    if (connector == null) return@intercept chain.proceed()
                    val display = methodF.invoke(connector) as? android.view.Display
                    if (display == null) return@intercept chain.proceed()

                    val realSize = android.graphics.Point()
                    display.getRealSize(realSize)
                    val w = realSize.x
                    val h = realSize.y
                    if (w <= 0 || h <= 0) return@intercept chain.proceed()
                    log("screenshot_fix: try ScreenCapture path ${w}x$h (display realSize)")
                    // 调 t5/f.j(w, h) 走官方截屏（u1.b0() 用 crop Rect(0,0,w,h) 截整个显示）
                    val bmp = methodJ.invoke(singleton, w, h)
                    if (bmp != null) {
                        log("screenshot_fix: ScreenCapture OK")
                        return@intercept bmp
                    }
                    log("screenshot_fix: ScreenCapture returned null, fallback")
                } catch (t: Throwable) {
                    log("screenshot_fix: exception, fallback -> ${t.message}")
                }
                chain.proceed()
            }
        log("OK hooked t5.f.i() screenshot fix (display realSize)")
    }

    // ========== 工具方法 ==========
    private fun findClass(cl: ClassLoader, name: String): Class<*>? {
        return try {
            cl.loadClass(name)
        } catch (t: Throwable) {
            null
        }
    }

    private fun findMethodByName(clazz: Class<*>, name: String): Method? {
        return try {
            clazz.declaredMethods.firstOrNull { it.name == name }?.also { it.isAccessible = true }
        } catch (t: Throwable) {
            null
        }
    }

    private fun log(msg: String) {
        Log.println(Log.INFO, TAG, "[CarMod] $msg")
    }
}