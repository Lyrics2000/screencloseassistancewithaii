package com.schoolproject.screenclose.core

import com.schoolproject.screenclose.BuildConfig
import java.net.URI

/** Non-secret runtime configuration compiled from local.properties (validated again at runtime). */
data class AppConfig(
    val entraClientId: String,
    val entraTenantId: String,
    val entraRedirectUri: String,
    val flowEndpointUrl: String,
    val flowScope: String,
    val maxImageLongEdge: Int,
    val jpegQuality: Int,
    val maxEncodedImageBytes: Int = 3_000_000,
    val httpTimeoutSeconds: Long = 110,
) {
    init {
        val uri = runCatching { URI(flowEndpointUrl) }.getOrNull()
        if (uri?.scheme != "https" || flowEndpointUrl.contains("sig=", ignoreCase = true)) {
            throw ScaException(ErrorCode.CONFIG_INVALID, "flow endpoint must be https and must not contain a SAS signature")
        }
        if (maxImageLongEdge !in 256..2576) {
            throw ScaException(ErrorCode.CONFIG_INVALID, "maxImageLongEdge must be within 256..2576")
        }
        if (jpegQuality !in 50..95) {
            throw ScaException(ErrorCode.CONFIG_INVALID, "jpegQuality must be within 50..95")
        }
    }

    companion object {
        fun fromBuildConfig(): AppConfig = AppConfig(
            entraClientId = BuildConfig.ENTRA_CLIENT_ID,
            entraTenantId = BuildConfig.ENTRA_TENANT_ID,
            entraRedirectUri = BuildConfig.ENTRA_REDIRECT_URI,
            flowEndpointUrl = BuildConfig.FLOW_ENDPOINT_URL,
            flowScope = BuildConfig.FLOW_SCOPE,
            maxImageLongEdge = BuildConfig.MAX_IMAGE_LONG_EDGE,
            jpegQuality = BuildConfig.JPEG_QUALITY,
        )
    }
}
