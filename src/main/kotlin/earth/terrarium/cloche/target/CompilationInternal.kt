package earth.terrarium.cloche.target

import earth.terrarium.cloche.COMMON
import earth.terrarium.cloche.ClochePlugin.Companion.IDEA_SYNC_TASK_NAME
import earth.terrarium.cloche.api.target.ClocheTarget
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.compilation.ClocheDependencyHandler
import earth.terrarium.cloche.api.target.compilation.Compilation
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import org.gradle.api.Action
import org.gradle.api.Buildable
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.ide.idea.model.IdeaModel
import javax.inject.Inject

fun modConfigurationName(name: String) =
    lowerCamelCaseGradleName("mod", name)

context(Project)
fun getNonProjectArtifacts(configurationName: String): Provider<ArtifactView> {
    return configurations.named(configurationName).map {
        it.incoming.artifactView {
            it.componentFilter {
                // We do *not* want to build anything during sync.
                it !is ProjectComponentIdentifier
            }
        }
    }
}

context(Project)
fun getRelevantSyncArtifacts(configurationName: String): Provider<Buildable> {
    return getNonProjectArtifacts(configurationName).map { it.files }
}

@Suppress("UnstableApiUsage")
@JvmDefaultWithoutCompatibility
internal abstract class CompilationInternal : Compilation {
    abstract val project: Project
        @Inject
        get

    val dependencyHandler: ClocheDependencyHandler by lazy(LazyThreadSafetyMode.NONE) {
        project.objects.newInstance(ClocheDependencyHandler::class.java, target.minecraftVersion)
    }

    @Suppress("UNCHECKED_CAST")
    val attributeActions: DomainObjectCollection<Action<AttributeContainer>> =
        project.objects.domainObjectSet(Action::class.java) as DomainObjectCollection<Action<AttributeContainer>>

    var withJavadoc: Boolean = false
    var withSources: Boolean = false

    val featureName
        get() = lowerCamelCaseGradleName(collapsedName)

    val capabilityName
        get() = collapsedName?.replace(TARGET_NAME_PATH_SEPARATOR, '-')

    val namePath
        get() = name.replace(TARGET_NAME_PATH_SEPARATOR, '/')

    val collapsedName
        get() = name.takeUnless { it == SourceSet.MAIN_SOURCE_SET_NAME }

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
        val modImplementation =
            project.configurations.dependencyScope(modConfigurationName(sourceSet.implementationConfigurationName)) {
                it.addCollectedDependencies(dependencyHandler.modImplementation)
            }.get()

        val modRuntimeOnly =
            project.configurations.dependencyScope(modConfigurationName(sourceSet.runtimeOnlyConfigurationName)) {
                it.addCollectedDependencies(dependencyHandler.modRuntimeOnly)
            }.get()

        val modCompileOnly =
            project.configurations.dependencyScope(modConfigurationName(sourceSet.compileOnlyConfigurationName)) {
                it.addCollectedDependencies(dependencyHandler.modCompileOnly)
            }.get()

        val modApi =
            project.configurations.dependencyScope(modConfigurationName(sourceSet.apiConfigurationName)) {
                it.addCollectedDependencies(dependencyHandler.modApi)
            }.get()

        val modCompileOnlyApi =
            project.configurations.dependencyScope(modConfigurationName(sourceSet.compileOnlyApiConfigurationName)) {
                it.addCollectedDependencies(dependencyHandler.modCompileOnlyApi)
            }.get()

        project.configurations.named(sourceSet.implementationConfigurationName) {
            it.extendsFrom(modImplementation)

            it.addCollectedDependencies(dependencyHandler.implementation)
        }

        project.configurations.named(sourceSet.compileOnlyConfigurationName) {
            it.extendsFrom(modCompileOnly)

            it.addCollectedDependencies(dependencyHandler.compileOnly)
        }

        project.configurations.named(sourceSet.runtimeOnlyConfigurationName) {
            it.extendsFrom(modRuntimeOnly)

            it.addCollectedDependencies(dependencyHandler.runtimeOnly)
        }

        project.configurations.named(sourceSet.apiConfigurationName) {
            it.extendsFrom(modApi)

            it.addCollectedDependencies(dependencyHandler.api)
        }

        project.configurations.named(sourceSet.compileOnlyApiConfigurationName) {
            it.extendsFrom(modCompileOnlyApi)

            it.addCollectedDependencies(dependencyHandler.compileOnlyApi)
        }

        project.configurations.named(sourceSet.annotationProcessorConfigurationName) {
            it.addCollectedDependencies(dependencyHandler.annotationProcessor)
        }
    }

    override fun toString() = target.name + TARGET_NAME_PATH_SEPARATOR + name
}

internal fun sourceSetName(compilationName: String, target: ClocheTarget) = when {
    target.name == COMMON -> lowerCamelCaseGradleName(compilationName)
    compilationName == SourceSet.MAIN_SOURCE_SET_NAME -> target.featureName
    else -> lowerCamelCaseGradleName(target.featureName, compilationName)
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

    val prefix = if (singleTarget || target.name == COMMON) {
        null
    } else {
        target.classifierName
    }

    val suffix = compilation.capabilityName

    val classifier = listOfNotNull(prefix, suffix).joinToString("-").takeUnless(String::isEmpty)

    tasks.named(sourceSet.jarTaskName, Jar::class.java) {
        if (target is MinecraftTargetInternal) {
            val archiveClassifier = target.modRemapNamespace.map {
                if (it.isEmpty()) {
                    classifier
                } else if (classifier != null) {
                    "$classifier-dev"
                } else {
                    "dev"
                }
            }

            it.archiveClassifier.set(archiveClassifier)
        } else if (classifier != null) {
            it.archiveClassifier.set(classifier)
        }
    }

    if (compilation is TargetCompilation) {
        compilation.remapJarTask.configure {
            if (classifier != null) {
                it.archiveClassifier.set(classifier)
            }
        }
    }

    val syncTask = tasks.named(IDEA_SYNC_TASK_NAME) { task ->
        task.dependsOn(getRelevantSyncArtifacts(sourceSet.compileClasspathConfigurationName))

        if (compilation is TargetCompilation) {
            task.dependsOn(compilation.finalMinecraftFile)
            task.dependsOn(compilation.extraClasspathFiles)
            task.dependsOn(getRelevantSyncArtifacts(sourceSet.runtimeClasspathConfigurationName))
        }
    }

    if (compilation is TargetCompilation) {
        // afterEvaluate required as isDownloadSources is not lazy
        if (project == rootProject) {
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
}
