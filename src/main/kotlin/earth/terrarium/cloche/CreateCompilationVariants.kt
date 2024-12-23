package earth.terrarium.cloche

import earth.terrarium.cloche.target.CompilationInternal
import net.msrandom.minecraftcodev.core.utils.extension
import org.gradle.api.Project
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping
import org.gradle.api.plugins.jvm.internal.DefaultJvmFeature
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal

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
        // TODO Make this somehow not use internals
        val feature = DefaultJvmFeature(
            sourceSet.name,
            sourceSet,
            emptySet(),
            this as ProjectInternal,
            false,
            false,
        )

        feature.withApi()

        if (compilation.withJavadoc) {
            feature.withJavadocJar()
        }

        if (compilation.withSources) {
            feature.withSourcesJar()
        }

        val component = components.getByName("java") as JvmSoftwareComponentInternal

        component.features.add(feature);

        val adhocComponent = component as AdhocComponentWithVariants

        val javadocElements = feature.javadocElementsConfiguration
        if (javadocElements != null) {
            adhocComponent.addVariantsFromConfiguration(javadocElements, JavaConfigurationVariantMapping("runtime", true));
        }

        val sourcesElements = feature.sourcesElementsConfiguration;
        if (sourcesElements != null) {
            adhocComponent.addVariantsFromConfiguration(sourcesElements, JavaConfigurationVariantMapping("runtime", true));
        }

        if (publish) {
            adhocComponent.addVariantsFromConfiguration(feature.apiElementsConfiguration, JavaConfigurationVariantMapping("compile", true, feature.compileClasspathConfiguration));
            adhocComponent.addVariantsFromConfiguration(feature.runtimeElementsConfiguration, JavaConfigurationVariantMapping("runtime", true, feature.runtimeClasspathConfiguration));
        }

        val consumableConfigurations = listOf(
            sourceSet.apiElementsConfigurationName,
            sourceSet.runtimeElementsConfigurationName,
            sourceSet.javadocElementsConfigurationName,
            sourceSet.sourcesElementsConfigurationName,
        )

        for (name in consumableConfigurations) {
            configurations.findByName(name)?.outgoing?.capabilities?.clear()
        }
    }
}
