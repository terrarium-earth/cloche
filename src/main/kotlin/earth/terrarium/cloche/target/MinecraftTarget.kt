package earth.terrarium.cloche.target

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional

interface MinecraftTarget : ClocheTarget, RunnableCompilation {
    val minecraftVersion: Property<String>
        @Input
        get

    val loaderVersion: Property<String>
        @Input get

    val extensionPattern: Property<String>
        @Optional
        @Input
        get

    val datagenDirectory: Provider<Directory>
        @Internal
        get() = project.layout.buildDirectory.dir("generatedResources/${name}")

    val loaderAttributeName: String
        @Internal get

    val main: RunnableCompilation
    val data: RunnableCompilation

    fun data() = data(null)
    fun data(action: Action<RunnableCompilation>?)

    fun mappings(action: Action<MappingsBuilder>)
}
