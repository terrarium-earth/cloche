package earth.terrarium.cloche.util

import org.gradle.api.Project
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.named

internal const val CLASSES_AND_RESOURCES_VARIANT_NAME = "classesAndResources"

internal fun Project.configureClassesAndResourcesVariant(sourceSet: SourceSet) {
    configurations.named(sourceSet.runtimeElementsConfigurationName) {
        val configuration = this

        val classesAndResourcesVariants = outgoing.variants.named { it == "classes" || it == "resources" }

        val classesAndResourcesVariant = outgoing.variants.maybeCreate(
            CLASSES_AND_RESOURCES_VARIANT_NAME
        ).also { classesAndResourcesVariant ->
            classesAndResourcesVariant.attributes
                .attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    objects.named(LibraryElements.CLASSES_AND_RESOURCES)
                )

            classesAndResourcesVariants.configureEach {
                val classesOrResourcesVariant = this

                classesOrResourcesVariant.artifacts.configureEach {
                    val artifact = this

                    classesAndResourcesVariant.artifact(artifact)
                }
            }
        }

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
