package earth.terrarium.cloche.target

import earth.terrarium.cloche.api.LazyConfigurable
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

internal inline fun <reified T : Any> Project.lazyConfigurable(noinline construct: () -> T) =
    LazyConfigurableInternal(construct, T::class.java, objects)

internal class LazyConfigurableInternal<out T : Any>(
    private val construct: () -> T,
    type: Class<T>,
    objects: ObjectFactory,
) : LazyConfigurable<T> {
    private val valueProperty = objects.property(type)

    override val value: Provider<@UnsafeVariance T>
        get() = valueProperty

    var internalValue: @UnsafeVariance T? = null
        private set

    val isConfigured: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val isConfiguredValue get() = internalValue != null

    private val listeners = mutableListOf<Action<T>>()

    fun onConfigured(action: Action<@UnsafeVariance T>) {
        internalValue?.let {
            action.execute(it)

            return
        }

        listeners.add(action)
    }

    override fun configure(action: Action<@UnsafeVariance T>?): T {
        internalValue?.let {
            action?.execute(it)

            return it
        }

        val value = construct()

        valueProperty.set(value)
        isConfigured.set(true)
        internalValue = value

        for (listener in listeners) {
            listener.execute(value)
        }

        listeners.clear()

        action?.execute(value)

        return value
    }
}
