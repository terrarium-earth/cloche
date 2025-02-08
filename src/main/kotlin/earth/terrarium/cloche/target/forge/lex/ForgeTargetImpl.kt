package earth.terrarium.cloche.target.forge.lex

import earth.terrarium.cloche.FORGE
import earth.terrarium.cloche.api.target.ForgeTarget
import earth.terrarium.cloche.target.CompilationInternal
import earth.terrarium.cloche.target.forge.ForgeLikeTargetImpl
import net.msrandom.minecraftcodev.core.utils.lowerCamelCaseGradleName
import net.msrandom.minecraftcodev.forge.task.GenerateLegacyClasspath
import net.msrandom.minecraftcodev.mixins.mixinsConfigurationName
import org.gradle.api.tasks.Internal
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

    override val generateLegacyClasspath = project.tasks.register(
        lowerCamelCaseGradleName("generate", featureName, "legacyClasspath"),
        GenerateLegacyClasspath::class.java,
    ) {
        it.classpath.from(main.sourceSet.runtimeClasspath)
    }

    override val generateLegacyDataClasspath = project.tasks.register(
        lowerCamelCaseGradleName("generate", featureName, "dataLegacyClasspath"),
        GenerateLegacyClasspath::class.java,
        ) {
        it.classpath.from(data.value.map { it.sourceSet.runtimeClasspath })
    }

    override val generateLegacyTestClasspath = project.tasks.register(
        lowerCamelCaseGradleName("generate", featureName, "testLegacyClasspath"),
        GenerateLegacyClasspath::class.java,
    ) {
        it.classpath.from(test.value.map { it.sourceSet.runtimeClasspath })
    }

    init {
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
