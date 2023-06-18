package earth.terrarium.cloche.target

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import javax.inject.Inject

interface ClocheTarget : Compilation {
    val minecraftVersion: Property<String>
        @Input
        get

    val accessWideners: ConfigurableFileCollection
        @Optional
        @InputFiles
        get

    val mixins: ConfigurableFileCollection
        @Optional
        @InputFiles
        get

    val project: Project
        @Inject
        get

    val dependsOn: ListProperty<CommonTarget>
        @Optional
        @Input
        get

    val compilations: Map<String, TargetCompilation>
        @Internal
        get

    val remapNamespace: String?
        @Internal
        get() = null

    fun dependsOn(vararg common: CommonTarget) {
        dependsOn.addAll(*common)
    }

    fun data() = data(null)
    fun data(action: Action<Compilation>?)

    fun mappings(action: Action<MappingsBuilder>)
}
