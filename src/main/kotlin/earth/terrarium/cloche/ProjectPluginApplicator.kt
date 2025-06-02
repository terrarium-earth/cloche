package earth.terrarium.cloche

import com.google.devtools.ksp.gradle.KspGradleSubplugin
import earth.terrarium.cloche.ClochePlugin.Companion.KOTLIN_JVM_PLUGIN_ID
import net.msrandom.classextensions.ClassExtensionsPlugin
import net.msrandom.minecraftcodev.accesswidener.MinecraftCodevAccessWidenerPlugin
import net.msrandom.minecraftcodev.core.VERSION_MANIFEST_URL
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
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.plugins.JavaLibraryPlugin

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
    project.plugins.apply(ClassExtensionsPlugin::class.java)

    project.plugins.apply(JavaLibraryPlugin::class.java)

    project.plugins.withId(KOTLIN_JVM_PLUGIN_ID) {
        project.plugins.apply(KspGradleSubplugin::class.java)
    }

    ClocheRepositoriesExtension.register(project.repositories)

    project.dependencies.attributesSchema { schema ->
        schema.attribute(SIDE_ATTRIBUTE) {
            it.compatibilityRules.add(SideCompatibilityRule::class.java)
            it.disambiguationRules.add(SideDisambiguationRule::class.java)
        }
    }

    project.dependencies.artifactTypes {
        it.named(ArtifactTypeDefinition.JAR_TYPE) { jar ->
            jar.attributes.attribute(
                ModTransformationStateAttribute.ATTRIBUTE,
                ModTransformationStateAttribute.INITIAL,
            )
            jar.attributes.attribute(NO_NAME_MAPPING_ATTRIBUTE, false)
        }

        it.create(JSON_ARTIFACT_TYPE)
    }

    project.ideaSyncHook()

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
