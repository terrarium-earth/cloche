package earth.terrarium.cloche.target

import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

interface MinecraftTarget : ClocheTarget {
    val withExtensions: Property<Boolean>
        @Optional
        @Input
        get

    fun test() = test(null)
    fun test(action: Action<Compilation>?)
}
