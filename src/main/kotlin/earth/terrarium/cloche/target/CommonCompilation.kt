package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import org.gradle.api.Action
import javax.inject.Inject

abstract class CommonCompilation @Inject constructor(private val name: String) : CompilationInternal {
    override val dependencySetupActions = mutableListOf<Action<ClocheDependencyHandler>>()

    override fun dependencies(action: Action<ClocheDependencyHandler>) {
        dependencySetupActions.add(action)
    }

    override fun getName() = name
}
