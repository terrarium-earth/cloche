package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.modConfigurationName
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

interface Compilation : Named {
    val accessWideners: ConfigurableFileCollection
        @InputFiles get

    val mixins: ConfigurableFileCollection
        @InputFiles get

    fun dependencies(action: Action<ClocheDependencyHandler>)
}

interface CompilationInternal : Compilation {
    val dependencySetupActions: List<Action<ClocheDependencyHandler>>
}

interface Runnable : Named {
    fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>)
}

interface RunnableInternal : Runnable {
    val runSetupActions: List<Action<MinecraftRunConfigurationBuilder>>
}

interface RunnableCompilation : Runnable, Compilation

interface RunnableCompilationInternal : CompilationInternal, RunnableCompilation, RunnableInternal {
    val minecraftFiles: FileCollection
        @Internal get

    val compileClasspath: FileCollection
        @Internal get

    val runtimeClasspath: FileCollection
        @Internal get

    val sources: FileCollection
        @Internal get

    val javadoc: FileCollection
        @Internal get
}

private fun sourceSetName(compilation: Compilation, target: ClocheTarget) = when {
    target.name == ClocheExtension::common.name -> compilation.name
    compilation.name == SourceSet.MAIN_SOURCE_SET_NAME -> target.name
    else -> lowerCamelCaseName(target.name, compilation.name)
}

private fun RunnableCompilationInternal.setupSourceSet(project: Project, sourceSet: SourceSet): SourceSet {
    if (project.configurations.findByName(modConfigurationName(sourceSet.compileClasspathConfigurationName)) != null) {
        return sourceSet
    }

    project.dependencies.add(sourceSet.implementationConfigurationName, minecraftFiles)
    project.dependencies.add(sourceSet.compileOnlyConfigurationName, compileClasspath)
    project.dependencies.add(sourceSet.runtimeOnlyConfigurationName, runtimeClasspath)

    fun modConfiguration(name: String): Configuration {
        return project.configurations.create(modConfigurationName(name)) { modConfig ->
            modConfig.isCanBeConsumed = false
            modConfig.isCanBeResolved = false
        }
    }

    val modImplementation = modConfiguration(sourceSet.implementationConfigurationName)
    val modRuntimeOnly = modConfiguration(sourceSet.runtimeOnlyConfigurationName)
    val modCompileOnly = modConfiguration(sourceSet.compileOnlyConfigurationName)

    modConfiguration(sourceSet.apiConfigurationName).apply {
        extendsFrom(modImplementation)
    }

    modConfiguration(sourceSet.compileOnlyApiConfigurationName).apply {
        extendsFrom(modCompileOnly)
    }

    modConfiguration(sourceSet.compileClasspathConfigurationName).apply {
        isCanBeResolved = true
        isCanBeDeclared = false

        extendsFrom(modImplementation)
        extendsFrom(modCompileOnly)
    }

    modConfiguration(sourceSet.runtimeClasspathConfigurationName).apply {
        isCanBeResolved = true
        isCanBeDeclared = false

        extendsFrom(modImplementation)
        extendsFrom(modRuntimeOnly)
    }

    return sourceSet
}

context(Project, MinecraftTarget) val RunnableCompilation.sourceSet: SourceSet
    get() {
        this@RunnableCompilation as RunnableCompilationInternal

        val cloche = project.extension<ClocheExtension>()

        val name = if (cloche.isSingleTargetMode) {
            name
        } else {
            sourceSetName(this, this@MinecraftTarget)
        }

        val sourceSets = project.extension<SourceSetContainer>()

        return setupSourceSet(project, sourceSets.maybeCreate(name))
    }

context(Project, CommonTarget) val Compilation.sourceSet: SourceSet
    get() = project
        .extension<SourceSetContainer>()
        .maybeCreate(sourceSetName(this, this@CommonTarget))
