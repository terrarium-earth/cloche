package earth.terrarium.cloche

import earth.terrarium.cloche.ClochePlugin.Companion.IDEA_SYNC_TASK_NAME
import earth.terrarium.cloche.api.target.MinecraftTarget
import earth.terrarium.cloche.target.MinecraftTargetInternal
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.PluginAware

fun Project.ideaSyncHook() {
    tasks.register(IDEA_SYNC_TASK_NAME)

    if (!System.getProperty("idea.sync.active", "false").toBoolean() && !System.getProperty("plugin.dir", "false").toBoolean()) {
        return
    }

    val taskPath = ":$IDEA_SYNC_TASK_NAME"

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
    target: MinecraftTarget<*>,
    singleTarget: Boolean,
) {
    target as MinecraftTargetInternal<*>

    target.minecraftVersion.convention(cloche.minecraftVersion)

    cloche.mappingActions.all(target::mappings)

    with(project) {
        handleTarget(target, singleTarget)
    }
}

class ClochePlugin<T : PluginAware> : Plugin<T> {
    override fun apply(target: T) {
        when (target) {
            is Project -> {
                applyToProject(target)
            }

            is Settings -> {
                ClocheRepositoriesExtension.register(target.dependencyResolutionManagement.repositories)
            }
        }
    }

    companion object {
        const val SERVER_RUNNABLE_NAME = "server"
        const val CLIENT_COMPILATION_NAME = "client"
        const val DATA_COMPILATION_NAME = "data"

        const val IDEA_SYNC_TASK_NAME = "clocheIdeaSync"

        const val STUB_GROUP = "net.msrandom"
        const val STUB_NAME = "stub"
        const val STUB_VERSION = "0.0.0"
        const val STUB_MODULE = "$STUB_GROUP:$STUB_NAME"
        const val STUB_DEPENDENCY = "$STUB_MODULE:$STUB_VERSION"

        const val KOTLIN_JVM_PLUGIN_ID = "org.jetbrains.kotlin.jvm"
    }
}
