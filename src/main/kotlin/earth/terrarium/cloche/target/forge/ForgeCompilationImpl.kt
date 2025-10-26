package earth.terrarium.cloche.target.forge

import earth.terrarium.cloche.api.target.compilation.ForgeCompilation
import earth.terrarium.cloche.ideaModule
import earth.terrarium.cloche.target.TargetCompilation
import earth.terrarium.cloche.target.TargetCompilationInfo
import earth.terrarium.cloche.target.addCollectedDependencies
import earth.terrarium.cloche.target.forge.lex.ForgeTargetImpl
import earth.terrarium.cloche.tasks.GenerateForgeModsToml
import earth.terrarium.cloche.tasks.data.MetadataFileProvider
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.forge.task.ResolvePatchedMinecraft
import net.msrandom.minecraftcodev.runs.task.WriteClasspathFile
import net.peanuuutz.tomlkt.TomlTable
import org.gradle.api.Action
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.jvm.tasks.ProcessResources
import javax.inject.Inject

internal abstract class ForgeCompilationImpl @Inject constructor(info: TargetCompilationInfo<ForgeLikeTargetImpl>) : TargetCompilation<ForgeLikeTargetImpl>(info), ForgeCompilation {
    private val legacyClasspathConfiguration = project.configurations.register(lowerCamelCaseGradleName(target.featureName, featureName, "legacyClasspath")) {
        addCollectedDependencies(legacyClasspath)

        this@ForgeCompilationImpl.attributes(attributes)

        isCanBeConsumed = false

        shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))
    }

    internal val writeLegacyClasspath = project.tasks.register<WriteClasspathFile>(
        lowerCamelCaseGradleName("write", target.featureName, featureName, "legacyClasspath"),
    ) {
        classpath.from(target.minecraftLibrariesConfiguration)
        classpath.from(target.resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra))
        classpath.from(legacyClasspathConfiguration)

        if (target is ForgeTargetImpl) {
            classpath.from(finalMinecraftFile)
        }
    }

    internal val generateModsToml = project.tasks.register<GenerateForgeModsToml>(
        lowerCamelCaseGradleName("generate", target.featureName, featureName, "modsToml"),
    ) {
        metadata.set(target.metadata)
        loaderName.set(target.loaderName)

        if (target is ForgeTargetImpl) {
            neoforge.set(false)

            loaderDependencyVersion.set(
                target.metadata.loaderVersion.orElse(target.loaderVersion.map {
                    target.loaderVersionRange(it.substringBefore('.'))
                }),
            )

            output.set(metadataDirectory.map {
                it.dir("META-INF").file("mods.toml")
            })
        } else {
            neoforge.set(true)

            loaderDependencyVersion.set(target.metadata.loaderVersion.orElse(target.loaderVersionRange("1")))

            output.set(metadataDirectory.map {
                it.dir("META-INF").file("neoforge.mods.toml")
            })

            mixinConfigs.from(mixins)
        }
    }

    init {
        project.tasks.named<ProcessResources>(sourceSet.processResourcesTaskName) {
            from(metadataDirectory)

            dependsOn(generateModsToml)
        }

        project.ideaModule(sourceSet) {
            it.resourceDirs.add(metadataDirectory.get().asFile)
        }
    }

    override fun withMetadataToml(action: Action<MetadataFileProvider<TomlTable>>) = generateModsToml.configure {
        withToml(action)
    }
}
