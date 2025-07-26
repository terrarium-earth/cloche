package earth.terrarium.cloche.target.forge.lex

import earth.terrarium.cloche.FORGE
import earth.terrarium.cloche.NO_NAME_MAPPING_ATTRIBUTE
import earth.terrarium.cloche.api.target.ForgeTarget
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.forge.ForgeLikeTargetImpl
import earth.terrarium.cloche.target.getModFiles
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.forge.task.GenerateMcpToSrg
import net.msrandom.minecraftcodev.forge.task.ResolvePatchedMinecraft
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import net.msrandom.minecraftcodev.runs.task.WriteClasspathFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import java.io.File
import javax.inject.Inject

internal abstract class ForgeTargetImpl @Inject constructor(name: String) : ForgeLikeTargetImpl(name), ForgeTarget {
    override val runs: LexForgeRunConfigurations = project.objects.newInstance(LexForgeRunConfigurations::class.java, this)

    override val group
        @Internal
        get() = "net.minecraftforge"

    override val artifact
        @Internal
        get() = "forge"

    override val loaderName get() = FORGE

    override val minecraftRemapNamespace: Provider<String>
        get() = providerFactory.provider { MinecraftCodevForgePlugin.SRG_MAPPINGS_NAMESPACE }

    val generateMcpToSrg: TaskProvider<GenerateMcpToSrg> = project.tasks.register(
        lowerCamelCaseGradleName("generate", featureName, "mcpToSrg"),
        GenerateMcpToSrg::class.java,
    ) {
        it.mappings.set(loadMappingsTask.flatMap(LoadMappings::output))
    }

    override val writeLegacyClasspath = project.tasks.register(
        lowerCamelCaseGradleName("write", featureName, "legacyClasspath"),
        WriteClasspathFile::class.java,
    ) { task ->
        configureLegacyClasspath(task, sourceSet)
    }

    override val writeLegacyDataClasspath = project.tasks.register(
        lowerCamelCaseGradleName("write", featureName, "dataLegacyClasspath"),
        WriteClasspathFile::class.java,
    ) { task ->
        data.onConfigured { data ->
            configureLegacyClasspath(task, data.sourceSet)
        }
    }

    override val writeLegacyTestClasspath = project.tasks.register(
        lowerCamelCaseGradleName("write", featureName, "testLegacyClasspath"),
        WriteClasspathFile::class.java,
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
    }

    private fun configureLegacyClasspath(task: WriteClasspathFile, sourceSet: SourceSet) {
        val classpath = project.files()

        classpath.from(minecraftLibrariesConfiguration)
        classpath.from(resolvePatchedMinecraft.flatMap(ResolvePatchedMinecraft::clientExtra))
        classpath.from(main.finalMinecraftFile)

        task.classpath.from(classpath - project.getModFiles(sourceSet.runtimeClasspathConfigurationName))
    }

    override fun initialize(isSingleTarget: Boolean) {
        super.initialize(isSingleTarget)

        project.dependencies.add(minecraftLibrariesConfiguration.name, "net.msrandom:codev-forge-runtime:0.1.1")

        project.configurations.named(sourceSet.compileClasspathConfigurationName) {
            it.attributes.attribute(NO_NAME_MAPPING_ATTRIBUTE, true)
        }

        project.configurations.named(sourceSet.runtimeClasspathConfigurationName) {
            it.attributes.attribute(NO_NAME_MAPPING_ATTRIBUTE, true)
        }

        minecraftLibrariesConfiguration.attributes.attribute(NO_NAME_MAPPING_ATTRIBUTE, true)
    }

    override fun version(minecraftVersion: String, loaderVersion: String) =
        "$minecraftVersion-$loaderVersion"

    override fun addJarInjects(compilation: CompilationInternal) {
        val configs = object {
            override fun toString(): String {
                return compilation.mixins.joinToString(",", transform = File::getName)
            }
        }
        project.tasks.named(compilation.sourceSet.jarTaskName, Jar::class.java) {
            it.manifest {
                it.attributes["MixinConfigs"] = configs
            }
        }

        includeJarTask.configure {
            it.manifest {
                it.attributes["MixinConfigs"] = configs
            }
        }
    }
}
