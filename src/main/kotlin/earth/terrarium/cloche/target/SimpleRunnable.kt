package earth.terrarium.cloche.target

import net.msrandom.minecraftcodev.runs.MinecraftRunConfigurationBuilder
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

abstract class SimpleRunnable @Inject constructor(private val name: String, objectFactory: ObjectFactory) : RunnableInternal {
    override val runSetupActions = objectFactory.domainObjectSet(Action::class.java) as DomainObjectCollection<Action<MinecraftRunConfigurationBuilder>>

    override fun runConfiguration(action: Action<MinecraftRunConfigurationBuilder>) {
        runSetupActions.add(action)
    }

    override fun getName() = name
}
