package earth.terrarium.cloche

import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.tasks.SourceSet

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

private fun getDependencies(component: ResolvedComponentResult): List<ComponentIdentifier> {
    return component.dependencies.filterIsInstance<ResolvedDependencyResult>().flatMap {
        getDependencies(it.selected)
    } + component.id
}

fun addMixinProcessor(project: Project, sourceSet: SourceSet) {
    val files = project.configurations.named(sourceSet.compileClasspathConfigurationName).map {
        val mixinComponents by lazy {
            it.incoming.resolutionResult.allComponents.filter { it.id.isMixin }.flatMap(::getDependencies)
        }

        it.incoming.artifactView {
            it.componentFilter(mixinComponents::contains)
        }.files
    }

    project.dependencies.add(sourceSet.annotationProcessorConfigurationName, project.files(files))
}
