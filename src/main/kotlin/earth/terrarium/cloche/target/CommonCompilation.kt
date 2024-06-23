package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import org.gradle.api.Action
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

abstract class CommonCompilation @Inject constructor(private val name: String) : CompilationInternal {
    final override lateinit var sourceSet: SourceSet
        private set

    override val dependencySetupActions = mutableListOf<Action<ClocheDependencyHandler>>()

    override fun process(sourceSet: SourceSet) {
        this.sourceSet = sourceSet
    }

    override fun dependencies(action: Action<ClocheDependencyHandler>) {
        dependencySetupActions.add(action)
    }

    override fun getName() = name
}
