package com.parallellite.launcher.data.remote

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming
import retrofit2.http.Url

interface RomhackingApi {

    @POST("v3/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("v3/hacks/following")
    suspend fun following(): List<RhdcFollowingHackDto>

    @GET("v3/hacks/hack/{id}")
    suspend fun hackDetail(@Path("id") hackId: String): RhdcHackDetailDto

    /** Streams an arbitrary (possibly off-host) download URL for a patch/archive. */
    @Streaming
    @GET
    suspend fun download(@Url url: String): ResponseBody

    companion object {
        const val BASE_URL = "https://api.romhacking.com/"
        const val HOST = "api.romhacking.com"
    }
}
