package earth.terrarium.cloche.target

import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

abstract class SimpleRunnable @Inject constructor(private val name: String, objectFactory: ObjectFactory) : RunnableInternal {
    override val runSetupActions = objectFactory.domainObjectSet(Action::class.java) as DomainObjectCollection<Action<MinecraftRunConfiguration>>

    override fun runConfiguration(action: Action<MinecraftRunConfiguration>) {
        runSetupActions.add(action)
    }

    override fun getName() = name
}
