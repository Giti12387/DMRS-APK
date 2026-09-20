package com.apps.apkstore.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.apps.apkstore.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(AuthInterceptor())
            .addInterceptor(NetworkInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .serializeNulls()
            .create()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}

class AuthInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        var request = chain.request()

        val token = TokenManager.getToken()
        if (token.isNotEmpty()) {
            request = request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        }

        request = request.newBuilder()
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "APKStore/${BuildConfig.VERSION_NAME} (Android ${android.os.Build.VERSION.SDK_INT})")
            .addHeader("X-App-Version", BuildConfig.VERSION_NAME)
            .addHeader("X-App-Build", BuildConfig.VERSION_CODE.toString())
            .build()

        return chain.proceed(request)
    }
}

class NetworkInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val response = chain.proceed(request)

        when (response.code) {
            401 -> {
                TokenManager.clearToken()
            }
        }

        return response
    }
}

object TokenManager {
    private const val TOKEN_KEY = "auth_token"
    private const val REFRESH_TOKEN_KEY = "refresh_token"

    fun saveToken(token: String, refreshToken: String) {
        val prefs = com.apps.apkstore.data.local.PreferencesManager.getInstance(
            com.apps.apkstore.APKStoreApplication.instance
        )
        prefs.putString(TOKEN_KEY, token)
        prefs.putString(REFRESH_TOKEN_KEY, refreshToken)
    }

    fun getToken(): String {
        val prefs = com.apps.apkstore.data.local.PreferencesManager.getInstance(
            com.apps.apkstore.APKStoreApplication.instance
        )
        return prefs.getString(TOKEN_KEY, "")
    }

    fun getRefreshToken(): String {
        val prefs = com.apps.apkstore.data.local.PreferencesManager.getInstance(
            com.apps.apkstore.APKStoreApplication.instance
        )
        return prefs.getString(REFRESH_TOKEN_KEY, "")
    }

    fun clearToken() {
        val prefs = com.apps.apkstore.data.local.PreferencesManager.getInstance(
            com.apps.apkstore.APKStoreApplication.instance
        )
        prefs.remove(TOKEN_KEY)
        prefs.remove(REFRESH_TOKEN_KEY)
    }

    fun hasToken(): Boolean {
        return getToken().isNotEmpty()
    }
}