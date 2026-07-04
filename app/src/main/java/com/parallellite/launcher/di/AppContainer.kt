package com.parallellite.launcher.di

import android.content.Context
import com.parallellite.launcher.BuildConfig
import com.parallellite.launcher.data.HackRepository
import com.parallellite.launcher.data.PatchLauncher
import com.parallellite.launcher.data.SessionStore
import com.parallellite.launcher.data.remote.NetworkModule

/**
 * Tiny manual dependency graph. Keeps construction in one place without pulling
 * in a full DI framework for an app this size.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val sessionStore: SessionStore by lazy { SessionStore(appContext) }

    private val api by lazy { NetworkModule.create(sessionStore, debug = BuildConfig.DEBUG) }

    val hackRepository: HackRepository by lazy { HackRepository(api, sessionStore) }

    val patchLauncher: PatchLauncher by lazy { PatchLauncher(appContext, api) }
}
