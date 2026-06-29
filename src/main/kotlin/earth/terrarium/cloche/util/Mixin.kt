package earth.terrarium.cloche.util

import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.provider.Property

private val ComponentIdentifier.isMixin
    get() = "sponge" in displayName && "mixin" in displayName

fun MinecraftRunConfiguration.addMixinJavaAgent(withMixinAgent: Property<Boolean>): MinecraftRunConfiguration {
    val objects = project.objects
    val empty = project.provider { emptyList<String>() }

    val args = sourceSet.flatMap {
        project.configurations.named(it.runtimeClasspathConfigurationName).flatMap { configuration ->
            configuration.incoming.artifactView {
                componentFilter(ComponentIdentifier::isMixin)
            }.files.elements
        }
    }.flatMap { mixins ->
        MinecraftRunConfiguration.compileArguments(objects, mixins.map { mixinJar ->
            MinecraftRunConfiguration.compileArgument(objects, "-javaagent:", mixinJar.asFile)
        })
    }

    jvmArguments.addAll(withMixinAgent.flatMap {
        if (it) {
            args
        } else {
            empty
        }
    })

    return this
}
