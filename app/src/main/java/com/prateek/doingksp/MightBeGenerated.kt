package com.prateek.doingksp

import android.content.Context
import retrofit2.Retrofit
import kotlin.reflect.KClass

object TinyDIComponentMightBe {

    val runtimeBindings: MutableMap<KClass<*>, Any> = mutableMapOf()

    @Suppress("UNCHECKED_CAST")
    fun <T> getDependency(clazz: KClass<*>): T =
        // 1) runtime fast‑path
        runtimeBindings[clazz] as? T ?: when (clazz) {
            // 2) providers – executed every call
//            String::class -> AppModule.provideBaseUrl() as T
//            Retrofit::class -> AppModule.provideRetrofit(getDependency(String::class)) as T
//            // 3) constructor‑inject factories
//            MyViewModel::class -> MyViewModel(
//                getDependency(Repository1::class),
//                getDependency(Repository2::class),
//                getDependency(Context::class),
//                getDependency(Retrofit::class)
//            ) as T
            // … generated factory branches …
            else -> error("No binding for $clazz")
        }

    inline fun <reified T : Any> TinyDIComponentMightBe.bind(instance: T) {
        runtimeBindings[T::class] = instance
    }

    fun TinyDIComponentMightBe.reset() = runtimeBindings.clear()

    inline fun <reified T> TinyDIComponentMightBe.get(): T =
        getDependency(T::class) as T

}