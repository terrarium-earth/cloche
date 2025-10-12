package earth.terrarium.cloche.target

import earth.terrarium.cloche.COMMON
import earth.terrarium.cloche.ClochePlugin.Companion.IDE_SYNC_TASK_NAME
import earth.terrarium.cloche.api.target.ClocheTarget
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.compilation.ClocheDependencyHandler
import earth.terrarium.cloche.api.target.compilation.Compilation
import earth.terrarium.cloche.cloche
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import org.gradle.api.Action
import org.gradle.api.Buildable
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.ide.idea.model.IdeaModel
import javax.inject.Inject

internal fun modConfigurationName(name: String) =
    lowerCamelCaseGradleName("mod", name)

internal fun getNonProjectArtifacts(configuration: Provider<out Configuration>): Provider<ArtifactView> = configuration.map {
    it.incoming.artifactView {
        it.componentFilter {
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

@JvmDefaultWithoutCompatibility
internal abstract class CompilationInternal : Compilation {
    abstract val isTest: Boolean

    abstract override val target: ClocheTargetInternal
        @Internal get

    abstract val project: Project
        @Inject
        get

    val dependencyHandler: ClocheDependencyHandler by lazy(LazyThreadSafetyMode.NONE) {
        project.objects.newInstance(ClocheDependencyHandler::class.java, target.minecraftVersion)
    }

    @Suppress("UNCHECKED_CAST")
    val attributeActions: DomainObjectCollection<Action<AttributeContainer>> =
        project.objects.domainObjectSet(Action::class.java) as DomainObjectCollection<Action<AttributeContainer>>

    @Suppress("UNCHECKED_CAST")
    val resolvableAttributeActions: DomainObjectCollection<Action<AttributeContainer>> =
        project.objects.domainObjectSet(Action::class.java) as DomainObjectCollection<Action<AttributeContainer>>

    var withJavadoc: Boolean = false
    var withSources: Boolean = false

    val featureName
        get() = collapsedName?.let { lowerCamelCaseGradleName(it) }

    val capabilitySuffix: Provider<String>
        get() = target.hasSeparateClient.map {
            val base = if (it) {
                collapsedName ?: "common"
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
            it.execute(attributes)
        }
    }

    fun resolvableAttributes(action: Action<AttributeContainer>) {
        resolvableAttributeActions.add(action)
    }

    open fun resolvableAttributes(attributes: AttributeContainer) {
        resolvableAttributeActions.all {
            it.execute(attributes)
        }
    }

    override fun toString() = target.name + TARGET_NAME_PATH_SEPARATOR + name
}

internal fun sourceSetName(target: ClocheTarget, compilationName: String) = when {
    target.name == COMMON -> lowerCamelCaseGradleName(compilationName)
    compilationName == SourceSet.MAIN_SOURCE_SET_NAME -> target.featureName
    else -> lowerCamelCaseGradleName(target.featureName, compilationName)
}

internal fun sourceSetName(target: ClocheTarget, compilationName: String, isSingleTarget: Boolean) = if (isSingleTarget) {
    lowerCamelCaseGradleName(compilationName)
} else {
    sourceSetName(target, compilationName)
}

internal fun Project.configureSourceSet(
    sourceSet: SourceSet,
    target: ClocheTarget,
    compilation: CompilationInternal,
    singleTarget: Boolean
) {
    if (!singleTarget) {
        val compilationDirectory =
            project.layout.projectDirectory.dir("src").dir(target.namePath).dir(compilation.namePath)

        sourceSet.java.srcDir(compilationDirectory.dir("java"))
        sourceSet.resources.srcDir(compilationDirectory.dir("resources"))

        plugins.withId("org.jetbrains.kotlin.jvm") {
            val kotlin = (sourceSet.extensions.getByName("kotlin") as SourceDirectorySet)

            kotlin.srcDir(compilationDirectory.dir("kotlin"))
        }

        // TODO Groovy + Scala?
    }

    val syncTask = tasks.named(IDE_SYNC_TASK_NAME) { task ->
        task.dependsOn(getRelevantSyncArtifacts(sourceSet.compileClasspathConfigurationName))

        if (compilation is TargetCompilation) {
            task.dependsOn(compilation.finalMinecraftFile)
            task.dependsOn(compilation.info.extraClasspathFiles)
            task.dependsOn(getRelevantSyncArtifacts(sourceSet.runtimeClasspathConfigurationName))
        }
    }

    if (compilation is TargetCompilation) {
        syncTask.configure { task ->
            task.dependsOn(compilation.generateModOutputs)
        }

        if (project == rootProject) {
            // afterEvaluate required as isDownloadSources is not lazy
            afterEvaluate { project ->
                syncTask.configure { task ->
                    if (project.extension<IdeaModel>().module.isDownloadSources) {
                        task.dependsOn(compilation.sources)
                    }
                }
            }
        } else {
            syncTask.configure { task ->
                if (rootProject.extension<IdeaModel>().module.isDownloadSources) {
                    task.dependsOn(compilation.sources)
                }
            }
        }
    }

    if (compilation.isTest) {
        return
    }

    val prefix = if (singleTarget || target.name == COMMON) {
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

    tasks.named(sourceSet.jarTaskName, Jar::class.java) {
        it.destinationDirectory.set(project.cloche.intermediateOutputsDirectory)

        it.archiveClassifier.set(devClassifier)
    }

    if (compilation is TargetCompilation) {
        compilation.includeJarTask.configure {
            it.archiveClassifier.set(classifier)
        }

        compilation.remapJarTask.configure {
            it.archiveClassifier.set(classifier)
        }
    }
}
