package com.carwith.enhance.mg4

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiuixTheme(
                colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                SettingsScreen()
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    var dpiScale by remember { mutableStateOf(loadDpiScale()) }
    var classicCard by remember { mutableStateOf(loadClassicCard()) }
    var castFps by remember { mutableStateOf(loadCastFps()) }
    var phoneCastCard by remember { mutableStateOf(loadPhoneCastCard()) }
    var page by remember { mutableStateOf(Page.Main) }

    val context = LocalContext.current
    var hideIcon by remember { mutableStateOf(isIconHidden(context)) }

    // 子页面时拦截系统返回键，回到上一级（主页面）
    BackHandler(enabled = page != Page.Main) {
        page = Page.Main
    }

    // 页面切换动画：进子页从右滑入+淡入，返回向左滑出+淡出
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (targetState != Page.Main) {
                // 进入子页：新页面从右滑入
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 3 } + fadeOut())
            } else {
                // 返回主页面：主页面从左滑入
                (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { it / 3 } + fadeOut())
            }
        }
    ) { target ->
        when (target) {
            Page.Main -> MainPage(
                context = context,
                dpiScale = dpiScale,
                classicCard = classicCard,
                castFps = castFps,
                phoneCastCard = phoneCastCard,
                hideIcon = hideIcon,
                onDpiScaleChange = { dpiScale = it; saveDpiScale(it) },
                onClassicCardChange = {
                    classicCard = it
                    saveClassicCard(it)
                    hideIcon = isIconHidden(context)
                },
                onCastFpsChange = { castFps = it; saveCastFps(it) },
                onPhoneCastCardChange = {
                    phoneCastCard = it
                    savePhoneCastCard(it)
                },
                onHideIconChange = {
                    hideIcon = it
                    setIconHidden(context, it)
                },
                onOpenReward = { page = Page.Reward },
                onOpenLicense = { page = Page.License }
            )
            Page.Reward -> RewardScreen(onBack = { page = Page.Main })
            Page.License -> LicenseScreen(onBack = { page = Page.Main })
        }
    }
}

private enum class Page { Main, Reward, License }

@Composable
private fun MainPage(
    context: android.content.Context,
    dpiScale: Float,
    classicCard: Boolean,
    castFps: Int,
    phoneCastCard: Boolean,
    hideIcon: Boolean,
    onDpiScaleChange: (Float) -> Unit,
    onClassicCardChange: (Boolean) -> Unit,
    onCastFpsChange: (Int) -> Unit,
    onPhoneCastCardChange: (Boolean) -> Unit,
    onHideIconChange: (Boolean) -> Unit,
    onOpenReward: () -> Unit,
    onOpenLicense: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = "CarWith 增强 · MG4",
                actions = {
                    // 右上角汉堡菜单（miuix 官方 OverlayIconDropdownMenu）
                    OverlayIconDropdownMenu(
                        entries = listOf(
                            DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = "打开 CarWith 应用信息",
                                        onClick = { openAppInfo(context, "com.miui.carlink") }
                                    )
                                )
                            )
                        )
                    ) {
                        Text(
                            text = "⋮",
                            fontSize = 22.sp,
                            color = if (isSystemInDarkTheme()) Color.White else Color.Black,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Card {
                SmallTitle(text = "功能")
                SliderPreference(
                    value = dpiScale,
                    onValueChange = { newValue ->
                        onDpiScaleChange(newValue)
                    },
                    title = "地图 DPI 缩放",
                    summary = "当前 %.1fx，默认 1.3x".format(dpiScale),
                    valueRange = 0.5f..3.0f,
                    steps = 24
                )
                SliderPreference(
                    value = castFpsToSlider(castFps),
                    onValueChange = { newPos ->
                        onCastFpsChange(sliderToCastFps(newPos))
                    },
                    title = "投屏帧率（实验功能，不一定生效）",
                    summary = "当前 ${castFpsLabel(castFps)}",
                    valueRange = 0f..4f,
                    steps = 3
                )
                SwitchPreference(
                    checked = phoneCastCard,
                    onCheckedChange = { checked ->
                        onPhoneCastCardChange(checked)
                    },
                    title = "手机投屏卡片",
                    summary = "在经典卡片桌面中开启被隐藏的「手机投屏」卡片"
                )
                SwitchPreference(
                    checked = classicCard,
                    onCheckedChange = { checked ->
                        onClassicCardChange(checked)
                    },
                    title = "经典卡片桌面图标",
                    summary = "在车机应用列表里显示，一键切换到全屏界面。"
                )
                SwitchPreference(
                    checked = hideIcon,
                    onCheckedChange = { checked ->
                        onHideIconChange(checked)
                    },
                    title = "隐藏桌面图标",
                    summary = "隐藏后可从 LSPosed 管理器进入"
                )
            }

            Card {
                SmallTitle(text = "关于")
                ArrowPreference(
                    title = "版本",
                    summary = "2.6.0",
                    endActions = {}
                )
                ArrowPreference(
                    title = "适配的 CarWith 版本",
                    summary = "4.0.14 · 其他版本自测",
                    endActions = {}
                )
                ArrowPreference(
                    title = "作者",
                    summary = "酷安@蘑菇蘑菇蛋蛋",
                    endActions = {}
                )
                ArrowPreference(
                    title = "致谢开源项目",
                    summary = "miuix · libxposed · Compose 等",
                    onClick = { onOpenLicense() }
                )
            }

            // 底部打赏入口：灰色居中
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clickable { onOpenReward() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "如果这个应用帮到了你，可以点击此处打赏作者",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}

// 打开指定应用的系统「应用信息」页
private fun openAppInfo(context: Context, packageName: String) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (t: Throwable) {
        Toast.makeText(context, "无法打开应用信息", Toast.LENGTH_SHORT).show()
    }
}

// 打开外部链接（浏览器）
private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (t: Throwable) {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}

// 是否隐藏桌面图标（alias 组件被禁用 = 隐藏）
private fun isIconHidden(context: Context): Boolean {
    return try {
        val cn = ComponentName(context, "com.carwith.enhance.mg4.MainActivityLauncher")
        context.packageManager.getComponentEnabledSetting(cn) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    } catch (t: Throwable) {
        false
    }
}

private fun setIconHidden(context: Context, hidden: Boolean) {
    try {
        val cn = ComponentName(context, "com.carwith.enhance.mg4.MainActivityLauncher")
        val state = if (hidden) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        context.packageManager.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
    } catch (t: Throwable) {
        // 忽略，避免影响其它功能
    }
}

@Composable
fun RewardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "支持作者",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            MiuixIcons.Back,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.mm_reward_qrcode),
                contentDescription = "打赏二维码",
                modifier = Modifier
                    .size(280.dp)
                    .clickable {
                        Toast.makeText(context, "~微信捐赠码~", Toast.LENGTH_SHORT).show()
                    }
            )
            Text(
                text = "插件完全免费，赞赏与否不影响插件功能",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun LicenseScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "致谢",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            MiuixIcons.Back,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // 感谢说明文字
            Text(
                text = "感谢以下开源项目让这个工具成为可能",
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            Card {
                SmallTitle(text = "开源组件")
                ArrowPreference(
                    title = "miuix",
                    summary = "compose-miuix-ui/miuix · Apache 2.0",
                    onClick = { openUrl(context, "https://github.com/compose-miuix-ui/miuix") }
                )
                ArrowPreference(
                    title = "libxposed",
                    summary = "libxposed/api · Apache 2.0",
                    onClick = { openUrl(context, "https://github.com/libxposed/api") }
                )
                ArrowPreference(
                    title = "JetBrains Compose Multiplatform",
                    summary = "JetBrains · Apache 2.0",
                    onClick = { openUrl(context, "https://github.com/JetBrains/compose-multiplatform") }
                )
                ArrowPreference(
                    title = "AndroidX",
                    summary = "AOSP · Apache 2.0",
                    onClick = { openUrl(context, "https://developer.android.com/jetpack/androidx") }
                )
                ArrowPreference(
                    title = "Kotlin",
                    summary = "JetBrains · Apache 2.0",
                    onClick = { openUrl(context, "https://github.com/JetBrains/kotlin") }
                )
            }
        }
    }
}

// ========== 配置读写（通过 XposedService 的 RemotePreferences） ==========

private fun prefs() = CarModApplication.service?.getRemotePreferences("default")

private fun loadDpiScale(): Float {
    return prefs()?.getFloat(CarLinkModEntry.KEY_DPI_SCALE, CarLinkModEntry.DEFAULT_DPI_SCALE)
        ?: CarLinkModEntry.DEFAULT_DPI_SCALE
}

private fun loadClassicCard(): Boolean {
    return prefs()?.getBoolean(CarLinkModEntry.KEY_CLASSIC_CARD, CarLinkModEntry.DEFAULT_CLASSIC_CARD)
        ?: CarLinkModEntry.DEFAULT_CLASSIC_CARD
}

private fun saveDpiScale(value: Float) {
    prefs()?.edit()?.putFloat(CarLinkModEntry.KEY_DPI_SCALE, value)?.apply()
}

private fun saveClassicCard(value: Boolean) {
    prefs()?.edit()?.putBoolean(CarLinkModEntry.KEY_CLASSIC_CARD, value)?.apply()
}

private fun loadPhoneCastCard(): Boolean {
    return prefs()?.getBoolean(CarLinkModEntry.KEY_PHONE_CAST_CARD, CarLinkModEntry.DEFAULT_PHONE_CAST_CARD)
        ?: CarLinkModEntry.DEFAULT_PHONE_CAST_CARD
}

private fun savePhoneCastCard(value: Boolean) {
    prefs()?.edit()?.putBoolean(CarLinkModEntry.KEY_PHONE_CAST_CARD, value)?.apply()
}

// ========== 投屏帧率档位（5 档） ==========
// 滑条位置 0..4 → 实际帧率（最左 25，最右跟随 CarWith）
// 位置 0 -> 25 FPS（最省电）
// 位置 1 -> 30 FPS
// 位置 2 -> 45 FPS
// 位置 3 -> 60 FPS（最流畅）
// 位置 4 -> -1（跟随 CarWith，不干预）

private val CAST_FPS_OPTIONS = intArrayOf(25, 30, 45, 60, -1)

private fun loadCastFps(): Int {
    return prefs()?.getInt(CarLinkModEntry.KEY_CAST_FPS, CarLinkModEntry.DEFAULT_CAST_FPS)
        ?: CarLinkModEntry.DEFAULT_CAST_FPS
}

private fun saveCastFps(value: Int) {
    prefs()?.edit()?.putInt(CarLinkModEntry.KEY_CAST_FPS, value)?.apply()
}

private fun castFpsToSlider(fps: Int): Float {
    val idx = CAST_FPS_OPTIONS.indexOfFirst { it == fps }
    return if (idx >= 0) idx.toFloat() else 4f // 未知值默认跟随（最右）
}

private fun sliderToCastFps(pos: Float): Int {
    val idx = pos.toInt().coerceIn(0, 4)
    return CAST_FPS_OPTIONS[idx]
}

private fun castFpsLabel(fps: Int): String {
    return when (fps) {
        25 -> "25 FPS · 最省电"
        30 -> "30 FPS · 标准"
        45 -> "45 FPS · 流畅"
        60 -> "60 FPS · 最流畅"
        else -> "跟随 CarWith"
    }
}