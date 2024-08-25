package earth.terrarium.cloche.target

import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import javax.inject.Inject

abstract class SimpleRunnable @Inject constructor(private val name: String) : RunnableInternal {
    private val _runSetupActions = mutableListOf<Action<MinecraftRunConfigurationBuilder>>()

    override val runSetupActions: List<Action<MinecraftRunConfigurationBuilder>>
        get() = _runSetupActions

    override fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>) {
        _runSetupActions.add(action)
    }

    override fun getName() = name
}
