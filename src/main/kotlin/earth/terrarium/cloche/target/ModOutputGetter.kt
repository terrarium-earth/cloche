package earth.terrarium.cloche.target

import earth.terrarium.cloche.MOD_ID_CATEGORY
import earth.terrarium.cloche.modId
import net.msrandom.minecraftcodev.runs.DependencyModOutputListing
import net.msrandom.minecraftcodev.runs.OutputListings
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance

internal fun Project.modOutputs(compilation: TargetCompilation<*>): OutputListings {
    val objects = project.objects

    val dependencyOutputs = configurations.named(compilation.sourceSet.runtimeClasspathConfigurationName).flatMap { runtimeClasspath ->
        val projectArtifacts = runtimeClasspath.incoming.artifactView {
            attributes
                .attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                .attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES_AND_RESOURCES))

            componentFilter { id ->
                // Only set mod output groups for project dependencies
                id is ProjectComponentIdentifier
            }

            @Suppress("UnstableApiUsage")
            withVariantReselection()
        }.artifacts

        val modIdFiles = runtimeClasspath.incoming.artifactView {
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(MOD_ID_CATEGORY))

            componentFilter { id ->
                id is ProjectComponentIdentifier
            }

            @Suppress("UnstableApiUsage")
            withVariantReselection()
        }.artifacts

        projectArtifacts.resolvedArtifacts.zip(modIdFiles.resolvedArtifacts) { projectFiles, modIds ->
            val modOutputsList = objects.listProperty<DependencyModOutputListing>()

            val modIds = modIds.associateBy {
                it.id.componentIdentifier
            }

            val groupedFiles = projectFiles.filter {
                val libraryElements = it.variant.attributes.getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE)
                val isDirectory = libraryElements?.name == LibraryElements.CLASSES_AND_RESOURCES || libraryElements?.name == LibraryElements.CLASSES || libraryElements?.name == LibraryElements.RESOURCES

                if (isDirectory) {
                    return@filter true
                }

                if (libraryElements?.name == LibraryElements.JAR) {
                    // Explicitly a JAR
                    return@filter false
                }

                // Fallback to filename checking
                '.' !in it.file.name
            }.groupBy {
                it.id.componentIdentifier
            }

            for ((id, artifacts) in groupedFiles) {
                val modIdArtifact = modIds[id] ?: continue

                val modOutputs = objects.newInstance<DependencyModOutputListing>()

                modOutputs.modIdFile.set(modIdArtifact.file)

                for (artifact in artifacts) {
                    modOutputs.outputs.from(artifact.file)
                }

                modOutputsList.add(modOutputs)
            }

            modOutputsList
        }.flatMap { it }
    }

    val listings = objects.newInstance<OutputListings>()

    listings.modId.set(project.modId)
    listings.outputs.from(compilation.modOutputs)
    listings.dependencies.set(dependencyOutputs)

    return listings
}

internal fun Project.modOutputs(compilation: Provider<out TargetCompilation<*>>) =
    compilation.map(::modOutputs)
