package com.prateek.doingksp

import com.prateek.doksp.inject_annotations.Module
import com.prateek.doksp.inject_annotations.Provides
import retrofit2.Retrofit

@Module
object AppModule {

    @Provides
    fun provideBaseUrl(): String = "https://www.google.com/"


    @Provides
    fun provideRetrofit(baseurl: String): Retrofit = Retrofit.Builder().baseUrl(baseurl).build()

}