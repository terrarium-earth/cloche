package earth.terrarium.cloche

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer

inline fun <reified T : Task> TaskContainer.maybeRegister(name: String, configure: Action<T>) =
    maybeRegister(name, T::class.java, configure)

fun <T : Task> TaskContainer.maybeRegister(name: String, type: Class<T>, configure: Action<T>): NamedDomainObjectProvider<T> =
    if (name in names) {
        named(name, type)
    } else {
        register(name, type, configure)
    }

inline fun <reified T : Task> TaskContainer.registerOrConfigure(name: String, configure: Action<T>) =
    registerOrConfigure(name, T::class.java, configure)

fun <T : Task> TaskContainer.registerOrConfigure(name: String, type: Class<T>, configure: Action<T>): NamedDomainObjectProvider<T> =
    if (name in names) {
        named(name, type, configure)
    } else {
        register(name, type, configure)
    }
