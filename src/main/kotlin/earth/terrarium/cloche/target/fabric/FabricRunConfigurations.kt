package earth.terrarium.cloche.target.fabric

import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.run.RunConfigurations
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.compilation.Compilation
import earth.terrarium.cloche.target.lazyConfigurable
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.fabric.runs.FabricRunsDefaultsContainer
import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import net.msrandom.minecraftcodev.runs.RunsContainer
import net.msrandom.minecraftcodev.runs.downloadAssetsTaskName
import net.msrandom.minecraftcodev.runs.extractNativesTaskName
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.gradle.api.Action
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

internal abstract class FabricRunConfigurations @Inject constructor(val target: FabricTargetImpl) : RunConfigurations {
    fun create(vararg names: String, action: Action<FabricRunsDefaultsContainer>): MinecraftRunConfiguration {
        val run = project.extension<RunsContainer>().create(listOf(target.name, *names).joinToString(TARGET_NAME_PATH_SEPARATOR.toString()))

        run.defaults {
            action.execute(it.extension<FabricRunsDefaultsContainer>())
        }

        return run
    }

    override val server = project.lazyConfigurable {
        create(ClochePlugin.SERVER_RUNNABLE_NAME) {
            it.server()
        }
            .sourceSet(target.sourceSet)
    }

    override val client = project.lazyConfigurable {
        create(ClochePlugin.CLIENT_COMPILATION_NAME) {
            it.client {
                it.minecraftVersion.set(target.minecraftVersion)
                it.extractNativesTask.set(
                    project.tasks.named(
                        target.sourceSet.extractNativesTaskName,
                        ExtractNatives::class.java
                    )
                )
                it.downloadAssetsTask.set(
                    project.tasks.named(
                        target.sourceSet.downloadAssetsTaskName,
                        DownloadAssets::class.java
                    )
                )
            }
        }
            .sourceSet(target.client.value.map(Compilation::sourceSet).orElse(target.sourceSet))
    }

    override val data = project.lazyConfigurable {
        create(ClochePlugin.DATA_COMPILATION_NAME) {
            it.data {
                it.modId.set(project.extension<ClocheExtension>().metadata.modId)
                it.minecraftVersion.set(target.minecraftVersion)
                it.outputDirectory.set(target.datagenDirectory)
                it.downloadAssetsTask.set(
                    project.tasks.named(
                        target.sourceSet.downloadAssetsTaskName,
                        DownloadAssets::class.java
                    )
                )
            }
        }
            .sourceSet(target.data.value.map(Compilation::sourceSet))
    }

    override val clientData: LazyConfigurable<MinecraftRunConfiguration> = project.lazyConfigurable {
        create(ClochePlugin.CLIENT_COMPILATION_NAME, ClochePlugin.DATA_COMPILATION_NAME) {
            it.clientData {
                it.modId.set(project.extension<ClocheExtension>().metadata.modId)
                it.minecraftVersion.set(target.minecraftVersion)
                it.outputDirectory.set(target.datagenDirectory)
                it.downloadAssetsTask.set(
                    project.tasks.named(
                        target.sourceSet.downloadAssetsTaskName,
                        DownloadAssets::class.java
                    )
                )
            }
        }.sourceSet(
            target.client.value.flatMap { it.data.value }.orElse(target.data.value).map(Compilation::sourceSet)
        )
    }

    override val test = project.lazyConfigurable {
        create(SourceSet.TEST_SOURCE_SET_NAME) {
            it.gameTestServer()
        }
            .sourceSet(target.test.value.map(Compilation::sourceSet))
    }

    override val clientTest = project.lazyConfigurable {
        create(ClochePlugin.CLIENT_COMPILATION_NAME, SourceSet.TEST_SOURCE_SET_NAME) {
            it.gameTestClient {
                it.minecraftVersion.set(target.minecraftVersion)
                it.extractNativesTask.set(
                    project.tasks.named(
                        target.sourceSet.extractNativesTaskName,
                        ExtractNatives::class.java
                    )
                )
                it.downloadAssetsTask.set(
                    project.tasks.named(
                        target.sourceSet.downloadAssetsTaskName,
                        DownloadAssets::class.java
                    )
                )
            }
        }
            .sourceSet(
                target.client.value.flatMap { it.test.value }.orElse(target.test.value).map(Compilation::sourceSet)
            )
    }
}
