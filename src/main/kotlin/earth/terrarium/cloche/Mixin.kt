package earth.terrarium.cloche

import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.provider.Property

private val ComponentIdentifier.isMixin
    get() = "sponge" in displayName && "mixin" in displayName

fun MinecraftRunConfiguration.addMixinJavaAgent(withMixinAgent: Property<Boolean>): MinecraftRunConfiguration {
    val args = sourceSet.flatMap {
        project.configurations.named(it.runtimeClasspathConfigurationName).map { configuration ->
            configuration.incoming.artifactView {
                componentFilter(ComponentIdentifier::isMixin)
            }.files
        }
    }.flatMap { mixins ->
        compileArguments(mixins.map { mixinJar ->
            compileArgument("-javaagent:", mixinJar)
        })
    }

    val empty = project.provider { emptyList<String>() }

    jvmArguments.addAll(withMixinAgent.flatMap {
        if (it) {
            args
        } else {
            empty
        }
    })

    return this
}
