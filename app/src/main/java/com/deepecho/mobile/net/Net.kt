package com.deepecho.mobile.net

import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Net {
    const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(12, 5, TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()
}

/** Chhote messages (toast) jo UI dikhata hai. */
object Bus {
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    fun toast(msg: String) {
        messages.tryEmit(msg)
    }
}
