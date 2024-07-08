package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.addSetupTask
import net.msrandom.minecraftcodev.accesswidener.AccessWidenJar
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import net.msrandom.minecraftcodev.mixins.JarMixin
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

abstract class TargetCompilation @Inject constructor(
    targetName: String,
    private val name: String,
    private val baseDependency: Provider<RegularFile>,
    private val project: Project,
) : RunnableCompilationInternal {
    private val accessWidenTask = project.tasks.register(lowerCamelCaseName("accessWiden", targetName, name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }, "Minecraft"), AccessWidenJar::class.java) {
        it.input.set(baseDependency)
        it.accessWideners.from(sourceSet.map(SourceSet::accessWidenersConfigurationName).flatMap(project.configurations::named))
        it.namespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
    }

    private val mixinTask = project.tasks.register(project.addSetupTask(lowerCamelCaseName("mixin", targetName, name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }, "Minecraft")), JarMixin::class.java) {
        it.input.set(accessWidenTask.flatMap(AccessWidenJar::output))
        it.classpath
    }

    override val dependency: Provider<Dependency>
        get() = project.provider {
            project.dependencies.create(project.files(mixinTask.flatMap(JarMixin::output)))
        }

    final override val sourceSet: Property<SourceSet> =
        project.objects.property(SourceSet::class.java)

    override val dependencySetupActions = mutableListOf<Action<ClocheDependencyHandler>>()
    override val runSetupActions = mutableListOf<Action<MinecraftRunConfigurationBuilder>>()

    override fun dependencies(action: Action<ClocheDependencyHandler>) {
        dependencySetupActions.add(action)
    }

    override fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>) {
        runSetupActions.add(action)
    }

    override fun getName() = name
}
