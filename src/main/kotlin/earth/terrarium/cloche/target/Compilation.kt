package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.ClocheExtension
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.Project
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

interface Runnable : Named {
    fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>)
}

interface RunnableInternal : Runnable {
    val runSetupActions: List<Action<MinecraftRunConfigurationBuilder>>
}

interface RunnableCompilation : Runnable, Compilation

interface RunnableCompilationInternal : CompilationInternal, RunnableCompilation, RunnableInternal {
    val dependencyMinecraftFile: Provider<RegularFile>
        @Internal get

    val finalMinecraftFiles: FileCollection
        @Internal get
}

private fun sourceSetName(compilation: Compilation, target: ClocheTarget) = when {
    target.name == ClocheExtension::common.name -> compilation.name
    compilation.name == SourceSet.MAIN_SOURCE_SET_NAME -> target.name
    '-' in target.name -> "${target.name}-${compilation.name}"
    else -> lowerCamelCaseGradleName(target.name, compilation.name)
}

context(Project, MinecraftTarget) val RunnableCompilationInternal.sourceSet: SourceSet
    get() {
        val cloche = project.extension<ClocheExtension>()

        val name = if (cloche.isSingleTargetMode) {
            name
        } else {
            sourceSetName(this, this@MinecraftTarget)
        }

        return project.extension<SourceSetContainer>().maybeCreate(name)
    }

context(Project, CommonTarget) val CompilationInternal.sourceSet: SourceSet
    get() {
        return project.extension<SourceSetContainer>().maybeCreate(sourceSetName(this, this@CommonTarget))
    }
