package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.FeatureSpec
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

internal abstract class CommonCompilation @Inject constructor(private val name: String, project: Project) : CompilationInternal {
    override val capabilityGroup = project.group.toString()

    override val capabilityName: String = if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
        project.name
    } else {
        "${project.name}-$name"
    }

    override val dependencySetupActions = mutableListOf<Action<ClocheDependencyHandler>>()
    override val javaFeatureActions = mutableListOf<Action<FeatureSpec>>()

    override fun dependencies(action: Action<ClocheDependencyHandler>) {
        dependencySetupActions.add(action)
    }

    override fun java(action: Action<FeatureSpec>) {
        javaFeatureActions.add(action)
    }

    override fun getName() = name
}
