package com.schoolproject.screenclose.network

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit binding for the "SCA Android HTTP Entry" flow. Raw bodies are used so that decoding
 * is done by the strict [com.schoolproject.screenclose.domain.ScaJson] and every failure mode
 * can be mapped to an SCA ERROR document.
 */
interface FlowApi {
    @POST
    suspend fun analyze(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Header("x-ms-client-request-id") clientRequestId: String,
        @Header("traceparent") traceparent: String,
        @Header("Accept") accept: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>
}
