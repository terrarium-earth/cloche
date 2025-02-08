package earth.terrarium.cloche.target.forge

import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.run.RunConfigurations
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.compilation.Compilation
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
import javax.inject.Inject

internal abstract class ForgeRunConfigurations @Inject constructor(val target: ForgeLikeTargetImpl) : RunConfigurations {
    fun create(vararg names: String, action: Action<ForgeRunsDefaultsContainer>): MinecraftRunConfiguration {
        val run = project.extension<RunsContainer>().create(listOf(target.name, *names).joinToString(TARGET_NAME_PATH_SEPARATOR.toString()))

        run.defaults {
            action.execute(it.extension<ForgeRunsDefaultsContainer>())
        }

        return run
    }

    private fun ForgeRunConfigurationData.mixins(sourceSet: SourceSet) {
        mixinConfigs.from(project.configurations.named(sourceSet.mixinsConfigurationName))

    }

    private fun ForgeRunConfigurationData.mixins(sourceSet: Provider<SourceSet>) {
        mixinConfigs.from(sourceSet.flatMap { project.configurations.named(it.mixinsConfigurationName) })
    }

    override val server = project.lazyConfigurable {
        create(ClochePlugin.SERVER_RUNNABLE_NAME) {
            it.server {
                it.modId.set(project.extension<ClocheExtension>().metadata.modId)
                it.minecraftVersion.set(target.minecraftVersion)
                it.patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                it.generateLegacyClasspathTask.set(target.generateLegacyClasspath)

                it.mixins(target.sourceSet)
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

                it.mixins(target.sourceSet)
            }
        }
            .sourceSet(target.sourceSet)
    }

    override val data = project.lazyConfigurable {
        create(ClochePlugin.DATA_COMPILATION_NAME) {
            it.data {
                it.modId.set(project.extension<ClocheExtension>().metadata.modId)
                it.minecraftVersion.set(target.minecraftVersion)
                it.patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                it.additionalIncludedSourceSets.add(target.sourceSet)
                it.outputDirectory.set(target.datagenDirectory)
                it.downloadAssetsTask.set(project.tasks.named(target.sourceSet.downloadAssetsTaskName, DownloadAssets::class.java))
                it.generateLegacyClasspathTask.set(target.generateLegacyDataClasspath)

                it.mixins(target.data.value.map { it.sourceSet })
            }
        }
            .sourceSet(target.data.value.map(Compilation::sourceSet))
    }

    override val clientData: LazyConfigurable<MinecraftRunConfiguration> = project.lazyConfigurable {
        create(ClochePlugin.CLIENT_COMPILATION_NAME, ClochePlugin.DATA_COMPILATION_NAME) {
            it.clientData {
                it.modId.set(project.extension<ClocheExtension>().metadata.modId)
                it.minecraftVersion.set(target.minecraftVersion)
                it.patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                it.outputDirectory.set(target.datagenDirectory)
                it.downloadAssetsTask.set(project.tasks.named(target.sourceSet.downloadAssetsTaskName, DownloadAssets::class.java))
                it.generateLegacyClasspathTask.set(target.generateLegacyDataClasspath)

                it.additionalIncludedSourceSets.add(target.sourceSet)
                it.additionalIncludedSourceSets.add(target.data.value.map(Compilation::sourceSet))

                it.mixins(target.data.value.map { it.sourceSet })
            }
        }
            .sourceSet(target.data.value.map(Compilation::sourceSet))
    }

    override val test = project.lazyConfigurable {
        create(SourceSet.TEST_SOURCE_SET_NAME) {
            it.gameTestServer {
                it.modId.set(project.extension<ClocheExtension>().metadata.modId)
                it.minecraftVersion.set(target.minecraftVersion)
                it.patches.from(project.configurations.named(target.sourceSet.patchesConfigurationName))
                it.additionalIncludedSourceSets.add(target.sourceSet)
                it.generateLegacyClasspathTask.set(target.generateLegacyTestClasspath)

                it.mixins(target.test.value.map { it.sourceSet })
            }
        }
            .sourceSet(target.test.value.map(Compilation::sourceSet))
    }

    override val clientTest: LazyConfigurable<MinecraftRunConfiguration> = project.lazyConfigurable {
        TODO()
    }
}
