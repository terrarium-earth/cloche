package earth.terrarium.cloche.api.run

import earth.terrarium.cloche.api.LazyConfigurable
import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import org.gradle.api.Project
import javax.inject.Inject

interface RunConfigurations {
    val project: Project
        @Inject get

    val server: LazyConfigurable<MinecraftRunConfiguration>
    val client: LazyConfigurable<MinecraftRunConfiguration>

    val data: LazyConfigurable<MinecraftRunConfiguration>
    val clientData: LazyConfigurable<MinecraftRunConfiguration>

    val test: LazyConfigurable<MinecraftRunConfiguration>
    val clientTest: LazyConfigurable<MinecraftRunConfiguration>
}
