package com.parallellite.launcher.ui.launcher

/** Simple navigation stack for the launcher: list → detail → version picker. */
sealed interface LauncherPage {
    data object List : LauncherPage
    data class Detail(val hackId: String) : LauncherPage
    data class Versions(val hackId: String) : LauncherPage
}
