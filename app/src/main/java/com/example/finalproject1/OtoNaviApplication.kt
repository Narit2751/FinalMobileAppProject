package com.example.finalproject1

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient

class OtoNaviApplication : Application(), SingletonImageLoader.Factory {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        SingletonImageLoader.setSafe(this)
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            OkHttpClient.Builder()
                                .followRedirects(true)
                                .followSslRedirects(true)
                                .addInterceptor { chain ->
                                    val request = chain.request().newBuilder()
                                            .header(
                                                "User-Agent",
                                                "OtoNaviJapan/1.0 university-project",
                                            )
                                            .build()
                                    chain.proceed(request)
                                }
                                .build()
                        }
                    )
                )
            }
            .build()
}
