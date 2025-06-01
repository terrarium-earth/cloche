package earth.terrarium.cloche.target

import earth.terrarium.cloche.MOD_OUTPUTS_LIBRARY_ELEMENTS
import net.msrandom.minecraftcodev.core.utils.named
import net.msrandom.minecraftcodev.runs.task.GenerateModOutputs
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider

internal fun Project.modOutputs(compilation: TargetCompilation): FileCollection {
    val dependencyOutputs = configurations.named(compilation.sourceSet.runtimeClasspathConfigurationName).map {
        it.incoming.artifactView {
            it.attributes.attribute(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                objects.named(MOD_OUTPUTS_LIBRARY_ELEMENTS),
            )

            it.componentFilter {
                // Only set mod output groups for project dependencies(which we know export that variant)
                it is ProjectComponentIdentifier
            }

            @Suppress("UnstableApiUsage")
            it.withVariantReselection()
        }.files
    }

    val localOutputs = compilation.generateModOutputs.flatMap(GenerateModOutputs::output)

    return files(dependencyOutputs, localOutputs)
}

internal fun Project.modOutputs(compilation: Provider<TargetCompilation>) =
    compilation.map { modOutputs(it) }
