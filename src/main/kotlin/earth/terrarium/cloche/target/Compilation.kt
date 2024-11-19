package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.ClocheExtension
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.FeatureSpec
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.util.internal.GUtil
import org.gradle.util.internal.TextUtil

interface Compilation : Named {
    val accessWideners: ConfigurableFileCollection
        @InputFiles get

    val mixins: ConfigurableFileCollection
        @InputFiles get

    fun dependencies(action: Action<ClocheDependencyHandler>)

    fun java(action: Action<FeatureSpec>)

    fun attributes(action: Action<AttributeContainer>)
}

@JvmDefaultWithoutCompatibility
internal interface CompilationInternal : Compilation {
    val dependencySetupActions: MutableList<Action<ClocheDependencyHandler>>
    val javaFeatureActions: MutableList<Action<FeatureSpec>>
    val attributeActions: MutableList<Action<AttributeContainer>>

    val capabilityGroup: String
    val capabilityName: String

    val capability
        get() = "$capabilityGroup:$capabilityName"

    override fun dependencies(action: Action<ClocheDependencyHandler>) {
        dependencySetupActions.add(action)
    }

    override fun java(action: Action<FeatureSpec>) {
        javaFeatureActions.add(action)
    }

    override fun attributes(action: Action<AttributeContainer>) {
        attributeActions.add(action)
    }

    fun attributes(attributes: AttributeContainer) {
        for (action in attributeActions) {
            action.execute(attributes)
        }
    }
}

interface Runnable : Named {
    fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>)
}

internal interface RunnableInternal : Runnable {
    val runSetupActions: List<Action<MinecraftRunConfigurationBuilder>>
}

interface RunnableCompilation : Runnable, Compilation

internal interface RunnableCompilationInternal : CompilationInternal, RunnableCompilation, RunnableInternal {
    val finalMinecraftFiles: FileCollection
        @Internal get

    val minecraftConfiguration: MinecraftConfiguration

    val target: MinecraftTargetInternal
}

private fun sourceSetName(compilation: Compilation, target: ClocheTarget) = when {
    target.name == ClocheExtension::common.name -> compilation.name
    compilation.name == SourceSet.MAIN_SOURCE_SET_NAME -> GUtil.toLowerCamelCase(target.featureName)
    else -> lowerCamelCaseGradleName(target.featureName, compilation.name)
}

context(Project, MinecraftTarget) internal val RunnableCompilationInternal.sourceSet: SourceSet
    get() {
        val cloche = project.extension<ClocheExtension>()

        val name = if (cloche.isSingleTargetMode) {
            name
        } else {
            sourceSetName(this, this@MinecraftTarget)
        }

        return project.extension<SourceSetContainer>().maybeCreate(name)
    }

context(Project, CommonTarget) internal val CompilationInternal.sourceSet: SourceSet
    get() {
        return project.extension<SourceSetContainer>().maybeCreate(sourceSetName(this, this@CommonTarget))
    }

internal fun Project.configureSourceSet(sourceSet: SourceSet, target: ClocheTarget, compilation: Compilation) {
    val cloche = project.extension<ClocheExtension>()

    if (!cloche.isSingleTargetMode) {
        val compilationDirectory = project.layout.projectDirectory.dir("src").dir(target.name).dir(compilation.name)

        sourceSet.java.srcDir(compilationDirectory.dir("java"))
        sourceSet.resources.srcDir(compilationDirectory.dir("resources"))

        plugins.withId("org.jetbrains.kotlin.jvm") {
            val kotlin = (sourceSet.extensions.getByName("kotlin") as SourceDirectorySet)

            kotlin.srcDir(compilationDirectory.dir("kotlin"))
        }

        // TODO Groovy + Scala?
    }

    project.tasks.named(sourceSet.jarTaskName, Jar::class.java) {
        if (!cloche.isSingleTargetMode && target.name != ClocheExtension::common.name) {
            val classifier = if (compilation.name == SourceSet.MAIN_SOURCE_SET_NAME) {
                target.featureName
            } else {
                "${TextUtil.camelToKebabCase(target.featureName)}-${compilation.name}"
            }

            it.archiveClassifier.set(classifier)
        }
    }
}
