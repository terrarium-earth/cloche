package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import net.msrandom.minecraftcodev.decompiler.dependency.withSources
import net.msrandom.minecraftcodev.remapper.dependency.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.dependency.remapped
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

abstract class TargetCompilation @Inject constructor(private val name: String, private val baseDependency: Provider<ModuleDependency>, val create: () -> Boolean) : RunnableCompilationInternal {
    override val dependency: Provider<ModuleDependency>
        get() = baseDependency.map {
            mixin(accessWiden(it.remapped(mappingsConfiguration = sourceSet.mappingsConfigurationName), this), this).withSources
        }

    final override lateinit var sourceSet: SourceSet
        private set

    override val dependencySetupActions = mutableListOf<Action<ClocheDependencyHandler>>()
    override val runSetupActions = mutableListOf<Action<MinecraftRunConfigurationBuilder>>()

    override val createSourceSet: Boolean
        get() = create()

    override fun process(sourceSet: SourceSet) {
        this.sourceSet = sourceSet
    }

    override fun dependencies(action: Action<ClocheDependencyHandler>) {
        dependencySetupActions.add(action)
    }

    override fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>) {
        runSetupActions.add(action)
    }

    override fun getName() = name
}
