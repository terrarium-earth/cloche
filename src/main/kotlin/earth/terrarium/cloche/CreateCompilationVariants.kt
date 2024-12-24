package earth.terrarium.cloche

import earth.terrarium.cloche.target.ClocheTarget
import earth.terrarium.cloche.target.CompilationInternal
import net.msrandom.minecraftcodev.core.utils.extension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet

internal fun Project.createCompilationVariants(
    target: ClocheTarget,
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
        java.registerFeature(sourceSet.name) { spec ->
            spec.usingSourceSet(sourceSet)

            spec.capability(project.group.toString(), project.name, project.version.toString())

            if (compilation.name == SourceSet.MAIN_SOURCE_SET_NAME) {
                spec.capability(
                    project.group.toString(),
                    "${project.name}-${target.capabilityName}",
                    project.version.toString(),
                )
            } else {
                spec.capability(
                    project.group.toString(),
                    "${project.name}-${target.capabilityName}-${compilation.name}",
                    project.version.toString(),
                )
            }

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
    }
}
