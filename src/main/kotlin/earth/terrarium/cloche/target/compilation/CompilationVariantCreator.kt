package earth.terrarium.cloche.target.compilation

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.requireGroup
import net.msrandom.minecraftcodev.core.utils.extension
import org.gradle.api.Project
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

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

        java.registerFeature(sourceSet.name) {
            usingSourceSet(sourceSet)

            capability(project.group.toString(), project.name, project.version.toString())

            if (compilation.withJavadoc) {
                withJavadocJar()
            }

            if (compilation.withSources) {
                withSourcesJar()
            }

            if (!publish) {
                disablePublication()
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
            outgoing.capability(compilationCapability)

            attributes {
                attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))

                plugins.withId(ClochePlugin.Companion.KOTLIN_JVM_PLUGIN_ID) {
                    attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
                }
            }
        }
    }

    if (sourceSet.externalRuntimeConfigurationName !in configurations.names) {
        configurations.dependencyScope(sourceSet.externalRuntimeConfigurationName)
    }

    if (sourceSet.externalCompileConfigurationName !in configurations.names) {
        configurations.dependencyScope(sourceSet.externalCompileConfigurationName)
    }

    if (sourceSet.externalApiConfigurationName !in configurations.names) {
        configurations.dependencyScope(sourceSet.externalApiConfigurationName)
    }
}
