package earth.terrarium.cloche.api.run

import earth.terrarium.cloche.addMixinJavaAgent
import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.target.MinecraftTarget
import earth.terrarium.cloche.target.TargetCompilation
import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
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

internal fun commonDescription(name: String) = "$name()"
internal fun quotedDescription(name: String) = "'${commonDescription(name)}'"

internal fun MinecraftRunConfiguration.withCompilation(compilation: TargetCompilation): MinecraftRunConfiguration {
    return sourceSet(compilation.sourceSet)
        .addMixinJavaAgent(compilation.target.withMixinAgent)
        .beforeRun(compilation.generateModOutputs)
}

internal fun MinecraftRunConfiguration.withCompilation(
    target: MinecraftTarget,
    compilation: Provider<TargetCompilation>,
    description: () -> String,
): MinecraftRunConfiguration {
    // afterEvaluate needed to query property
    project.afterEvaluate {
        if (!compilation.isPresent) {
            throw InvalidUserCodeException("Run configuration '$name' did not have pre-requisite source sets configured. Please add ${description()} to the target '${target.name}'.")
        }
    }

    return sourceSet(compilation.map(TargetCompilation::sourceSet))
        .addMixinJavaAgent(target.withMixinAgent)
        .beforeRun(compilation.flatMap(TargetCompilation::generateModOutputs))
}
