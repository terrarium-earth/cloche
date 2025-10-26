package earth.terrarium.cloche

import earth.terrarium.cloche.ClochePlugin.Companion.IDE_SYNC_TASK_NAME
import earth.terrarium.cloche.util.isIdeDetected
import earth.terrarium.cloche.api.target.MinecraftTarget
import earth.terrarium.cloche.target.MinecraftTargetInternal
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.PluginAware
import org.gradle.util.GradleVersion

internal fun Project.requireGroup() {
    if (group.toString().isEmpty()) {
        throw InvalidUserCodeException("Group was not set for $project. Please set 'group' in either a gradle.properties file or a build script like build.gradle(.kts)")
    }
}

internal fun Project.ideSyncHook() {
    if (!isIdeDetected()) {
        return
    }

    val taskPath = ":$IDE_SYNC_TASK_NAME"

    val fullName = if (project == project.rootProject) {
        taskPath
    } else {
        project.path + taskPath
    }

    val startParameter = project.gradle.startParameter

    startParameter.setTaskNames(startParameter.taskNames + fullName)
}

fun Project.extend(
    base: String,
    dependency: String,
) = project.configurations.findByName(dependency)?.let {
    project.configurations.findByName(base)?.extendsFrom(it)
}

internal fun addTarget(
    cloche: ClocheExtension,
    project: Project,
    target: MinecraftTarget,
) {
    target as MinecraftTargetInternal

    target.minecraftVersion.convention(cloche.minecraftVersion)

    cloche.mappingActions.all(target::mappings)
    cloche.metadata.useAsConventionFor(target.metadata)

    with(project) {
        handleTarget(target)
    }
}

class ClochePlugin<T : PluginAware> : Plugin<T> {
    override fun apply(target: T) {
        val currentGradle = GradleVersion.current()

        if (currentGradle < MINIMUM_GRADLE) {
            throw InvalidUserCodeException("Current Gradle version is ${currentGradle.version} while the minimum supported version is ${MINIMUM_GRADLE.version}")
        }

        when (target) {
            is Project -> {
                applyToProject(target)
            }

            is Settings -> {
                ClocheRepositoriesExtension.register(target.dependencyResolutionManagement.repositories)
            }
        }
    }

    internal companion object {
        const val SERVER_RUNNABLE_NAME = "server"
        const val CLIENT_COMPILATION_NAME = "client"
        const val DATA_COMPILATION_NAME = "data"

        const val IDE_SYNC_TASK_NAME = "clocheIdeSync"
        const val WRITE_MOD_ID_TASK_NAME = "writeModId"

        const val STUB_GROUP = "net.msrandom"
        const val STUB_NAME = "stub"
        const val STUB_VERSION = "0.0.0"
        const val STUB_MODULE = "$STUB_GROUP:$STUB_NAME"
        const val STUB_DEPENDENCY = "$STUB_MODULE:$STUB_VERSION"

        const val KOTLIN_JVM_PLUGIN_ID = "org.jetbrains.kotlin.jvm"

        @JvmField
        val MINIMUM_GRADLE: GradleVersion = GradleVersion.version("9.0.0")
    }
}
