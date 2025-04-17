package earth.terrarium.cloche

import com.google.devtools.ksp.gradle.KspGradleSubplugin
import earth.terrarium.cloche.ClochePlugin.Companion.KOTLIN_JVM_PLUGIN_ID
import earth.terrarium.cloche.target.modConfigurationName
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
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin
import net.msrandom.minecraftcodev.runs.MinecraftCodevRunsPlugin
import net.msrandom.virtualsourcesets.JavaVirtualSourceSetsPlugin
import net.msrandom.virtualsourcesets.SourceSetStaticLinkageInfo
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.tasks.SourceSetContainer

fun applyToProject(target: Project) {
    val cloche = target.extensions.create("cloche", ClocheExtension::class.java)

    target.plugins.apply(MinecraftCodevFabricPlugin::class.java)
    target.plugins.apply(MinecraftCodevForgePlugin::class.java)
    target.plugins.apply(MinecraftCodevRemapperPlugin::class.java)
    target.plugins.apply(MinecraftCodevIncludesPlugin::class.java)
    target.plugins.apply(MinecraftCodevDecompilerPlugin::class.java)
    target.plugins.apply(MinecraftCodevAccessWidenerPlugin::class.java)
    target.plugins.apply(MinecraftCodevMixinsPlugin::class.java)
    target.plugins.apply(MinecraftCodevRunsPlugin::class.java)

    target.plugins.apply(JavaVirtualSourceSetsPlugin::class.java)
    target.plugins.apply(ClassExtensionsPlugin::class.java)

    target.plugins.apply(JavaLibraryPlugin::class.java)

    target.plugins.withId(KOTLIN_JVM_PLUGIN_ID) {
        target.plugins.apply(KspGradleSubplugin::class.java)
    }

    ClocheRepositoriesExtension.register(target.repositories)

    target.dependencies.attributesSchema { schema ->
        schema.attribute(SIDE_ATTRIBUTE) {
            it.compatibilityRules.add(VariantCompatibilityRule::class.java)
            it.disambiguationRules.add(VariantDisambiguationRule::class.java)
        }
    }

    target.dependencies.artifactTypes {
        it.named(ArtifactTypeDefinition.JAR_TYPE) { jar ->
            jar.attributes.attribute(
                ModTransformationStateAttribute.ATTRIBUTE,
                ModTransformationStateAttribute.INITIAL,
            )
            jar.attributes.attribute(NO_NAME_MAPPING_ATTRIBUTE, false)
        }
    }

    target.extension<SourceSetContainer>().all {
        it.extension<SourceSetStaticLinkageInfo>().links.all { dependency ->
            target.extend(modConfigurationName(it.implementationConfigurationName), modConfigurationName(dependency.implementationConfigurationName))
            target.extend(modConfigurationName(it.apiConfigurationName), modConfigurationName(dependency.apiConfigurationName))
            target.extend(modConfigurationName(it.runtimeOnlyConfigurationName), modConfigurationName(dependency.runtimeOnlyConfigurationName))
            target.extend(modConfigurationName(it.compileOnlyConfigurationName), modConfigurationName(dependency.compileOnlyConfigurationName))
            target.extend(modConfigurationName(it.compileOnlyApiConfigurationName), modConfigurationName(dependency.compileOnlyApiConfigurationName))

            target.extend(it.mixinsConfigurationName, dependency.mixinsConfigurationName)
        }
    }

    target.ideaSyncHook()

    target.dependencies.components.withModule(
        "net.minecraftforge:forge",
        ForgeLexToNeoComponentMetadataRule::class.java
    ) {
        it.params(
            getGlobalCacheDirectory(target),
            VERSION_MANIFEST_URL,
            target.gradle.startParameter.isOffline,
        )
    }

    target.dependencies.components.withModule(
        "de.oceanlabs.mcp:mcp_config",
        McpConfigToNeoformComponentMetadataRule::class.java
    ) {
        it.params(
            getGlobalCacheDirectory(target),
        )
    }

    applyTargets(target, cloche)
}
