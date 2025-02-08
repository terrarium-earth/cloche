package earth.terrarium.cloche.api

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.provider.Provider

interface LazyConfigurable<out T : Any> {
    val value: Provider<@UnsafeVariance T>

    operator fun invoke() = configure()
    operator fun invoke(action: Action<@UnsafeVariance T>) = configure(action)

    fun call() = configure()
    fun call(closure: Closure<*>) = configure(closure::call)

    fun configure(action: Action<@UnsafeVariance T>? = null): T
}
