package earth.terrarium.cloche.target.fabric

import earth.terrarium.cloche.api.target.compilation.FabricCompilation
import earth.terrarium.cloche.ideaModule
import earth.terrarium.cloche.target.TargetCompilation
import earth.terrarium.cloche.target.TargetCompilationInfo
import earth.terrarium.cloche.tasks.GenerateFabricModJson
import earth.terrarium.cloche.tasks.data.MetadataFileProvider
import kotlinx.serialization.json.JsonObject
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import org.gradle.api.Action
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.jvm.tasks.ProcessResources
import javax.inject.Inject

internal abstract class FabricCompilationImpl @Inject constructor(info: TargetCompilationInfo<FabricTargetImpl>) :
    TargetCompilation<FabricTargetImpl>(info), FabricCompilation {
    internal val generateModJson = project.tasks.register<GenerateFabricModJson>(
        lowerCamelCaseGradleName("generate", target.featureName, featureName, "modJson"),
    ) {
        loaderDependencyVersion.set(target.loaderVersion.map {
            it.substringBeforeLast('.')
        })

        output.set(metadataDirectory.map {
            it.file(MinecraftCodevFabricPlugin.MOD_JSON)
        })

        targetMetadata.set(target.metadata)

        mixinConfigs.from(mixins)

        if (info.name == SourceSet.MAIN_SOURCE_SET_NAME) {
            // TODO How do we apply this for client:data and client:test as well?
            info.target.client.onConfigured { client ->
                clientMixinConfigs.from(client.mixins)
            }
        }
    }

    init {
        project.tasks.named<ProcessResources>(sourceSet.processResourcesTaskName) {
            from(metadataDirectory)

            dependsOn(generateModJson)
        }

        project.ideaModule(sourceSet) {
            it.resourceDirs.add(metadataDirectory.get().asFile)
        }
    }

    override fun withMetadataJson(action: Action<MetadataFileProvider<JsonObject>>) = generateModJson.configure {
        withJson(action)
    }
}
