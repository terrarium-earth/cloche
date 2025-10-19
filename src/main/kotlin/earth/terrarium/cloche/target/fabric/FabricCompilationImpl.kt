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
import org.gradle.language.jvm.tasks.ProcessResources
import javax.inject.Inject

internal abstract class FabricCompilationImpl @Inject constructor(info: TargetCompilationInfo<FabricTargetImpl>) :
    TargetCompilation<FabricTargetImpl>(info), FabricCompilation {
    internal val generateModJson = project.tasks.register(
        lowerCamelCaseGradleName("generate", target.featureName, featureName, "ModJson"),
        GenerateFabricModJson::class.java
    ) {
        it.loaderDependencyVersion.set(target.loaderVersion.map {
            it.substringBeforeLast('.')
        })

        it.output.set(metadataDirectory.map {
            it.file(MinecraftCodevFabricPlugin.MOD_JSON)
        })

        it.targetMetadata.set(target.metadata)

        it.mixinConfigs.from(mixins)

        if (info.name == SourceSet.MAIN_SOURCE_SET_NAME) {
            // TODO How do we apply this for client:data and client:test as well?
            info.target.client.onConfigured { client ->
                it.clientMixinConfigs.from(client.mixins)
            }
        }
    }

    init {
        project.tasks.named(sourceSet.processResourcesTaskName, ProcessResources::class.java) {
            it.from(metadataDirectory)

            it.dependsOn(generateModJson)
        }

        project.ideaModule(sourceSet) {
            it.resourceDirs.add(metadataDirectory.get().asFile)
        }
    }

    override fun withMetadataJson(action: Action<MetadataFileProvider<JsonObject>>) = generateModJson.configure {
        it.withJson(action)
    }
}
