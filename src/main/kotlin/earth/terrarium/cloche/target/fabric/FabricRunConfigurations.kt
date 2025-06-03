package earth.terrarium.cloche.target.fabric

import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.run.RunConfigurations
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.compilation.Compilation
import earth.terrarium.cloche.ideaModule
import earth.terrarium.cloche.target.TargetCompilation
import earth.terrarium.cloche.target.lazyConfigurable
import earth.terrarium.cloche.target.modOutputs
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
import org.gradle.language.jvm.tasks.ProcessResources
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
            it.server {
                it.modOutputs.from(project.modOutputs(target.main))
                it.writeRemapClasspathTask.set(target.writeRemapClasspathTask)
            }
        }
            .sourceSet(target.sourceSet)
    }

    override val client = project.lazyConfigurable {
        create(ClochePlugin.CLIENT_COMPILATION_NAME) {
            it.client {
                it.modOutputs.from(project.modOutputs(target.client.value.map<TargetCompilation> { it }.orElse(target.main)))
                it.writeRemapClasspathTask.set(target.writeRemapClasspathTask)

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
        val data = create(ClochePlugin.DATA_COMPILATION_NAME) {
            it.data {
                it.modOutputs.from(project.modOutputs(target.data.value))
                it.writeRemapClasspathTask.set(target.writeRemapClasspathTask)

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

        project.tasks.named(target.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
            it.from(target.datagenDirectory)
        }

        target.test.onConfigured {
            project.tasks.named(it.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
                it.from(target.datagenDirectory)
            }
        }

        // afterEvaluate needed because idea APIs are not lazy
        project.afterEvaluate {
            project.ideaModule(target.sourceSet) {
                it.resourceDirs.add(target.datagenDirectory.get().asFile)
            }

            target.test.onConfigured {
                project.ideaModule(it.sourceSet) {
                    it.resourceDirs.add(target.datagenDirectory.get().asFile)
                }
            }
        }

        server.onConfigured {
            it.dependsOn(data)
        }

        test.onConfigured {
            it.dependsOn(data)
        }

        data
    }

    override val clientData: LazyConfigurable<MinecraftRunConfiguration> = project.lazyConfigurable {
        val clientData = create(ClochePlugin.CLIENT_COMPILATION_NAME, ClochePlugin.DATA_COMPILATION_NAME) {
            it.clientData {
                val compilation = target.client.value.flatMap { it.data.value }.orElse(target.data.value)

                it.modOutputs.from(project.modOutputs(compilation))
                it.writeRemapClasspathTask.set(target.writeRemapClasspathTask)

                it.modId.set(project.extension<ClocheExtension>().metadata.modId)
                it.minecraftVersion.set(target.minecraftVersion)
                it.outputDirectory.set(target.datagenClientDirectory)
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

        target.client.onConfigured {
            project.tasks.named(it.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
                it.from(target.datagenDirectory)
                it.from(target.datagenClientDirectory)
            }

            it.test.onConfigured {
                project.tasks.named(it.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
                    it.from(target.datagenDirectory)
                    it.from(target.datagenClientDirectory)
                }
            }

            // afterEvaluate needed because idea APIs are not lazy
            project.afterEvaluate { _ ->
                project.ideaModule(it.sourceSet) {
                    it.resourceDirs.add(target.datagenDirectory.get().asFile)
                    it.resourceDirs.add(target.datagenClientDirectory.get().asFile)
                }

                it.test.onConfigured {
                    project.ideaModule(it.sourceSet) {
                        it.resourceDirs.add(target.datagenDirectory.get().asFile)
                        it.resourceDirs.add(target.datagenClientDirectory.get().asFile)
                    }
                }
            }
        }

        target.onClientIncluded {
            project.tasks.named(target.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
                it.from(target.datagenClientDirectory)
            }

            target.test.onConfigured {
                project.tasks.named(it.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
                    it.from(target.datagenClientDirectory)
                }
            }

            // afterEvaluate needed because idea APIs are not lazy
            project.afterEvaluate {
                project.ideaModule(target.sourceSet) {
                    it.resourceDirs.add(target.datagenClientDirectory.get().asFile)
                }

                target.test.onConfigured {
                    project.ideaModule(it.sourceSet) {
                        it.resourceDirs.add(target.datagenClientDirectory.get().asFile)
                    }
                }
            }
        }

        data.onConfigured { data ->
            clientData.dependsOn(data)

            client.onConfigured {
                it.dependsOn(data)
            }

            clientTest.onConfigured {
                it.dependsOn(data)
            }
        }

        client.onConfigured {
            it.dependsOn(clientData)
        }

        clientTest.onConfigured {
            it.dependsOn(clientData)
        }

        clientData
    }

    override val test = project.lazyConfigurable {
        create(SourceSet.TEST_SOURCE_SET_NAME) {
            it.gameTestServer {
                it.modOutputs.from(project.modOutputs(target.test.value))
                it.writeRemapClasspathTask.set(target.writeRemapClasspathTask)
            }
        }
            .sourceSet(target.test.value.map(Compilation::sourceSet))
    }

    override val clientTest = project.lazyConfigurable {
        create(ClochePlugin.CLIENT_COMPILATION_NAME, SourceSet.TEST_SOURCE_SET_NAME) {
            it.gameTestClient {
                val compilation = target.client.value.flatMap { it.test.value }.orElse(target.test.value)

                it.modOutputs.from(project.modOutputs(compilation))
                it.writeRemapClasspathTask.set(target.writeRemapClasspathTask)

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
