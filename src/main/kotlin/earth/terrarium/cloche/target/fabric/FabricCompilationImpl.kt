package earth.terrarium.cloche.target.fabric

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.attributes.IncludeTransformationStateAttribute
import earth.terrarium.cloche.api.attributes.ModDistribution
import earth.terrarium.cloche.api.target.compilation.FabricCompilation
import earth.terrarium.cloche.withIdeaModule
import earth.terrarium.cloche.target.FinalJarTasks
import earth.terrarium.cloche.target.TargetCompilation
import earth.terrarium.cloche.target.TargetCompilationInfo
import earth.terrarium.cloche.tasks.GenerateFabricModJson
import earth.terrarium.cloche.tasks.data.MetadataFileProvider
import kotlinx.serialization.json.JsonObject
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import net.msrandom.minecraftcodev.fabric.task.JarInJar
import org.gradle.api.Action
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.jvm.tasks.ProcessResources
import javax.inject.Inject

private fun clientClasspath(client: Provider<Boolean>, commonJar: Provider<RegularFile>) = client.zip(commonJar) { isClient, common ->
    if (isClient) {
        listOf(common)
    } else {
        emptyList()
    }
}

internal class FabricCompilationInfo(
    name: String,
    target: FabricTargetImpl,
    intermediaryMinecraftClasspath: FileCollection,
    val commonMinecraftFile: Provider<RegularFile>,
    val clientMinecraftFile: Provider<RegularFile>,
    val finalCommonJar: Provider<RegularFile>,
    mainJar: Provider<RegularFile>,
    data: Boolean,
    test: Boolean,
    internal val client: Provider<Boolean>,
) : TargetCompilationInfo<FabricTargetImpl>(
    name,
    target,
    intermediaryMinecraftClasspath,
    client.flatMap {
        if (it) {
            clientMinecraftFile
        } else {
            commonMinecraftFile
        }
    },
    if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
        clientClasspath(client, finalCommonJar)
    } else {
        clientClasspath(client, finalCommonJar).zip(mainJar, List<RegularFile>::plus)
    },
    client.map {
        if (it) {
            ModDistribution.client
        } else {
            ModDistribution.common
        }
    },
    data,
    test,
    IncludeTransformationStateAttribute.Stripped,
    JarInJar::class.java,
)

internal abstract class FabricCompilationImpl @Inject constructor(override val info: FabricCompilationInfo) :
    TargetCompilation<FabricTargetImpl>(info), FabricCompilation {
    val commonMinecraftFile get() =
        info.finalCommonJar

    val clientMinecraftFile get() =
        info.client.flatMap {
            if (it) {
                setupFiles.libraryArtifact
            } else {
                null
            }
        }

    internal val generateModJson = project.tasks.register<GenerateFabricModJson>(
        lowerCamelCaseGradleName("generate", target.featureName, featureName, "modJson"),
    ) {
        loaderDependencyVersion.set(target.loaderVersion.map {
            it.substringBeforeLast('.')
        })

        output.set(metadataDirectory.map {
            it.file(MinecraftCodevFabricPlugin.MOD_JSON)
        })

        metadata.set(target.metadata)

        mixinConfigs.from(mixins)
    }

    init {
        if (!info.name.startsWith(ClochePlugin.CLIENT_COMPILATION_NAME)) {
            project.tasks.named<ProcessResources>(sourceSet.processResourcesTaskName) {
                from(metadataDirectory)

                dependsOn(generateModJson)
            }

            project.withIdeaModule(sourceSet) {
                it.resourceDirs.add(metadataDirectory.get().asFile)
            }
        }
    }

    override fun withMetadataJson(action: Action<MetadataFileProvider<JsonObject>>) = generateModJson.configure {
        withJson(action)
    }
}
