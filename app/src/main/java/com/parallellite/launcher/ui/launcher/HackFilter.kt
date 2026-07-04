package com.parallellite.launcher.ui.launcher

import androidx.annotation.StringRes
import com.parallellite.launcher.R

/** Playlist filters shown in the launcher dropdown. */
enum class HackFilter(@StringRes val labelRes: Int, val matchKey: String?) {
    ALL(R.string.filter_all, null),
    WANT_TO_PLAY(R.string.filter_want, "Want To Play"),
    IN_PROGRESS(R.string.filter_in_progress, "In Progress"),
    COMPLETED(R.string.filter_completed, "Completed"),
}
