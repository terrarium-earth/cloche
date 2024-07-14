package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.addSetupTask
import earth.terrarium.cloche.modConfigurationName
import net.msrandom.minecraftcodev.accesswidener.AccessWidenJar
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import net.msrandom.minecraftcodev.includes.ExtractIncludes
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.remapper.RemapJars
import net.msrandom.minecraftcodev.remapper.mappingsConfigurationName
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import java.util.Optional
import javax.inject.Inject

abstract class TargetCompilation @Inject constructor(
    targetName: String,
    private val name: String,
    private val baseDependency: Provider<RegularFile>,
    private val project: Project,
    main: Optional<TargetCompilation>,
    remapNamespace: String?,
    mappingClasspath: FileCollection,
) : RunnableCompilationInternal {
    private val namePart = name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }

    private val accessWidenTask = project.tasks.register(project.addSetupTask(lowerCamelCaseName("accessWiden", targetName, namePart, "Minecraft")), AccessWidenJar::class.java) {
        it.input.set(baseDependency)
        it.accessWideners.from(sourceSet.map(SourceSet::accessWidenersConfigurationName).flatMap(project.configurations::named))
        it.namespace.set(MinecraftCodevRemapperPlugin.NAMED_MAPPINGS_NAMESPACE)
    }

/*    private val mixinTask = project.tasks.register(project.addSetupTask(lowerCamelCaseName("mixin", targetName, name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }, "Minecraft")), JarMixin::class.java) {
        it.input.set(accessWidenTask.flatMap(AccessWidenJar::output))
        it.classpath
    }*/

    private val extractCompileIncludes = project.tasks.register(lowerCamelCaseName("extract", targetName, namePart, "compileIncludes"), ExtractIncludes::class.java) {
        it.inputFiles.from(sourceSet.map { modConfigurationName(it.compileClasspathConfigurationName) }.flatMap(project.configurations::named))
    }

    private val extractRuntimeIncludes = project.tasks.register(lowerCamelCaseName("extract", targetName, namePart, "runtimeIncludes"), ExtractIncludes::class.java) {
        it.inputFiles.from(sourceSet.map { modConfigurationName(it.runtimeClasspathConfigurationName) }.flatMap(project.configurations::named))
    }

    override val minecraftJar: Provider<RegularFile>
        get() = accessWidenTask.flatMap(AccessWidenJar::output)

    override val dependency: Provider<Dependency>
        get() = project.provider { project.dependencies.create(project.files(minecraftJar)) }

    override val compileClasspath: Property<FileCollection> = project.objects.property(FileCollection::class.java)
    override val runtimeClasspath: Property<FileCollection> = project.objects.property(FileCollection::class.java)

    final override val sourceSet: Property<SourceSet> =
        project.objects.property(SourceSet::class.java)

    override val dependencySetupActions = mutableListOf<Action<ClocheDependencyHandler>>()
    override val runSetupActions = mutableListOf<Action<MinecraftRunConfigurationBuilder>>()

    init {
        if (remapNamespace != null) {
            val mappings = main.map(TargetCompilation::sourceSet).orElse(sourceSet).map(SourceSet::mappingsConfigurationName).flatMap(project.configurations::named)

            val remapCompileClasspath = project.tasks.register(project.addSetupTask(lowerCamelCaseName("remap", targetName, namePart, "compileClasspath")), RemapJars::class.java) {
                it.inputFiles.from(extractCompileIncludes.map(ExtractIncludes::outputFiles))

                it.mappings.from(mappings)
                it.sourceNamespace.set(remapNamespace)

                it.classpath.from(mappingClasspath)
            }

            val remapRuntimeClasspath = project.tasks.register(project.addSetupTask(lowerCamelCaseName("remap", targetName, namePart, "runtimeClasspath")), RemapJars::class.java) {
                it.inputFiles.from(extractRuntimeIncludes.map(ExtractIncludes::outputFiles))

                it.mappings.from(mappings)
                it.sourceNamespace.set(remapNamespace)

                it.classpath.from(mappingClasspath)
            }

            compileClasspath.set(remapCompileClasspath.map(RemapJars::outputFiles))
            runtimeClasspath.set(remapRuntimeClasspath.map(RemapJars::outputFiles))
        } else {
            project.addSetupTask(extractCompileIncludes.name)
            project.addSetupTask(extractRuntimeIncludes.name)

            compileClasspath.set(extractCompileIncludes.map(ExtractIncludes::outputFiles))
            runtimeClasspath.set(extractRuntimeIncludes.map(ExtractIncludes::outputFiles))
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
