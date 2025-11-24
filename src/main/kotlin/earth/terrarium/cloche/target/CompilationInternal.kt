package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.ClochePlugin.Companion.IDE_SYNC_TASK_NAME
import earth.terrarium.cloche.api.attributes.MinecraftModLoader
import earth.terrarium.cloche.api.target.ClocheTarget
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.compilation.ClocheDependencyHandler
import earth.terrarium.cloche.api.target.compilation.Compilation
import earth.terrarium.cloche.api.target.isSingleTarget
import earth.terrarium.cloche.api.target.targetName
import earth.terrarium.cloche.cloche
import earth.terrarium.cloche.withIdeaModel
import earth.terrarium.cloche.util.optionalDir
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import org.gradle.api.Action
import org.gradle.api.Buildable
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.dsl.Dependencies
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.domainObjectSet
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance

internal fun modConfigurationName(name: String) =
    lowerCamelCaseGradleName("mod", name)

internal fun getNonProjectArtifacts(configuration: Provider<out Configuration>): Provider<ArtifactView> = configuration.map {
    it.incoming.artifactView {
        componentFilter {
            // We do *not* want to build anything during sync.
            it !is ProjectComponentIdentifier
        }
    }
}

val SourceSet.localRuntimeConfigurationName
    get() = lowerCamelCaseGradleName(takeUnless(SourceSet::isMain)?.name, "localRuntime")

val SourceSet.localImplementationConfigurationName
    get() = lowerCamelCaseGradleName(takeUnless(SourceSet::isMain)?.name, "localImplementation")

context(Project)
internal fun getRelevantSyncArtifacts(configurationName: String): Provider<Buildable> =
    getNonProjectArtifacts(configurations.named(configurationName)).map(ArtifactView::getFiles)

internal abstract class CompilationInternal : Compilation, Dependencies {
    abstract val isTest: Boolean

    abstract override val target: ClocheTargetInternal
        @Internal get

    val dependencyHandler: ClocheDependencyHandler by lazy(LazyThreadSafetyMode.NONE) {
        project.objects.newInstance<ClocheDependencyHandler>(target.minecraftVersion)
    }

    @Suppress("UNCHECKED_CAST")
    val attributeActions: DomainObjectCollection<Action<AttributeContainer>> =
        project.objects.domainObjectSet(Action::class) as DomainObjectCollection<Action<AttributeContainer>>

    @Suppress("UNCHECKED_CAST")
    val resolvableAttributeActions: DomainObjectCollection<Action<AttributeContainer>> =
        project.objects.domainObjectSet(Action::class) as DomainObjectCollection<Action<AttributeContainer>>

    var withJavadoc: Boolean = false
    var withSources: Boolean = false

    val featureName
        get() = collapsedName?.let { lowerCamelCaseGradleName(it) }

    val capabilitySuffix: Provider<String>
        get() = target.hasSeparateClient.map {
            val collapsedName = collapsedName

            val base = if (it) {
                if (collapsedName == null) {
                    "server"
                } else if (collapsedName == ClochePlugin.CLIENT_COMPILATION_NAME) {
                    null
                } else {
                    val prefix = "${ClochePlugin.CLIENT_COMPILATION_NAME}:"

                    if (collapsedName.startsWith(prefix)) {
                        collapsedName.substring(prefix.length)
                    } else {
                        "server-$collapsedName"
                    }
                }
            } else {
                collapsedName
            }

            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            base?.replace(TARGET_NAME_PATH_SEPARATOR, '-')
        }

    val namePath
        get() = name.replace(TARGET_NAME_PATH_SEPARATOR, '/')

    val collapsedName
        get() = name.takeUnless(SourceSet.MAIN_SOURCE_SET_NAME::equals)

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
            execute(attributes)
        }
    }

    fun resolvableAttributes(action: Action<AttributeContainer>) {
        resolvableAttributeActions.add(action)
    }

    open fun resolvableAttributes(attributes: AttributeContainer) {
        resolvableAttributeActions.all {
            execute(attributes)
        }
    }

    override fun toString(): String {
        val name = listOfNotNull(target.targetName, name).joinToString(TARGET_NAME_PATH_SEPARATOR.toString())

        if (project == project.rootProject) {
            return project.path + name
        }

        return project.path + TARGET_NAME_PATH_SEPARATOR + name
    }
}

internal fun sourceSetName(target: ClocheTarget, compilationName: String) = when {
    target.isSingleTarget || target.targetName == MinecraftModLoader.common.name -> lowerCamelCaseGradleName(compilationName)
    compilationName == SourceSet.MAIN_SOURCE_SET_NAME -> target.featureName ?: SourceSet.MAIN_SOURCE_SET_NAME
    else -> lowerCamelCaseGradleName(target.featureName, compilationName)
}

internal fun Project.configureSourceSet(
    sourceSet: SourceSet,
    target: ClocheTarget,
    compilation: CompilationInternal,
) {
    if (!target.isSingleTarget) {
        val compilationDirectory =
            project.layout.projectDirectory.dir("src").optionalDir(target.namePath).dir(compilation.namePath)

        sourceSet.java.srcDir(compilationDirectory.dir("java"))
        sourceSet.resources.srcDir(compilationDirectory.dir("resources"))

        plugins.withId("org.jetbrains.kotlin.jvm") {
            val kotlin = (sourceSet.extensions.getByName("kotlin") as SourceDirectorySet)

            kotlin.srcDir(compilationDirectory.dir("kotlin"))
        }

        // TODO Groovy + Scala?
    }

    val syncTask = tasks.named(IDE_SYNC_TASK_NAME) {
        dependsOn(getRelevantSyncArtifacts(sourceSet.compileClasspathConfigurationName))

        if (compilation is TargetCompilation<*>) {
            dependsOn(compilation.finalMinecraftFile)
            dependsOn(compilation.info.extraClasspathFiles)
            dependsOn(getRelevantSyncArtifacts(sourceSet.runtimeClasspathConfigurationName))
        }
    }

    if (compilation is TargetCompilation<*>) {
        withIdeaModel {
            syncTask.configure {
                if (it.module.isDownloadSources) {
                    dependsOn(compilation.sources)
                }
            }
        }
    }

    if (compilation.isTest) {
        return
    }

    val prefix = if (target.isSingleTarget || target.targetName == MinecraftModLoader.common.name) {
        null
    } else {
        target.capabilitySuffix
    }

    val suffix = compilation.capabilitySuffix

    val classifier = if (prefix == null) {
        suffix
    } else {
        suffix.map {
            "$prefix-$it"
        }.orElse(prefix)
    }

    val devClassifier = if (target is MinecraftTargetInternal) {
        classifier.orElse("").zip(target.modRemapNamespace) { classifier, namespace ->
            if (namespace.isEmpty()) {
                classifier.ifEmpty { null }
            } else if (classifier.isNotEmpty()) {
                "$classifier-dev"
            } else {
                "dev"
            }
        }
    } else {
        classifier
    }

    tasks.named<Jar>(sourceSet.jarTaskName) {
        destinationDirectory.set(project.cloche.intermediateOutputsDirectory)

        archiveClassifier.set(devClassifier)
    }

    if (compilation is TargetCompilation<*>) {
        compilation.includeJarTask!!.configure {
            archiveClassifier.set(classifier)
        }

        compilation.remapJarTask!!.configure {
            archiveClassifier.set(classifier)
        }
    }
}
