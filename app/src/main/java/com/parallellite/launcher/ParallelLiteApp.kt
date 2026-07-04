package com.parallellite.launcher

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.parallellite.launcher.data.remote.RomhackingApi
import com.parallellite.launcher.di.AppContainer
import okhttp3.OkHttpClient

class ParallelLiteApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * Coil loads thumbnails from the Romhacking host. Attach the session token
     * for that host (harmless if the image is public) so authed-only images
     * still load; the token is never sent to any other host.
     */
    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val token = container.sessionStore.currentToken()
                val authed = if (token.isNotEmpty() && request.url.host == RomhackingApi.HOST) {
                    request.newBuilder().header("Authorization", "Bearer $token").build()
                } else {
                    request
                }
                chain.proceed(authed)
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }
}
