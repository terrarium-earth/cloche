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
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withModule
import org.gradle.kotlin.dsl.withType

private fun propertyName(name: String) = "earth.terrarium.cloche.$name"

private fun Project.checkFlag(name: String) =
    project.findProperty(propertyName(name))?.toString()?.toBoolean() == true

fun applyToProject(target: Project) {
    val cloche = target.extensions.create("cloche", ClocheExtension::class)

    target.apply<MinecraftCodevFabricPlugin<*>>()
    target.apply<MinecraftCodevForgePlugin<*>>()
    target.apply<MinecraftCodevRemapperPlugin<*>>()
    target.apply<MinecraftCodevIncludesPlugin<*>>()
    target.apply<MinecraftCodevDecompilerPlugin<*>>()
    target.apply<MinecraftCodevAccessWidenerPlugin<*>>()
    target.apply<MinecraftCodevMixinsPlugin<*>>()
    target.apply<MinecraftCodevRunsPlugin<*>>()

    target.apply<JavaVirtualSourceSetsPlugin>()

    if (!target.checkFlag("disable-class-extensions")) {
        target.apply<ClassExtensionsPlugin>()
    }

    target.apply<JavaLibraryPlugin>()

    target.plugins.withType<MavenPublishPlugin> {
        target.extension<PublishingExtension>().publications.configureEach {
            if (this !is MavenPublication) {
                return@configureEach
            }

            // afterEvaluate needed to query value of property
            target.afterEvaluate {
                val error = "artifactId set for publication '${this@configureEach.name}' in $target. This is heavily discouraged as it can break core capability functionality."

                if (artifactId != name) {
                    if (target.checkFlag("allow-maven-artifact-id")) {
                        target.logger.warn("WARNING: $error")
                    } else {
                        throw InvalidUserCodeException("$error If you explicitly want opt-in to artifact-id, set the ${propertyName("allow-maven-artifact-id")} property")
                    }
                }
            }
        }
    }

    ClocheRepositoriesExtension.register(target.repositories)

    target.dependencies.attributesSchema {
        attribute(CompilationAttributes.SIDE) {
            compatibilityRules.add(SideCompatibilityRule::class)
            disambiguationRules.add(SideDisambiguationRule::class)
        }

        attribute(CompilationAttributes.DATA) {
            compatibilityRules.add(DataCompatibilityRule::class)
            disambiguationRules.add(DataDisambiguationRule::class)
        }

        attribute(ClocheTargetAttribute.ATTRIBUTE) {
            compatibilityRules.add(ClocheTargetAttribute.CompatibilityRule::class)
        }
    }

    target.dependencies.artifactTypes {
        named(ArtifactTypeDefinition.JAR_TYPE) {
            attributes
                .attribute(REMAPPED_ATTRIBUTE, false)
                .attribute(NO_NAME_MAPPING_ATTRIBUTE, false)
                .attribute(IncludeTransformationStateAttribute.ATTRIBUTE, IncludeTransformationStateAttribute.None)
                .attribute(ClocheTargetAttribute.ATTRIBUTE, ClocheTargetAttribute.INITIAL)
        }
    }

    val writeModId = target.tasks.register<WriteModId>(WRITE_MOD_ID_TASK_NAME) {
        modId.set(cloche.metadata.modId)
        outputFile.set(target.layout.buildDirectory.file("modId.txt"))
    }

    target.configurations.consumable("modId") {
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, target.objects.named(MOD_ID_CATEGORY))

        outgoing.artifact(writeModId.flatMap(WriteModId::outputFile))
    }

    target.tasks.register(IDE_SYNC_TASK_NAME) {
        dependsOn(writeModId)
    }

    target.ideSyncHook()

    target.dependencies.components.withModule<ForgeLexToNeoComponentMetadataRule>("net.minecraftforge:forge") {
        params(
            getGlobalCacheDirectory(target),
            VERSION_MANIFEST_URL,
            target.gradle.startParameter.isOffline,
        )
    }

    target.dependencies.components.withModule<McpConfigToNeoformComponentMetadataRule>("de.oceanlabs.mcp:mcp_config") {
        params(
            getGlobalCacheDirectory(target),
        )
    }

    applyTargets(target, cloche)
}
