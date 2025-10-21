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
import org.gradle.language.jvm.tasks.ProcessResources
import javax.inject.Inject

internal abstract class ForgeCompilationImpl @Inject constructor(info: TargetCompilationInfo<ForgeLikeTargetImpl>) : TargetCompilation<ForgeLikeTargetImpl>(info), ForgeCompilation {
    private val legacyClasspathConfiguration = project.configurations.register(lowerCamelCaseGradleName(target.featureName, featureName, "legacyClasspath")) {
        it.addCollectedDependencies(legacyClasspath)

        attributes(it.attributes)

        it.isCanBeConsumed = false

        it.shouldResolveConsistentlyWith(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName))
    }

    internal val writeLegacyClasspath = project.tasks.register(
        lowerCamelCaseGradleName("write", target.featureName, featureName, "legacyClasspath"),
        WriteClasspathFile::class.java,
    ) { task ->
        task.classpath.from(target.minecraftLibrariesConfiguration)
        task.classpath.from(target.resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra))
        task.classpath.from(legacyClasspathConfiguration)

        if (target is ForgeTargetImpl) {
            task.classpath.from(finalMinecraftFile)
        }
    }

    internal val generateModsToml: TaskProvider<GenerateForgeModsToml> = project.tasks.register(
        lowerCamelCaseGradleName("generate", target.featureName, featureName, "modsToml"),
        GenerateForgeModsToml::class.java
    ) {
        it.metadata.set(target.metadata)
        it.loaderName.set(target.loaderName)

        if (target is ForgeTargetImpl) {
            it.neoforge.set(false)

            it.loaderDependencyVersion.set(
                target.metadata.loaderVersion.orElse(target.loaderVersion.map {
                    target.loaderVersionRange(it.substringBefore('.'))
                }),
            )

            it.output.set(metadataDirectory.map {
                it.dir("META-INF").file("mods.toml")
            })
        } else {
            it.neoforge.set(true)

            it.loaderDependencyVersion.set(target.metadata.loaderVersion.orElse(target.loaderVersionRange("1")))

            it.output.set(metadataDirectory.map {
                it.dir("META-INF").file("neoforge.mods.toml")
            })

            it.mixinConfigs.from(mixins)
        }
    }

    init {
        project.tasks.named(sourceSet.processResourcesTaskName, ProcessResources::class.java) {
            it.from(metadataDirectory)

            it.dependsOn(generateModsToml)
        }

        project.ideaModule(sourceSet) {
            it.resourceDirs.add(metadataDirectory.get().asFile)
        }
    }

    override fun withMetadataToml(action: Action<MetadataFileProvider<TomlTable>>) = generateModsToml.configure {
        it.withToml(action)
    }
}
