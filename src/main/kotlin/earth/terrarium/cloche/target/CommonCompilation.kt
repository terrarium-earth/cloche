package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

abstract class CommonCompilation @Inject constructor(private val name: String, project: Project) : CompilationInternal {
    final override val sourceSet: Property<SourceSet> =
        project.objects.property(SourceSet::class.java)

    override val dependencySetupActions = mutableListOf<Action<ClocheDependencyHandler>>()

    override fun dependencies(action: Action<ClocheDependencyHandler>) {
        dependencySetupActions.add(action)
    }

    override fun getName() = name
}
