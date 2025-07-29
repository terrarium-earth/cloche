package earth.terrarium.cloche.target.fabric

import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.run.RunConfigurations
import earth.terrarium.cloche.api.run.commonDescription
import earth.terrarium.cloche.api.run.quotedDescription
import earth.terrarium.cloche.api.run.withCompilation
import earth.terrarium.cloche.api.target.FabricTarget
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.compilation.CommonSecondarySourceSets
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

    private fun clientDescription(name: String) = if (target.hasIncludedClient) {
        quotedDescription(name)
    } else if (target.client.value.isPresent) {
        "'${FabricTarget::client.name} { ${commonDescription(name)} }'"
    } else {
        "'${FabricTarget::client.name} { ${commonDescription(name)} }' or ${quotedDescription(name)}"
    }

    override val server = project.lazyConfigurable {
        create(ClochePlugin.SERVER_RUNNABLE_NAME) {
            it.server {
                it.modOutputs.from(project.modOutputs(target.main))
                it.writeRemapClasspathTask.set(target.writeRemapClasspathTask)
            }
        }.withCompilation(target.main)
    }

    override val client = project.lazyConfigurable {
        val compilation = target.client.value.map<TargetCompilation> { it }.orElse(target.main)

        create(ClochePlugin.CLIENT_COMPILATION_NAME) {
            it.client {
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
        }.withCompilation(target, compilation) {
            // TODO This error description is currently unused, as the fallback to target.main will *always* succeed
            //  Whether this should be changed is still up for debate. Should a target with no configured client still be runnable as client?
            //  it makes sense for server mods and datapacks to still be tested on the client, so the best option is probably removing the checking here and always allowing client runs
            ""
        }
    }

    override val data = project.lazyConfigurable {
        val compilation = target.data.value

        val data = create(ClochePlugin.DATA_COMPILATION_NAME) {
            it.data {
                it.modOutputs.from(project.modOutputs(compilation))
                it.writeRemapClasspathTask.set(target.writeRemapClasspathTask)

                it.modId.set(target.metadata.modId)
                it.minecraftVersion.set(target.minecraftVersion)
                it.outputDirectory.set(target.datagenDirectory)
                it.downloadAssetsTask.set(
                    project.tasks.named(
                        target.sourceSet.downloadAssetsTaskName,
                        DownloadAssets::class.java
                    )
                )
            }
        }.withCompilation(target, compilation) { quotedDescription(CommonSecondarySourceSets::data.name) }

        project.tasks.named(target.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
            it.from(target.datagenDirectory)
            it.mustRunAfter(data.runTask)
        }

        project.tasks.named(target.sourceSet.jarTaskName) {
            it.dependsOn(data.runTask)
        }

        target.test.onConfigured {
            project.tasks.named(it.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
                it.from(target.datagenDirectory)
                it.mustRunAfter(data.runTask)
            }
        }

        project.ideaModule(target.sourceSet) {
            it.resourceDirs.add(target.datagenDirectory.get().asFile)
        }

        target.test.onConfigured {
            project.ideaModule(it.sourceSet) {
                it.resourceDirs.add(target.datagenDirectory.get().asFile)
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
        val compilation = target.client.value.flatMap { it.data.value }.orElse(target.data.value)

        val clientData = create(ClochePlugin.CLIENT_COMPILATION_NAME, ClochePlugin.DATA_COMPILATION_NAME) {
            it.clientData {
                it.modOutputs.from(project.modOutputs(compilation))
                it.writeRemapClasspathTask.set(target.writeRemapClasspathTask)

                it.modId.set(target.metadata.modId)
                it.minecraftVersion.set(target.minecraftVersion)
                it.outputDirectory.set(target.datagenClientDirectory)
                it.downloadAssetsTask.set(
                    project.tasks.named(
                        target.sourceSet.downloadAssetsTaskName,
                        DownloadAssets::class.java
                    )
                )
            }
        }.withCompilation(target, compilation) {
            clientDescription(CommonSecondarySourceSets::data.name)
        }

        target.client.onConfigured {
            project.tasks.named(it.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
                it.from(target.datagenDirectory)
                it.from(target.datagenClientDirectory)
                it.mustRunAfter(clientData.runTask)
            }

            project.tasks.named(it.sourceSet.jarTaskName) {
                it.dependsOn(clientData.runTask)
            }

            it.test.onConfigured {
                project.tasks.named(it.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
                    it.from(target.datagenDirectory)
                    it.from(target.datagenClientDirectory)
                    it.mustRunAfter(clientData.runTask)
                }
            }

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

        target.onClientIncluded {
            project.tasks.named(target.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
                it.from(target.datagenClientDirectory)
                it.mustRunAfter(clientData.runTask)
            }

            project.tasks.named(target.sourceSet.jarTaskName) {
                it.dependsOn(clientData.runTask)
            }

            target.test.onConfigured {
                project.tasks.named(it.sourceSet.processResourcesTaskName, ProcessResources::class.java) {
                    it.from(target.datagenClientDirectory)
                    it.mustRunAfter(clientData.runTask)
                }
            }

            project.ideaModule(target.sourceSet) {
                it.resourceDirs.add(target.datagenClientDirectory.get().asFile)
            }

            target.test.onConfigured {
                project.ideaModule(it.sourceSet) {
                    it.resourceDirs.add(target.datagenClientDirectory.get().asFile)
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
        val compilation = target.test.value

        create(SourceSet.TEST_SOURCE_SET_NAME) {
            it.gameTestServer {
                it.modOutputs.from(project.modOutputs(compilation))
                it.writeRemapClasspathTask.set(target.writeRemapClasspathTask)
            }
        }.withCompilation(target, compilation) {
            quotedDescription(CommonSecondarySourceSets::test.name)
        }
    }

    override val clientTest = project.lazyConfigurable {
        val compilation = target.client.value.flatMap { it.test.value }.orElse(target.test.value)
        create(ClochePlugin.CLIENT_COMPILATION_NAME, SourceSet.TEST_SOURCE_SET_NAME) {
            it.gameTestClient {
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
        }.withCompilation(target, compilation) {
            clientDescription(CommonSecondarySourceSets::test.name)
        }
    }
}
