package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.ClocheExtension
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseName
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
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

interface RunnableCompilation : Compilation {
    fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>)
}

interface RunnableCompilationInternal : CompilationInternal, RunnableCompilation {
    val minecraftJar: Provider<RegularFile>
        @Internal get

    val dependency: Provider<Dependency>
        @Internal get

    val compileClasspath: FileCollection
        @Internal get

    val runtimeClasspath: FileCollection
        @Internal get

    val runSetupActions: List<Action<MinecraftRunConfigurationBuilder>>
}

private fun sourceSetName(compilation: Compilation, target: ClocheTarget) = when {
    target.name == ClocheExtension::common.name -> compilation.name
    compilation.name == SourceSet.MAIN_SOURCE_SET_NAME -> target.name
    else -> lowerCamelCaseName(target.name, compilation.name)
}

context(Project, MinecraftTarget) val RunnableCompilation.sourceSet: SourceSet
    get() {
        this@RunnableCompilation as RunnableCompilationInternal

        val name = sourceSetName(this, this@MinecraftTarget)

        val sourceSets = project.extension<SourceSetContainer>()

        return sourceSets.findByName(name) ?: sourceSets.create(name) {
            project.dependencies.add(it.compileOnlyConfigurationName, compileClasspath)
            project.dependencies.add(it.runtimeOnlyConfigurationName, runtimeClasspath)
        }
    }

context(Project, MinecraftTarget) val RunnableCompilation.singleTargetSourceSet: SourceSet
    get() {
        this@RunnableCompilation as RunnableCompilationInternal

        val sourceSets = project.extension<SourceSetContainer>()

        return sourceSets.findByName(name) ?: sourceSets.create(name) {
            project.dependencies.add(it.compileOnlyConfigurationName, compileClasspath)
            project.dependencies.add(it.runtimeOnlyConfigurationName, runtimeClasspath)
        }
    }

context(Project, CommonTarget) val Compilation.sourceSet: SourceSet
    get() = project
        .extension<SourceSetContainer>()
        .maybeCreate(sourceSetName(this, this@CommonTarget))
