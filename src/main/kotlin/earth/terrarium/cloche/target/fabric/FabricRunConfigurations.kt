package earth.terrarium.cloche.target.fabric

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.run.RunConfigurations
import earth.terrarium.cloche.api.run.commonDescription
import earth.terrarium.cloche.api.run.quotedDescription
import earth.terrarium.cloche.api.run.withCompilation
import earth.terrarium.cloche.api.target.FabricTarget
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.compilation.CommonSecondarySourceSets
import earth.terrarium.cloche.api.target.targetName
import earth.terrarium.cloche.util.withIdeaModule
import earth.terrarium.cloche.modId
import earth.terrarium.cloche.target.compilation.TargetCompilation
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
import org.gradle.kotlin.dsl.named
import org.gradle.language.jvm.tasks.ProcessResources
import javax.inject.Inject

internal abstract class FabricRunConfigurations @Inject constructor(val target: FabricTargetImpl) : RunConfigurations {
    fun create(name: String, action: Action<FabricRunsDefaultsContainer>): MinecraftRunConfiguration {
        val run = project.extension<RunsContainer>()
            .create(listOfNotNull(target.targetName, name).joinToString(TARGET_NAME_PATH_SEPARATOR.toString()))

        run.defaults {
            action.execute(extension<FabricRunsDefaultsContainer>())
        }

        return run
    }

    private fun clientDescription(name: String) = if (target.client.isConfiguredValue) {
        quotedDescription(name)
    } else if (target.client.value.isPresent) {
        "'${FabricTarget::client.name} { ${commonDescription(name)} }'"
    } else {
        "'${FabricTarget::client.name} { ${commonDescription(name)} }' or ${quotedDescription(name)}"
    }

    override val server = project.lazyConfigurable {
        create(ClochePlugin.SERVER_RUNNABLE_NAME) {
            server {
                modOutputs.set(project.modOutputs(target.main))
                writeRemapClasspathTask.set(target.writeRemapClasspathTask)
            }
        }.withCompilation(target.main)
    }

    override val client = project.lazyConfigurable {
        val compilation = target.client.value.map<TargetCompilation<*>> { it }.orElse(target.main)

        create(ClochePlugin.CLIENT_COMPILATION_NAME) {
            client {
                modOutputs.set(project.modOutputs(compilation))
                writeRemapClasspathTask.set(target.writeRemapClasspathTask)

                minecraftVersion.set(target.minecraftVersion)
                extractNativesTask.set(
                    project.tasks.named<ExtractNatives>(target.sourceSet.extractNativesTaskName)
                )
                downloadAssetsTask.set(
                    project.tasks.named<DownloadAssets>(target.sourceSet.downloadAssetsTaskName)
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
            data {
                modOutputs.set(project.modOutputs(compilation))
                writeRemapClasspathTask.set(target.writeRemapClasspathTask)

                modId.set(project.modId)
                minecraftVersion.set(target.minecraftVersion)
                outputDirectory.set(project.provider { target.datagenDirectory.get() })
                downloadAssetsTask.set(
                    project.tasks.named<DownloadAssets>(target.sourceSet.downloadAssetsTaskName)
                )
            }
        }.withCompilation(target, compilation) { quotedDescription(CommonSecondarySourceSets::data.name) }

        data.runTask.configure {
            outputs.cacheIf { true }
            outputs.dir(target.datagenDirectory)
        }

        target.datagenDirectoryBuildDependencies.builtBy(data.runTask)

        project.withIdeaModule(target.sourceSet) {
            it.resourceDirs.add(target.datagenDirectory.get().asFile)
        }

        target.test.onConfigured {
            project.withIdeaModule(it.sourceSet) {
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

        val clientData = create(ClochePlugin.CLIENT_DATA_COMPILATION_NAME) {
            clientData {
                modOutputs.set(project.modOutputs(compilation))
                writeRemapClasspathTask.set(target.writeRemapClasspathTask)

                modId.set(project.modId)
                minecraftVersion.set(target.minecraftVersion)
                outputDirectory.set(project.provider { target.datagenClientDirectory.get() })
                downloadAssetsTask.set(
                    project.tasks.named<DownloadAssets>(target.sourceSet.downloadAssetsTaskName)
                )
            }
        }.withCompilation(target, compilation) {
            clientDescription(CommonSecondarySourceSets::data.name)
        }

        clientData.runTask.configure {
            outputs.cacheIf { true }
            outputs.dir(target.datagenClientDirectory)
        }

        target.datagenClientDirectoryBuildDependencies.builtBy(clientData.runTask)

        target.client.onConfigured {
            project.withIdeaModule(it.sourceSet) {
                it.resourceDirs.add(target.datagenDirectory.get().asFile)
                it.resourceDirs.add(target.datagenClientDirectory.get().asFile)
            }

            it.test.onConfigured {
                project.withIdeaModule(it.sourceSet) {
                    it.resourceDirs.add(target.datagenDirectory.get().asFile)
                    it.resourceDirs.add(target.datagenClientDirectory.get().asFile)
                }
            }
        }

        target.onClientIncluded {
            project.withIdeaModule(target.sourceSet) {
                it.resourceDirs.add(target.datagenClientDirectory.get().asFile)
            }

            target.test.onConfigured {
                project.withIdeaModule(it.sourceSet) {
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
            gameTestServer {
                modOutputs.set(project.modOutputs(compilation))
                writeRemapClasspathTask.set(target.writeRemapClasspathTask)
            }
        }.withCompilation(target, compilation) {
            quotedDescription(CommonSecondarySourceSets::test.name)
        }
    }

    override val clientTest = project.lazyConfigurable {
        val compilation = target.client.value.flatMap { it.test.value }.orElse(target.test.value)

        create(ClochePlugin.CLIENT_TEST_COMPILATION_NAME) {
            gameTestClient {
                modOutputs.set(project.modOutputs(compilation))
                writeRemapClasspathTask.set(target.writeRemapClasspathTask)

                minecraftVersion.set(target.minecraftVersion)
                extractNativesTask.set(
                    project.tasks.named<ExtractNatives>(target.sourceSet.extractNativesTaskName)
                )
                downloadAssetsTask.set(
                    project.tasks.named<DownloadAssets>(target.sourceSet.downloadAssetsTaskName)
                )
            }
        }.withCompilation(target, compilation) {
            clientDescription(CommonSecondarySourceSets::test.name)
        }
    }
}
