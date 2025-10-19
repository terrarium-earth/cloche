package earth.terrarium.cloche

import earth.terrarium.cloche.target.CompilationInternal
import net.msrandom.minecraftcodev.core.utils.extension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet

internal fun Project.createCompilationVariants(
    compilation: CompilationInternal,
    sourceSet: SourceSet,
    publish: Boolean,
) {
    val java = project.extension<JavaPluginExtension>()

    if (SourceSet.isMain(sourceSet)) {
        if (compilation.withJavadoc) {
            java.withJavadocJar()
        }

        if (compilation.withSources) {
            java.withSourcesJar()
        }
    } else {
        requireGroup()

        java.registerFeature(sourceSet.name) { spec ->
            spec.usingSourceSet(sourceSet)

            spec.capability(project.group.toString(), project.name, project.version.toString())

            if (compilation.withJavadoc) {
                spec.withJavadocJar()
            }

            if (compilation.withSources) {
                spec.withSourcesJar()
            }

            if (!publish) {
                spec.disablePublication()
            }
        }

        // We cannot use lazy capabilities in FeatureSpec yet, thus manually add lazy extra capability to all relevant configurations
        val baseCapabilityName = compilation.target.capabilitySuffix?.let {
            "${project.name}-$it"
        } ?: project.name

        val compilationCapability = compilation.capabilitySuffix.map {
            "${project.group}:$baseCapabilityName-$it:${project.version}"
        }.orElse("${project.group}:$baseCapabilityName:${project.version}")

        val configurationNames = listOf(
            sourceSet.apiElementsConfigurationName,
            sourceSet.runtimeElementsConfigurationName,
            sourceSet.javadocElementsConfigurationName,
            sourceSet.sourcesElementsConfigurationName,
        )

        project.configurations.named { it in configurationNames }.configureEach {
            it.outgoing.capability(compilationCapability)
        }
    }
}
