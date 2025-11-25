package earth.terrarium.cloche.target.forge

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.run.RunConfigurations
import earth.terrarium.cloche.api.run.quotedDescription
import earth.terrarium.cloche.api.run.withCompilation
import earth.terrarium.cloche.api.target.ForgeTarget
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.targetName
import earth.terrarium.cloche.withIdeaModule
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
import org.gradle.kotlin.dsl.named
import org.gradle.language.jvm.tasks.ProcessResources
import javax.inject.Inject

internal abstract class ForgeRunConfigurations<T : ForgeLikeTargetImpl> @Inject constructor(val target: T) : RunConfigurations {
    fun create(vararg names: String, action: Action<ForgeRunsDefaultsContainer>): MinecraftRunConfiguration {
        val run = project.extension<RunsContainer>().create(listOfNotNull(target.targetName, *names).joinToString(TARGET_NAME_PATH_SEPARATOR.toString()))

        applyDefault(run)
        run.defaults {
            action.execute(extension<ForgeRunsDefaultsContainer>())
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
            server {
                modOutputs.set(project.modOutputs(target.main))

                minecraftVersion.set(target.minecraftVersion)
                patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                writeLegacyClasspathTask.set(target.main.writeLegacyClasspath)

                configure(target.main)
            }
        }.withCompilation(target.main)
    }

    override val client = project.lazyConfigurable {
        create(ClochePlugin.CLIENT_COMPILATION_NAME) {
            client {
                modOutputs.set(project.modOutputs(target.main))

                minecraftVersion.set(target.minecraftVersion)
                patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                extractNativesTask.set(project.tasks.named<ExtractNatives>(target.sourceSet.extractNativesTaskName))
                downloadAssetsTask.set(project.tasks.named<DownloadAssets>(target.sourceSet.downloadAssetsTaskName))
                writeLegacyClasspathTask.set(target.main.writeLegacyClasspath)

                configure(target.main)
            }
        }.withCompilation(target.main)
    }

    override val data = project.lazyConfigurable {
        val compilation = target.data.value

        val data = create(ClochePlugin.DATA_COMPILATION_NAME) {
            data {
                modOutputs.set(project.modOutputs(compilation))

                modId.set(project.modId)
                minecraftVersion.set(target.minecraftVersion)
                patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                mainResources.set(target.sourceSet.output.resourcesDir)
                outputDirectory.set(target.datagenDirectory)
                downloadAssetsTask.set(
                    project.tasks.named<DownloadAssets>(target.sourceSet.downloadAssetsTaskName)
                )
                writeLegacyClasspathTask.set(compilation.flatMap(ForgeCompilationImpl::writeLegacyClasspath))

                configure(compilation)
            }
        }.withCompilation(target, compilation) { quotedDescription(ForgeTarget::data.name) }

        project.tasks.named<ProcessResources>(target.sourceSet.processResourcesTaskName) {
            from(target.datagenDirectory)
            mustRunAfter(data.runTask)
        }

        project.tasks.named(target.sourceSet.jarTaskName) {
            dependsOn(data.runTask)
        }

        target.test.onConfigured {
            project.tasks.named<ProcessResources>(it.sourceSet.processResourcesTaskName) {
                from(target.datagenDirectory)
                mustRunAfter(data.runTask)
            }
        }

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
            clientData {
                modOutputs.set(project.modOutputs(compilation))

                modId.set(project.modId)
                minecraftVersion.set(target.minecraftVersion)
                patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                outputDirectory.set(target.datagenClientDirectory)
                commonOutputDirectory.set(target.datagenDirectory)
                downloadAssetsTask.set(
                    project.tasks.named<DownloadAssets>(target.sourceSet.downloadAssetsTaskName)
                )
                writeLegacyClasspathTask.set(compilation.flatMap(ForgeCompilationImpl::writeLegacyClasspath))

                mainResources.set(target.sourceSet.output.resourcesDir)

                configure(compilation)
            }
        }.withCompilation(target, compilation) { quotedDescription(ForgeTarget::data.name) }

        project.tasks.named<ProcessResources>(target.sourceSet.processResourcesTaskName) {
            from(target.datagenClientDirectory)
            mustRunAfter(clientData.runTask)
        }

        project.tasks.named(target.sourceSet.jarTaskName) {
            dependsOn(clientData.runTask)
        }

        target.test.onConfigured {
            project.tasks.named<ProcessResources>(it.sourceSet.processResourcesTaskName) {
                from(target.datagenClientDirectory)
                mustRunAfter(clientData.runTask)
            }
        }

        project.withIdeaModule(target.sourceSet) {
            it.resourceDirs.add(target.datagenClientDirectory.get().asFile)
        }

        target.test.onConfigured {
            project.withIdeaModule(it.sourceSet) {
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
            gameTestServer {
                modOutputs.set(project.modOutputs(compilation))

                minecraftVersion.set(target.minecraftVersion)
                patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                writeLegacyClasspathTask.set(compilation.flatMap(ForgeCompilationImpl::writeLegacyClasspath))

                configure(compilation)
            }
        }.withCompilation(target, compilation) { quotedDescription(ForgeTarget::test.name) }
    }

    override val clientTest: LazyConfigurableInternal<MinecraftRunConfiguration> = project.lazyConfigurable {
        TODO()
    }
}
