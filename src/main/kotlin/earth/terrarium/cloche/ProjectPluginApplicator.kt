package earth.terrarium.cloche

import earth.terrarium.cloche.ClochePlugin.Companion.IDE_SYNC_TASK_NAME
import earth.terrarium.cloche.ClochePlugin.Companion.WRITE_MOD_ID_TASK_NAME
import earth.terrarium.cloche.api.attributes.CompilationAttributes
import earth.terrarium.cloche.api.attributes.IncludeTransformationStateAttribute
import earth.terrarium.cloche.tasks.WriteModId
import net.msrandom.classextensions.ClassExtensionsPlugin
import net.msrandom.minecraftcodev.accesswidener.MinecraftCodevAccessWidenerPlugin
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.getGlobalCacheDirectory
import net.msrandom.minecraftcodev.core.utils.named
import net.msrandom.minecraftcodev.decompiler.MinecraftCodevDecompilerPlugin
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.forge.lexforge.ForgeLexToNeoComponentMetadataRule
import net.msrandom.minecraftcodev.forge.lexforge.McpConfigToNeoformComponentMetadataRule
import net.msrandom.minecraftcodev.includes.MinecraftCodevIncludesPlugin
import net.msrandom.minecraftcodev.mixins.MinecraftCodevMixinsPlugin
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.runs.MinecraftCodevRunsPlugin
import net.msrandom.virtualsourcesets.JavaVirtualSourceSetsPlugin
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Category
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

private fun propertyName(name: String) = "earth.terrarium.cloche.$name"

private fun Project.checkFlag(name: String) =
    project.findProperty(propertyName(name))?.toString()?.toBoolean() == true

fun applyToProject(project: Project) {
    val cloche = project.extensions.create("cloche", ClocheExtension::class.java)

    project.plugins.apply(MinecraftCodevFabricPlugin::class.java)
    project.plugins.apply(MinecraftCodevForgePlugin::class.java)
    project.plugins.apply(MinecraftCodevRemapperPlugin::class.java)
    project.plugins.apply(MinecraftCodevIncludesPlugin::class.java)
    project.plugins.apply(MinecraftCodevDecompilerPlugin::class.java)
    project.plugins.apply(MinecraftCodevAccessWidenerPlugin::class.java)
    project.plugins.apply(MinecraftCodevMixinsPlugin::class.java)
    project.plugins.apply(MinecraftCodevRunsPlugin::class.java)

    project.plugins.apply(JavaVirtualSourceSetsPlugin::class.java)

    if (!project.checkFlag("disable-class-extensions")) {
        project.plugins.apply(ClassExtensionsPlugin::class.java)
    }

    project.plugins.apply(JavaLibraryPlugin::class.java)

    project.plugins.withType(MavenPublishPlugin::class.java) {
        project.extension<PublishingExtension>().publications.configureEach { publication ->
            if (publication is MavenPublication) {
                // afterEvaluate needed to query value of property
                project.afterEvaluate {
                    val error = "artifactId set for publication '${publication.name}' in $project. This is heavily discouraged as it can break core capability functionality."

                    if (publication.artifactId != project.name) {
                        if (project.checkFlag("allow-maven-artifact-id")) {
                            project.logger.warn("WARNING: $error")
                        } else {
                            throw InvalidUserCodeException("$error If you explicitly want opt-in to artifact-id, set the ${propertyName("allow-maven-artifact-id")} property")
                        }
                    }
                }
            }
        }
    }

    ClocheRepositoriesExtension.register(project.repositories)

    project.dependencies.attributesSchema { schema ->
        schema.attribute(CompilationAttributes.SIDE) {
            it.compatibilityRules.add(SideCompatibilityRule::class.java)
            it.disambiguationRules.add(SideDisambiguationRule::class.java)
        }

        schema.attribute(CompilationAttributes.DATA) {
            it.compatibilityRules.add(DataCompatibilityRule::class.java)
            it.disambiguationRules.add(DataDisambiguationRule::class.java)
        }
    }

    project.dependencies.artifactTypes {
        it.named(ArtifactTypeDefinition.JAR_TYPE) { jar ->
            jar.attributes
                .attribute(REMAPPED_ATTRIBUTE, false)
                .attribute(NO_NAME_MAPPING_ATTRIBUTE, false)
                .attribute(IncludeTransformationStateAttribute.ATTRIBUTE, IncludeTransformationStateAttribute.None)
        }
    }

    val writeModId = project.tasks.register(WRITE_MOD_ID_TASK_NAME, WriteModId::class.java) {
        it.modId.set(cloche.metadata.modId)
        it.outputFile.set(project.layout.buildDirectory.file("modId.txt"))
    }

    project.configurations.consumable("modId") {
        it.attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(MOD_ID_CATEGORY))

        it.outgoing.artifact(writeModId.flatMap(WriteModId::outputFile))
    }

    project.tasks.register(IDE_SYNC_TASK_NAME) {
        it.dependsOn(writeModId)
    }

    project.ideSyncHook()

    project.dependencies.components.withModule(
        "net.minecraftforge:forge",
        ForgeLexToNeoComponentMetadataRule::class.java
    ) {
        it.params(
            getGlobalCacheDirectory(project),
            VERSION_MANIFEST_URL,
            project.gradle.startParameter.isOffline,
        )
    }

    project.dependencies.components.withModule(
        "de.oceanlabs.mcp:mcp_config",
        McpConfigToNeoformComponentMetadataRule::class.java
    ) {
        it.params(
            getGlobalCacheDirectory(project),
        )
    }

    applyTargets(project, cloche)
}
