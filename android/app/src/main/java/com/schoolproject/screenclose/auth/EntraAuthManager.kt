package com.schoolproject.screenclose.auth

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import com.schoolproject.screenclose.core.AppConfig
import com.schoolproject.screenclose.core.ErrorCode
import com.schoolproject.screenclose.core.Logger
import com.schoolproject.screenclose.core.ScaException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface AuthState {
    data object Initializing : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val username: String) : AuthState
    data class Failed(val code: ErrorCode, val message: String) : AuthState
}

/**
 * Microsoft Entra ID sign-in with MSAL (single-account public client, no client secret).
 * Tokens are requested for https://service.flow.microsoft.com//.default, which yields
 * aud=https://service.flow.microsoft.com/ as required by the OAuth-protected HTTP trigger.
 * MSAL keeps its token cache encrypted in app-private storage; the app never persists tokens itself.
 */
class EntraAuthManager(
    private val context: Context,
    private val config: AppConfig,
    private val logger: Logger,
) : TokenProvider {

    private val app = CompletableDeferred<ISingleAccountPublicClientApplication>()
    private val _state = MutableStateFlow<AuthState>(AuthState.Initializing)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val scopes = listOf(config.flowScope)
    private val authority get() = "https://login.microsoftonline.com/${config.entraTenantId}"

    suspend fun initialize() {
        if (app.isCompleted) return
        try {
            val file = withContext(Dispatchers.IO) { writeMsalConfig() }
            val created = suspendCancellableCoroutine<ISingleAccountPublicClientApplication> { cont ->
                PublicClientApplication.createSingleAccountPublicClientApplication(
                    context,
                    file,
                    object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                        override fun onCreated(application: ISingleAccountPublicClientApplication) {
                            if (cont.isActive) cont.resume(application)
                        }

                        override fun onError(exception: MsalException) {
                            if (cont.isActive) cont.resumeWithException(exception)
                        }
                    },
                )
            }
            app.complete(created)
            val account = currentAccount(created)
            _state.value = account?.let { AuthState.SignedIn(it.username) } ?: AuthState.SignedOut
            logger.info("auth_initialized", mapOf("signedIn" to (account != null)))
        } catch (e: MsalException) {
            _state.value = AuthState.Failed(ErrorCode.AUTH_NOT_INITIALIZED, e.errorCode)
            logger.error("auth_init_failed", mapOf("msalCode" to e.errorCode), e)
        } catch (e: java.io.IOException) {
            _state.value = AuthState.Failed(ErrorCode.AUTH_NOT_INITIALIZED, "could not write MSAL configuration")
            logger.error("auth_init_failed", mapOf("code" to ErrorCode.AUTH_NOT_INITIALIZED.code), e)
        }
    }

    suspend fun signIn(activity: Activity) {
        if (!app.isCompleted) initialize()
        try {
            val pca = requireApp()
            val result = suspendCancellableCoroutine<IAuthenticationResult> { cont ->
                val params = SignInParameters.builder()
                    .withActivity(activity)
                    .withScopes(scopes)
                    .withCallback(object : AuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult) {
                            if (cont.isActive) cont.resume(authenticationResult)
                        }

                        override fun onError(exception: MsalException) {
                            if (cont.isActive) cont.resumeWithException(exception)
                        }

                        override fun onCancel() {
                            if (cont.isActive) cont.resumeWithException(ScaException(ErrorCode.AUTH_CANCELLED, "sign-in cancelled"))
                        }
                    })
                    .build()
                pca.signIn(params)
            }
            _state.value = AuthState.SignedIn(result.account.username)
            logger.info("auth_signed_in")
        } catch (e: ScaException) {
            _state.value = if (e.errorCode == ErrorCode.AUTH_CANCELLED) AuthState.SignedOut else AuthState.Failed(e.errorCode, e.message.orEmpty())
            logger.warn("auth_sign_in_not_completed", mapOf("code" to e.errorCode.code), e)
        } catch (e: MsalException) {
            _state.value = AuthState.Failed(ErrorCode.AUTH_FAILED, e.errorCode)
            logger.error("auth_sign_in_failed", mapOf("msalCode" to e.errorCode), e)
        }
    }

    suspend fun signOut() {
        try {
            val pca = requireApp()
            suspendCancellableCoroutine<Unit> { cont ->
                pca.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                    override fun onSignOut() {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(exception: MsalException) {
                        if (cont.isActive) cont.resumeWithException(ScaException(ErrorCode.AUTH_FAILED, "sign-out failed", exception))
                    }
                })
            }
            _state.value = AuthState.SignedOut
            logger.info("auth_signed_out")
        } catch (e: ScaException) {
            _state.value = AuthState.Failed(e.errorCode, e.message.orEmpty())
            logger.error("auth_sign_out_failed", mapOf("code" to e.errorCode.code), e)
        }
    }

    override suspend fun accessToken(forceRefresh: Boolean): String {
        val pca = requireApp()
        val account = currentAccount(pca)
            ?: throw ScaException(ErrorCode.AUTH_INTERACTION_REQUIRED, "no signed-in account")
        return try {
            suspendCancellableCoroutine<String> { cont ->
                val params = AcquireTokenSilentParameters.Builder()
                    .forAccount(account)
                    .fromAuthority(authority)
                    .withScopes(scopes)
                    .forceRefresh(forceRefresh)
                    .withCallback(object : SilentAuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult) {
                            if (cont.isActive) cont.resume(authenticationResult.accessToken)
                        }

                        override fun onError(exception: MsalException) {
                            if (cont.isActive) cont.resumeWithException(exception)
                        }
                    })
                    .build()
                pca.acquireTokenSilentAsync(params)
            }
        } catch (e: MsalUiRequiredException) {
            _state.value = AuthState.SignedOut
            throw ScaException(ErrorCode.AUTH_INTERACTION_REQUIRED, "interactive sign-in required", e)
        } catch (e: MsalException) {
            throw ScaException(ErrorCode.AUTH_FAILED, "silent token acquisition failed (${e.errorCode})", e)
        }
    }

    private suspend fun requireApp(): ISingleAccountPublicClientApplication {
        if (!app.isCompleted) throw ScaException(ErrorCode.AUTH_NOT_INITIALIZED, "MSAL not initialized")
        return app.await()
    }

    private suspend fun currentAccount(pca: ISingleAccountPublicClientApplication): IAccount? =
        suspendCancellableCoroutine<IAccount?> { cont ->
            pca.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                override fun onAccountLoaded(activeAccount: IAccount?) {
                    if (cont.isActive) cont.resume(activeAccount)
                }

                override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                    if (cont.isActive) cont.resume(currentAccount)
                }

                override fun onError(exception: MsalException) {
                    if (cont.isActive) cont.resumeWithException(exception)
                }
            })
        }

    /** MSAL configuration is generated from non-secret BuildConfig values into app-private storage. */
    private fun writeMsalConfig(): File {
        val json = buildJsonObject {
            put("client_id", config.entraClientId)
            put("authorization_user_agent", "DEFAULT")
            put("redirect_uri", config.entraRedirectUri)
            put("account_mode", "SINGLE")
            put("broker_redirect_uri_registered", true)
            put("authorities", buildJsonArray {
                add(buildJsonObject {
                    put("type", "AAD")
                    putJsonObject("audience") {
                        put("type", "AzureADMyOrg")
                        put("tenant_id", config.entraTenantId)
                    }
                })
            })
        }
        val file = File(context.noBackupFilesDir, "msal_config.json")
        file.writeText(json.toString())
        return file
    }
}
