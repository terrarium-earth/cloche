package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet

interface Compilation : Named {
    val accessWideners: ConfigurableFileCollection
        @InputFiles
        get

    val mixins: ConfigurableFileCollection
        @InputFiles
        get

    val sourceSet: SourceSet
        @Internal
        get

    fun dependencies(action: Action<ClocheDependencyHandler>)
}

interface CompilationInternal: Compilation {
    val dependencySetupActions: List<Action<ClocheDependencyHandler>>

    fun process(sourceSet: SourceSet)
}

interface RunnableCompilation : Compilation {
    fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>)
}

interface RunnableCompilationInternal : CompilationInternal, RunnableCompilation {
    val dependency: Provider<ModuleDependency>
        @Internal
        get

    val createSourceSet: Boolean
        @Internal
        get

    val runSetupActions: List<Action<MinecraftRunConfigurationBuilder>>
}
