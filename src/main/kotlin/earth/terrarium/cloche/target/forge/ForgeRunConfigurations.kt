package earth.terrarium.cloche.target.forge

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.run.RunConfigurations
import earth.terrarium.cloche.api.run.quotedDescription
import earth.terrarium.cloche.api.run.withCompilation
import earth.terrarium.cloche.api.target.ForgeTarget
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.targetName
import earth.terrarium.cloche.ideaModule
import earth.terrarium.cloche.modId
import earth.terrarium.cloche.target.LazyConfigurableInternal
import earth.terrarium.cloche.target.lazyConfigurable
import earth.terrarium.cloche.target.modOutputs
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.forge.patchesConfigurationName
import net.msrandom.minecraftcodev.forge.runs.ForgeRunConfigurationData
import net.msrandom.minecraftcodev.forge.runs.ForgeRunsDefaultsContainer
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
        val run = project.extension<RunsContainer>().create(listOfNotNull(target.targetName, *names).joinToString(TARGET_NAME_PATH_SEPARATOR.toString()))

        applyDefault(run)
        run.defaults {
            action.execute(it.extension<ForgeRunsDefaultsContainer>())
        }

        return run
    }

    protected open fun applyDefault(run: MinecraftRunConfiguration) {}
    protected open fun configureData(data: ForgeRunConfigurationData, compilation: ForgeCompilationImpl) {}
    protected open fun configureData(data: ForgeRunConfigurationData, compilation: Provider<ForgeCompilationImpl>) {}

    private fun ForgeRunConfigurationData.configure(sourceSet: ForgeCompilationImpl) {
        configureData(this, sourceSet)
    }

    private fun ForgeRunConfigurationData.configure(sourceSet: Provider<ForgeCompilationImpl>) {
        configureData(this, sourceSet)
    }

    override val server = project.lazyConfigurable {
        create(ClochePlugin.SERVER_RUNNABLE_NAME) {
            it.server {
                it.modOutputs.set(project.modOutputs(target.main))

                it.minecraftVersion.set(target.minecraftVersion)
                it.patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                it.writeLegacyClasspathTask.set(target.main.writeLegacyClasspath)

                it.configure(target.main)
            }
        }.withCompilation(target.main)
    }

    override val client = project.lazyConfigurable {
        create(ClochePlugin.CLIENT_COMPILATION_NAME) {
            it.client {
                it.modOutputs.set(project.modOutputs(target.main))

                it.minecraftVersion.set(target.minecraftVersion)
                it.patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                it.extractNativesTask.set(project.tasks.named(target.sourceSet.extractNativesTaskName, ExtractNatives::class.java))
                it.downloadAssetsTask.set(project.tasks.named(target.sourceSet.downloadAssetsTaskName, DownloadAssets::class.java))
                it.writeLegacyClasspathTask.set(target.main.writeLegacyClasspath)

                it.configure(target.main)
            }
        }.withCompilation(target.main)
    }

    override val data = project.lazyConfigurable {
        val compilation = target.data.value

        val data = create(ClochePlugin.DATA_COMPILATION_NAME) {
            it.data {
                it.modOutputs.set(project.modOutputs(compilation))

                it.modId.set(project.modId)
                it.minecraftVersion.set(target.minecraftVersion)
                it.patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                it.mainResources.set(target.sourceSet.output.resourcesDir)
                it.outputDirectory.set(target.datagenDirectory)
                it.downloadAssetsTask.set(
                    project.tasks.named(
                        target.sourceSet.downloadAssetsTaskName,
                        DownloadAssets::class.java
                    )
                )
                it.writeLegacyClasspathTask.set(compilation.flatMap(ForgeCompilationImpl::writeLegacyClasspath))

                it.configure(compilation)
            }
        }.withCompilation(target, compilation) { quotedDescription(ForgeTarget::data.name) }

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

        client.onConfigured {
            it.dependsOn(data)
        }

        clientTest.onConfigured {
            it.dependsOn(data)
        }

        data
    }

    override val clientData: LazyConfigurable<MinecraftRunConfiguration> = project.lazyConfigurable {
        val compilation = target.data.value

        val clientData = create(ClochePlugin.CLIENT_COMPILATION_NAME, ClochePlugin.DATA_COMPILATION_NAME) {
            it.clientData {
                it.modOutputs.set(project.modOutputs(compilation))

                it.modId.set(project.modId)
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
                it.writeLegacyClasspathTask.set(compilation.flatMap(ForgeCompilationImpl::writeLegacyClasspath))

                it.mainResources.set(target.sourceSet.output.resourcesDir)

                it.configure(compilation)
            }
        }.withCompilation(target, compilation) { quotedDescription(ForgeTarget::data.name) }

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
                it.modOutputs.set(project.modOutputs(compilation))

                it.minecraftVersion.set(target.minecraftVersion)
                it.patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                it.writeLegacyClasspathTask.set(compilation.flatMap(ForgeCompilationImpl::writeLegacyClasspath))

                it.configure(compilation)
            }
        }.withCompilation(target, compilation) { quotedDescription(ForgeTarget::test.name) }
    }

    override val clientTest: LazyConfigurableInternal<MinecraftRunConfiguration> = project.lazyConfigurable {
        TODO()
    }
}
