package com.schoolproject.screenclose.auth

/** Port used by the network layer; lets tests supply static tokens. */
fun interface TokenProvider {
    /** Returns a bearer token for the flow audience. Throws ScaException(AUTH_*) when sign-in is needed. */
    suspend fun accessToken(forceRefresh: Boolean): String
}
