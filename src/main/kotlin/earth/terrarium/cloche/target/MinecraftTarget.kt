package earth.terrarium.cloche.target

import org.gradle.api.Action
import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

interface MinecraftTarget : ClocheTarget, RunnableCompilation, Compilation {
    val minecraftVersion: Property<String>
        @Input
        get

    val loaderVersion: Property<String>
        @Input get

    val datagenDirectory: Provider<Directory>
        @Internal
        get() = project.layout.buildDirectory.dir("generatedResources/${name}")

    val loaderAttributeName: String
        @Internal get

    val main: RunnableCompilation
    val data: RunnableCompilation
    val client: Runnable

    fun data() = data(null)
    fun data(action: Action<RunnableCompilation>?)

    fun mappings(action: Action<MappingsBuilder>)
}
