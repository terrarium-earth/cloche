package earth.terrarium.cloche.target.forge.lex

import earth.terrarium.cloche.FORGE
import earth.terrarium.cloche.api.target.ForgeTarget
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.forge.ForgeLikeTargetImpl
import earth.terrarium.cloche.target.getModFiles
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.forge.task.GenerateLegacyClasspath
import net.msrandom.minecraftcodev.forge.task.ResolvePatchedMinecraft
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.tasks.Jar
import javax.inject.Inject

internal abstract class ForgeTargetImpl @Inject constructor(name: String) : ForgeLikeTargetImpl(name), ForgeTarget {
    override val group
        @Internal
        get() = "net.minecraftforge"

    override val artifact
        @Internal
        get() = "forge"

    override val loaderName get() = FORGE

    override val minecraftRemapNamespace: Provider<String>
        get() = providerFactory.provider { MinecraftCodevForgePlugin.SRG_MAPPINGS_NAMESPACE }

    override val generateLegacyClasspath = project.tasks.register(
        lowerCamelCaseGradleName("generate", featureName, "legacyClasspath"),
        GenerateLegacyClasspath::class.java,
    ) { task ->
        configureLegacyClasspath(task, sourceSet)
    }

    override val generateLegacyDataClasspath = project.tasks.register(
        lowerCamelCaseGradleName("generate", featureName, "dataLegacyClasspath"),
        GenerateLegacyClasspath::class.java,
    ) { task ->
        data.onConfigured { data ->
            configureLegacyClasspath(task, data.sourceSet)
        }
    }

    override val generateLegacyTestClasspath = project.tasks.register(
        lowerCamelCaseGradleName("generate", featureName, "testLegacyClasspath"),
        GenerateLegacyClasspath::class.java,
    ) { task ->
        test.onConfigured { test ->
            configureLegacyClasspath(task, test.sourceSet)
        }
    }

    init {
        generateModsToml.configure {
            it.loaderDependencyVersion.set(
                metadata.loaderVersion.orElse(loaderVersion.map {
                    loaderVersionRange(it.substringBefore('.'))
                }),
            )
        }

        resolvePatchedMinecraft.configure {
            it.output.set(
                project.layout.file(
                    minecraftVersion.flatMap { mc ->
                        loaderVersion.map { forge ->
                            it.temporaryDir.resolve("forge-$mc-$forge.jar")
                        }
                    }
                )
            )
        }
    }

    private fun configureLegacyClasspath(task: GenerateLegacyClasspath, sourceSet: SourceSet) {
        val classpath = project.files()

        classpath.from(minecraftLibrariesConfiguration)
        classpath.from(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra))
        classpath.from(main.finalMinecraftFile)

        task.classpath.from(classpath - project.getModFiles(sourceSet.runtimeClasspathConfigurationName, isTransitive = false))
    }

    override fun version(minecraftVersion: String, loaderVersion: String) =
        "$minecraftVersion-$loaderVersion"

    override fun addJarInjects(compilation: CompilationInternal) {
        project.tasks.named(compilation.sourceSet.jarTaskName, Jar::class.java) {
            it.manifest {
                it.attributes["MixinConfigs"] = object {
                    override fun toString(): String {
                        return project.configurations.getByName(compilation.sourceSet.mixinsConfigurationName)
                            .joinToString(",") { it.name }
                    }
                }
            }
        }
    }
}
