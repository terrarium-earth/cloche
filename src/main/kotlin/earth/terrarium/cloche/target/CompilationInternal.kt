package earth.terrarium.cloche.target

import earth.terrarium.cloche.COMMON
import earth.terrarium.cloche.api.target.compilation.ClocheDependencyHandler
import earth.terrarium.cloche.api.target.ClocheTarget
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.compilation.Compilation
import net.msrandom.minecraftcodev.accesswidener.accessWidenersConfigurationName
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

@JvmDefaultWithoutCompatibility
internal abstract class CompilationInternal : Compilation {
    val dependencyHandler: ClocheDependencyHandler = project.objects.newInstance(ClocheDependencyHandler::class.java)
    val attributeActions: DomainObjectCollection<Action<AttributeContainer>> = project.objects.domainObjectSet(Action::class.java) as DomainObjectCollection<Action<AttributeContainer>>

    var withJavadoc: Boolean = false
    var withSources: Boolean = false

    val featureName
        get() = lowerCamelCaseGradleName(name)

    val capabilityName
        get() = name.replace(TARGET_NAME_PATH_SEPARATOR, '-')

    val namePath
        get() = name.replace(TARGET_NAME_PATH_SEPARATOR, '/')

    val namePart
        get() = featureName.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }

    override fun withJavadocJar() {
        withJavadoc = true
    }

    override fun withSourcesJar() {
        withSources = true
    }

    override fun dependencies(action: Action<ClocheDependencyHandler>) =
        action.execute(dependencyHandler)

    override fun attributes(action: Action<AttributeContainer>) {
        attributeActions.add(action)
    }

    open fun attributes(attributes: AttributeContainer) {
        attributeActions.all {
            it.execute(attributes)
        }
    }

    fun addDependencies() {
        project.configurations.named(sourceSet.implementationConfigurationName) {
            it.dependencies.addAllLater(dependencyHandler.implementation.dependencies)
            it.dependencyConstraints.addAllLater(dependencyHandler.implementation.dependencyConstraints)

            it.dependencies.addAllLater(dependencyHandler.modImplementation.dependencies)
            it.dependencyConstraints.addAllLater(dependencyHandler.modImplementation.dependencyConstraints)
        }

        project.configurations.named(sourceSet.compileOnlyConfigurationName) {
            it.dependencies.addAllLater(dependencyHandler.compileOnly.dependencies)
            it.dependencyConstraints.addAllLater(dependencyHandler.compileOnly.dependencyConstraints)

            it.dependencies.addAllLater(dependencyHandler.modCompileOnly.dependencies)
            it.dependencyConstraints.addAllLater(dependencyHandler.modCompileOnly.dependencyConstraints)
        }

        project.configurations.named(sourceSet.runtimeOnlyConfigurationName) {
            it.dependencies.addAllLater(dependencyHandler.runtimeOnly.dependencies)
            it.dependencyConstraints.addAllLater(dependencyHandler.runtimeOnly.dependencyConstraints)

            it.dependencies.addAllLater(dependencyHandler.modRuntimeOnly.dependencies)
            it.dependencyConstraints.addAllLater(dependencyHandler.modRuntimeOnly.dependencyConstraints)
        }

        project.configurations.named(sourceSet.apiConfigurationName) {
            it.dependencies.addAllLater(dependencyHandler.api.dependencies)
            it.dependencyConstraints.addAllLater(dependencyHandler.api.dependencyConstraints)

            it.dependencies.addAllLater(dependencyHandler.modApi.dependencies)
            it.dependencyConstraints.addAllLater(dependencyHandler.modApi.dependencyConstraints)
        }

        project.configurations.named(sourceSet.compileOnlyApiConfigurationName) {
            it.dependencies.addAllLater(dependencyHandler.compileOnlyApi.dependencies)
            it.dependencyConstraints.addAllLater(dependencyHandler.compileOnlyApi.dependencyConstraints)

            it.dependencies.addAllLater(dependencyHandler.modCompileOnlyApi.dependencies)
            it.dependencyConstraints.addAllLater(dependencyHandler.modCompileOnlyApi.dependencyConstraints)
        }

        project.configurations.named(sourceSet.accessWidenersConfigurationName) {
            it.dependencies.addAllLater(dependencyHandler.modImplementation.dependencies)
            it.dependencyConstraints.addAllLater(dependencyHandler.modImplementation.dependencyConstraints)

            it.dependencies.addAllLater(dependencyHandler.modCompileOnly.dependencies)
            it.dependencyConstraints.addAllLater(dependencyHandler.modCompileOnly.dependencyConstraints)

            it.dependencies.addAllLater(dependencyHandler.modApi.dependencies)
            it.dependencyConstraints.addAllLater(dependencyHandler.modApi.dependencyConstraints)

            it.dependencies.addAllLater(dependencyHandler.modCompileOnlyApi.dependencies)
            it.dependencyConstraints.addAllLater(dependencyHandler.modCompileOnlyApi.dependencyConstraints)
        }
    }

    override fun toString() = target.name + TARGET_NAME_PATH_SEPARATOR + name
}

internal fun sourceSetName(compilationName: String, target: ClocheTarget) = when {
    target.name == COMMON -> lowerCamelCaseGradleName(compilationName)
    compilationName == SourceSet.MAIN_SOURCE_SET_NAME -> target.featureName
    else -> lowerCamelCaseGradleName(target.featureName, compilationName)
}

internal fun Project.configureSourceSet(sourceSet: SourceSet, target: ClocheTarget, compilation: CompilationInternal, singleTarget: Boolean) {
    if (!singleTarget) {
        val compilationDirectory = project.layout.projectDirectory.dir("src").dir(target.namePath).dir(compilation.namePath)

        sourceSet.java.srcDir(compilationDirectory.dir("java"))
        sourceSet.resources.srcDir(compilationDirectory.dir("resources"))

        plugins.withId("org.jetbrains.kotlin.jvm") {
            val kotlin = (sourceSet.extensions.getByName("kotlin") as SourceDirectorySet)

            kotlin.srcDir(compilationDirectory.dir("kotlin"))
        }

        // TODO Groovy + Scala?
    }

    tasks.named(sourceSet.jarTaskName, Jar::class.java) {
        val dev = (target as? MinecraftTargetInternal<*>)?.remapNamespace?.map { it.isNotEmpty() } ?: provider { false }

        if (!singleTarget && target.name != COMMON) {
            val classifier = if (compilation.name == SourceSet.MAIN_SOURCE_SET_NAME) {
                target.capabilityName
            } else {
                "${target.capabilityName}-${compilation.capabilityName}"
            }

            it.archiveClassifier.set(dev.map {
                if (it) {
                    "$classifier-dev"
                } else {
                    classifier
                }
            })
        } else {
            it.archiveClassifier.set(dev.map { if (it) "dev" else null })
        }
    }
}
