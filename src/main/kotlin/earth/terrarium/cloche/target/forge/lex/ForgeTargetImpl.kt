package earth.terrarium.cloche.target.forge.lex

import earth.terrarium.cloche.NO_NAME_MAPPING_ATTRIBUTE
import earth.terrarium.cloche.api.target.ForgeTarget
import earth.terrarium.cloche.api.target.compilation.Compilation
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.forge.ForgeLikeTargetImpl
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.forge.task.GenerateMcpToSrg
import net.msrandom.minecraftcodev.remapper.task.LoadMappings
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
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

    override val minecraftRemapNamespace: Provider<String>
        get() = providerFactory.provider { MinecraftCodevForgePlugin.SRG_MAPPINGS_NAMESPACE }

    val generateMcpToSrg: TaskProvider<GenerateMcpToSrg> = project.tasks.register(
        lowerCamelCaseGradleName("generate", featureName, "mcpToSrg"),
        GenerateMcpToSrg::class.java,
    ) {
        it.mappings.set(loadMappingsTask.flatMap(LoadMappings::output))
    }

    init {
        generateModsToml.configure {
            it.neoforge.set(false)

            it.loaderDependencyVersion.set(
                metadata.loaderVersion.orElse(loaderVersion.map {
                    loaderVersionRange(it.substringBefore('.'))
                }),
            )
        }
    }

    private fun removeNameMappingService(compilation: Compilation) {
        project.configurations.named(compilation.sourceSet.compileClasspathConfigurationName) {
            it.attributes.attribute(NO_NAME_MAPPING_ATTRIBUTE, true)
        }

        project.configurations.named(compilation.sourceSet.runtimeClasspathConfigurationName) {
            it.attributes.attribute(NO_NAME_MAPPING_ATTRIBUTE, true)
        }
    }

    override fun initialize(isSingleTarget: Boolean) {
        super.initialize(isSingleTarget)

        project.dependencies.add(minecraftLibrariesConfiguration.name, "net.msrandom:codev-forge-runtime:0.1.1")

        removeNameMappingService(main)

        data.onConfigured(::removeNameMappingService)
        test.onConfigured(::removeNameMappingService)

        minecraftLibrariesConfiguration.attributes.attribute(NO_NAME_MAPPING_ATTRIBUTE, true)
    }

    override fun version(minecraftVersion: String, loaderVersion: String) =
        "$minecraftVersion-$loaderVersion"

    override fun addJarInjects(compilation: CompilationInternal) {
        project.tasks.named(compilation.sourceSet.jarTaskName, Jar::class.java) {
            it.manifest {
                it.attributes["MixinConfigs"] = object {
                    override fun toString(): String {
                        return compilation.mixins.joinToString(",", transform = File::getName)
                    }
                }
            }
        }
    }
}
