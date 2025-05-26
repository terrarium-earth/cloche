package earth.terrarium.cloche.target.forge

import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.run.RunConfigurations
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.compilation.Compilation
import earth.terrarium.cloche.ideaModule
import earth.terrarium.cloche.target.LazyConfigurableInternal
import earth.terrarium.cloche.target.lazyConfigurable
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.forge.runs.ForgeRunConfigurationData
import net.msrandom.minecraftcodev.forge.runs.ForgeRunsDefaultsContainer
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import net.msrandom.minecraftcodev.runs.MinecraftRunConfiguration
import net.msrandom.minecraftcodev.runs.RunsContainer
import net.msrandom.minecraftcodev.runs.downloadAssetsTaskName
import net.msrandom.minecraftcodev.runs.extractNativesTaskName
import net.msrandom.minecraftcodev.runs.task.DownloadAssets
import net.msrandom.minecraftcodev.runs.task.ExtractNatives
import org.gradle.api.Action
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.language.jvm.tasks.ProcessResources
import javax.inject.Inject

internal abstract class ForgeRunConfigurations<T : ForgeLikeTargetImpl> @Inject constructor(val target: T) : RunConfigurations {
    fun create(vararg names: String, action: Action<ForgeRunsDefaultsContainer>): MinecraftRunConfiguration {
        val run = project.extension<RunsContainer>().create(listOf(target.name, *names).joinToString(TARGET_NAME_PATH_SEPARATOR.toString()))

        applyDefault(run)
        run.defaults {
            action.execute(it.extension<ForgeRunsDefaultsContainer>())
        }

        return run
    }

    protected open fun applyDefault(run: MinecraftRunConfiguration) {}

    protected open fun configureData(data: ForgeRunConfigurationData, sourceSet: SourceSet) {
        data.mixinConfigs.from(project.configurations.named(sourceSet.mixinsConfigurationName))
    }

    protected open fun configureData(data: ForgeRunConfigurationData, sourceSet: Provider<SourceSet>) {
        data.mixinConfigs.from(sourceSet.flatMap { project.configurations.named(it.mixinsConfigurationName) })
    }

    private fun ForgeRunConfigurationData.configure(sourceSet: SourceSet) {
        configureData(this, sourceSet)
    }

    private fun ForgeRunConfigurationData.configure(sourceSet: Provider<SourceSet>) {
        configureData(this, sourceSet)
    }

    override val server = project.lazyConfigurable {
        create(ClochePlugin.SERVER_RUNNABLE_NAME) {
            it.server {
                it.modId.set(project.extension<ClocheExtension>().metadata.modId)
                it.minecraftVersion.set(target.minecraftVersion)
                it.patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                it.generateLegacyClasspathTask.set(target.generateLegacyClasspath)

                it.configure(target.sourceSet)
            }
        }
            .sourceSet(target.sourceSet)
    }

    override val client = project.lazyConfigurable {
        create(ClochePlugin.CLIENT_COMPILATION_NAME) {
            it.client {
                it.modId.set(project.extension<ClocheExtension>().metadata.modId)
                it.minecraftVersion.set(target.minecraftVersion)
                it.patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                it.extractNativesTask.set(project.tasks.named(target.sourceSet.extractNativesTaskName, ExtractNatives::class.java))
                it.downloadAssetsTask.set(project.tasks.named(target.sourceSet.downloadAssetsTaskName, DownloadAssets::class.java))
                it.generateLegacyClasspathTask.set(target.generateLegacyClasspath)

                it.configure(target.sourceSet)
            }
        }
            .sourceSet(target.sourceSet)
    }

    override val data = project.lazyConfigurable {
        val data = create(ClochePlugin.DATA_COMPILATION_NAME) {
            it.data {
                it.modId.set(project.extension<ClocheExtension>().metadata.modId)
                it.minecraftVersion.set(target.minecraftVersion)
                it.patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                it.additionalIncludedSourceSets.add(target.sourceSet)
                it.outputDirectory.set(target.datagenDirectory)
                it.downloadAssetsTask.set(
                    project.tasks.named(
                        target.sourceSet.downloadAssetsTaskName,
                        DownloadAssets::class.java
                    )
                )
                it.generateLegacyClasspathTask.set(target.generateLegacyDataClasspath)

                it.configure(target.data.value.map { it.sourceSet })
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

        client.onConfigured {
            it.dependsOn(data)
        }

        clientTest.onConfigured {
            it.dependsOn(data)
        }

        data
    }

    override val clientData: LazyConfigurable<MinecraftRunConfiguration> = project.lazyConfigurable {
        val clientData = create(ClochePlugin.CLIENT_COMPILATION_NAME, ClochePlugin.DATA_COMPILATION_NAME) {
            it.clientData {
                it.modId.set(project.extension<ClocheExtension>().metadata.modId)
                it.minecraftVersion.set(target.minecraftVersion)
                it.patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                it.outputDirectory.set(target.datagenClientDirectory)
                it.commonOutputDirectory.set(target.datagenDirectory)
                it.downloadAssetsTask.set(
                    project.tasks.named(
                        target.sourceSet.downloadAssetsTaskName,
                        DownloadAssets::class.java
                    )
                )
                it.generateLegacyClasspathTask.set(target.generateLegacyDataClasspath)

                it.additionalIncludedSourceSets.add(target.sourceSet)
                it.additionalIncludedSourceSets.add(target.data.value.map(Compilation::sourceSet))

                it.configure(target.data.value.map { it.sourceSet })
            }
        }
            .sourceSet(target.data.value.map(Compilation::sourceSet))

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
                it.modId.set(project.extension<ClocheExtension>().metadata.modId)
                it.minecraftVersion.set(target.minecraftVersion)
                it.patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                it.additionalIncludedSourceSets.add(target.sourceSet)
                it.generateLegacyClasspathTask.set(target.generateLegacyTestClasspath)

                it.configure(target.test.value.map { it.sourceSet })
            }
        }
            .sourceSet(target.test.value.map(Compilation::sourceSet))
    }

    override val clientTest: LazyConfigurableInternal<MinecraftRunConfiguration> = project.lazyConfigurable {
        TODO()
    }
}
