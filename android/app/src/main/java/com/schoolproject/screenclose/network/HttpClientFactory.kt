package com.schoolproject.screenclose.network

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * ADR-004: default OkHttp TLS stack (platform trust store + hostname verification) with only
 * MODERN_TLS allowed and cleartext disabled. No HttpLoggingInterceptor is installed, so headers
 * and bodies (bearer token, screenshot) are never logged.
 */
object HttpClientFactory {
    fun okHttp(timeoutSeconds: Long): OkHttpClient = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(timeoutSeconds + 10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false) // retries are explicit and bounded in FlowHttpTransport
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /** Base URL is unused because FlowApi takes the full endpoint via @Url. */
    fun flowApi(client: OkHttpClient, baseUrl: String = "https://localhost/"): FlowApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .build()
        .create(FlowApi::class.java)
}
