package earth.terrarium.cloche.util

import earth.terrarium.cloche.MOD_CLASSPATH_PREFERABLE_ATTRIBUTE
import earth.terrarium.cloche.WITHOUT_DATA_ATTRIBUTE
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.named

internal const val CLASSES_AND_RESOURCES_VARIANT_NAME = "classesAndResources"

internal fun withoutDataName(name: String) = "${name}WithoutData"

internal fun Project.configureClassesAndResourcesVariant(sourceSet: SourceSet) {
    configurations.named(sourceSet.apiElementsConfigurationName) {
        outgoing.variants.named(LibraryElements.CLASSES) {
            attributes {
                attribute(MOD_CLASSPATH_PREFERABLE_ATTRIBUTE, true)
            }
        }
    }

    configurations.named(sourceSet.runtimeElementsConfigurationName) {
        val configuration = this

        val classesAndResourcesVariant = configureClassesAndResourcesVariant(
            CLASSES_AND_RESOURCES_VARIANT_NAME,
            configuration,
            LibraryElements.RESOURCES,
            withoutData = false,
        )

        components.named("java") {
            this as AdhocComponentWithVariants

            withVariantsFromConfiguration(configuration) {
                if (configurationVariant.name == classesAndResourcesVariant.name) {
                    skip()
                }
            }
        }
    }
}

internal fun Project.configureClassesAndResourcesVariant(
    name: String,
    configuration: Configuration,
    resourcesVariantName: String,
    withoutData: Boolean,
): ConfigurationVariant {
    val classesAndResourcesVariants =
        configuration.outgoing.variants.named { it == LibraryElements.CLASSES || it == resourcesVariantName }
    val providers = providers

    return configuration.outgoing.variants.create(name).also { classesAndResourcesVariant ->
        classesAndResourcesVariant.attributes
            .attribute(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                objects.named(LibraryElements.CLASSES_AND_RESOURCES)
            )
            .attribute(MOD_CLASSPATH_PREFERABLE_ATTRIBUTE, true)
            .attribute(WITHOUT_DATA_ATTRIBUTE, withoutData)

        classesAndResourcesVariants.configureEach {
            val classesOrResourcesVariant = this

            classesAndResourcesVariant.artifacts.addAllLater(providers.provider { classesOrResourcesVariant.artifacts })
        }
    }
}
