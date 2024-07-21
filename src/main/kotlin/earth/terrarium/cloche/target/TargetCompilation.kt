package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.addSetupTask
import earth.terrarium.cloche.modConfigurationName
import net.msrandom.minecraftcodev.accesswidener.AccessWidenJar
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import net.msrandom.minecraftcodev.includes.ExtractIncludes
import net.msrandom.minecraftcodev.mixins.task.JarMixin
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.remapper.RemapJars
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import java.util.Optional
import javax.inject.Inject

abstract class TargetCompilation @Inject constructor(
    private val name: String,
    private val target: MinecraftTarget,
    private val baseDependency: Provider<RegularFile>,
    main: Optional<TargetCompilation>,
    remapNamespace: String?,
    mappingClasspath: FileCollection,
    private val project: Project,
) : RunnableCompilationInternal {
    private val namePart = name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }

    private val accessWidenTask = project.tasks.register(lowerCamelCaseName("accessWiden", target.name, namePart, "Minecraft"), AccessWidenJar::class.java) {
        it.input.set(baseDependency)

        with(project) {
            with(target) {
                it.accessWideners.from(project.configurations.named(this@TargetCompilation.sourceSet.accessWidenersConfigurationName))
            }
        }

        it.namespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
    }

    private val mixinTask = project.tasks.register(project.addSetupTask(lowerCamelCaseName("mixin", target.name, namePart, "Minecraft")), JarMixin::class.java) {
        it.input.set(accessWidenTask.flatMap(AccessWidenJar::output))
        it.classpath.from(compileClasspath)
        it.targetNamespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
        it.sourceNamespace.set(remapNamespace)

        with(project) {
            with(target) {
                it.mappings.from(project.configurations.named(main.map { it.sourceSet }.orElse(this@TargetCompilation.sourceSet).mappingsConfigurationName))
            }
        }
    }

    private val extractCompileIncludes = project.tasks.register(lowerCamelCaseName("extract", target.name, namePart, "compileIncludes"), ExtractIncludes::class.java) {
        with(project) {
            with(target) {
                it.inputFiles.from(project.configurations.named(modConfigurationName(this@TargetCompilation.sourceSet.compileClasspathConfigurationName)))
            }
        }
    }

    private val extractRuntimeIncludes = project.tasks.register(lowerCamelCaseName("extract", target.name, namePart, "runtimeIncludes"), ExtractIncludes::class.java) {
        with(project) {
            with(target) {
                it.inputFiles.from(project.configurations.named(modConfigurationName(this@TargetCompilation.sourceSet.runtimeClasspathConfigurationName)))
            }
        }
    }

    final override val minecraftJar: Provider<RegularFile>
        get() = mixinTask.flatMap(JarMixin::output)

    final override val dependency: Provider<Dependency>
        get() = project.provider { project.dependencies.create(project.files(minecraftJar)) }

    final override val compileClasspath: ConfigurableFileCollection = project.files()
    final override val runtimeClasspath: ConfigurableFileCollection = project.files()

    final override val dependencySetupActions = mutableListOf<Action<ClocheDependencyHandler>>()
    final override val runSetupActions = mutableListOf<Action<MinecraftRunConfigurationBuilder>>()

    init {
        if (remapNamespace != null) {
            val mappings = with(project) {
                with(target) {
                    project.configurations.named(main.map { it.sourceSet }.orElse(this@TargetCompilation.sourceSet).mappingsConfigurationName)
                }
            }

            val remapCompileClasspath = project.tasks.register(project.addSetupTask(lowerCamelCaseName("remap", target.name, namePart, "compileClasspath")), RemapJars::class.java) {
                it.inputFiles.from(extractCompileIncludes.map(ExtractIncludes::outputFiles))

                it.mappings.from(mappings)
                it.sourceNamespace.set(remapNamespace)

                it.classpath.from(mappingClasspath)
            }

            val remapRuntimeClasspath = project.tasks.register(project.addSetupTask(lowerCamelCaseName("remap", target.name, namePart, "runtimeClasspath")), RemapJars::class.java) {
                it.inputFiles.from(extractRuntimeIncludes.map(ExtractIncludes::outputFiles))

                it.mappings.from(mappings)
                it.sourceNamespace.set(remapNamespace)

                it.classpath.from(mappingClasspath)
            }

            compileClasspath.from(remapCompileClasspath.map(RemapJars::outputFiles))
            runtimeClasspath.from(remapRuntimeClasspath.map(RemapJars::outputFiles))
        } else {
            project.addSetupTask(extractCompileIncludes.name)
            project.addSetupTask(extractRuntimeIncludes.name)

            compileClasspath.from(extractCompileIncludes.map(ExtractIncludes::outputFiles))
            runtimeClasspath.from(extractRuntimeIncludes.map(ExtractIncludes::outputFiles))
        }
    }

    override fun dependencies(action: Action<ClocheDependencyHandler>) {
        dependencySetupActions.add(action)
    }

    override fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>) {
        runSetupActions.add(action)
    }

    override fun getName() = name
}
