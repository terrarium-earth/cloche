package earth.terrarium.cloche

import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import org.gradle.api.artifacts.component.ComponentIdentifier

private val ComponentIdentifier.isMixin
    get() = "sponge" in displayName && "mixin" in displayName

fun MinecraftRunConfiguration.addMixinJavaAgent(): MinecraftRunConfiguration {
    val args = sourceSet.flatMap {
        project.configurations.named(it.runtimeClasspathConfigurationName).map { configuration ->
            configuration.incoming.artifactView { view ->
                view.componentFilter(ComponentIdentifier::isMixin)
            }.files
        }
    }.flatMap { mixins ->
        compileArguments(mixins.map { mixinJar ->
            compileArgument("-javaagent:", mixinJar)
        })
    }

    jvmArguments.addAll(args)

    return this
}
