package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.addSetupTask
import earth.terrarium.cloche.modConfigurationName
import net.msrandom.minecraftcodev.accesswidener.AccessWiden
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import net.msrandom.minecraftcodev.includes.ExtractIncludes
import net.msrandom.minecraftcodev.mixins.task.Mixin
import net.msrandom.minecraftcodev.mixins.task.StripMixins
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.remapper.task.Remap
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.SourceSet
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import java.util.*
import javax.inject.Inject

abstract class TargetCompilation @Inject constructor(
    private val name: String,
    target: MinecraftTarget,
    intermediateMinecraft: FileCollection,
    main: Optional<TargetCompilation>,
    classpathMapper: (classpath: FileCollection, nameParts: Array<String?>) -> FileCollection,
    side: Side,
    remapNamespace: String?,
    classpath: FileCollection,
    project: Project,
) : RunnableCompilationInternal {
    private val namePart = name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }

    private val extractCompileIncludes = project.tasks.register(lowerCamelCaseName("extract", target.name, namePart, "compileIncludes"), ExtractIncludes::class.java)
    private val extractRuntimeIncludes = project.tasks.register(lowerCamelCaseName("extract", target.name, namePart, "runtimeIncludes"), ExtractIncludes::class.java)

    private val stripRuntimeMixins = project.tasks.register(lowerCamelCaseName("strip", target.name, namePart, "runtimeMixins"), StripMixins::class.java) {
        it.inputFiles.from(extractRuntimeIncludes.map(ExtractIncludes::outputFiles))
    }

/*    private val mixinTask = project.tasks.register(lowerCamelCaseName("mixin", target.name, namePart, "Minecraft"), Mixin::class.java) {
        it.inputFiles.from(intermediateMinecraft)
        it.mixinFiles.from(extractCompileIncludes.map(ExtractIncludes::outputFiles))
        it.classpath.from(classpath)
        it.side.set(side)
    }*/

    private val remapMinecraftNamedJar = project.tasks.register(lowerCamelCaseName("remap", target.name, namePart, "minecraftNamed"), Remap::class.java) {
        it.inputFiles.from(intermediateMinecraft)
        it.sourceNamespace.set(remapNamespace)
        it.targetNamespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
        it.classpath.from(classpath)
        it.filterMods.set(false)
    }

    private val accessWidenTask = project.tasks.register(project.addSetupTask(lowerCamelCaseName("accessWiden", target.name, namePart, "Minecraft")), AccessWiden::class.java) {
        it.inputFiles.from(remapMinecraftNamedJar.map(Remap::outputFiles))
        it.namespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
    }

    final override val minecraftFiles: FileCollection = project.files(accessWidenTask.map(AccessWiden::outputFiles))

    final override val compileClasspath: ConfigurableFileCollection = project.files()
    final override val runtimeClasspath: ConfigurableFileCollection = project.files()

    override val sources: FileCollection = project.files()
    override val javadoc: FileCollection = project.files()

    final override val dependencySetupActions = mutableListOf<Action<ClocheDependencyHandler>>()
    final override val runSetupActions = mutableListOf<Action<MinecraftRunConfigurationBuilder>>()

    init {
        project.afterEvaluate {
            extractCompileIncludes.configure {
                with(project) {
                    with(target) {
                        it.inputFiles.from(project.configurations.named(modConfigurationName(this@TargetCompilation.sourceSet.compileClasspathConfigurationName)))
                    }
                }
            }

            extractRuntimeIncludes.configure {
                with(project) {
                    with(target) {
                        it.inputFiles.from(project.configurations.named(modConfigurationName(this@TargetCompilation.sourceSet.runtimeClasspathConfigurationName)))
                    }
                }
            }

            remapMinecraftNamedJar.configure {
                with(project) {
                    with(target) {
                        it.mappings.from(project.configurations.named(main.map { it.sourceSet }.orElse(this@TargetCompilation.sourceSet).mappingsConfigurationName))
                    }
                }
            }

            accessWidenTask.configure {
                with(project) {
                    with(target) {
                        it.accessWideners.from(project.configurations.named(this@TargetCompilation.sourceSet.accessWidenersConfigurationName))
                    }
                }
            }
        }

        if (remapNamespace != null) {
            val remapCompileClasspath = project.tasks.register(project.addSetupTask(lowerCamelCaseName("remap", target.name, namePart, "compileClasspath")), Remap::class.java) {
                it.inputFiles.from(extractCompileIncludes.map(ExtractIncludes::outputFiles))
                it.sourceNamespace.set(remapNamespace)
                it.classpath.from(intermediateMinecraft)
            }

            val remapRuntimeClasspath = project.tasks.register(project.addSetupTask(lowerCamelCaseName("remap", target.name, namePart, "runtimeClasspath")), Remap::class.java) {
                it.inputFiles.from(stripRuntimeMixins.map(StripMixins::outputFiles))
                it.sourceNamespace.set(remapNamespace)
                it.classpath.from(intermediateMinecraft)
            }

            project.afterEvaluate {
                val mappings = with(project) {
                    with(target) {
                        project.configurations.named(main.map { it.sourceSet }.orElse(this@TargetCompilation.sourceSet).mappingsConfigurationName)
                    }
                }

                remapCompileClasspath.configure {
                    it.mappings.from(mappings)
                }

                remapRuntimeClasspath.configure {
                    it.mappings.from(mappings)
                }
            }

            compileClasspath.from(remapCompileClasspath.map(Remap::outputFiles))
            runtimeClasspath.from(remapRuntimeClasspath.map(Remap::outputFiles))
        } else {
            project.addSetupTask(extractCompileIncludes.name)
            project.addSetupTask(stripRuntimeMixins.name)

            compileClasspath.from(extractCompileIncludes.map(ExtractIncludes::outputFiles))
            runtimeClasspath.from(stripRuntimeMixins.map(StripMixins::outputFiles))
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
