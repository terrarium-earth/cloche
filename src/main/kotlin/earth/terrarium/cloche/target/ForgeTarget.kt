package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import net.msrandom.minecraftcodev.core.MinecraftCodevExtension
import net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin
import net.msrandom.minecraftcodev.forge.PatchedMinecraftCodevExtension
import net.msrandom.minecraftcodev.remapper.dependency.remapped
import org.gradle.api.Action
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

abstract class ForgeTarget : MinecraftTarget {
    private val main: TargetCompilation = run {
        project.objects.newInstance(TargetCompilation::class.java, KotlinCompilation.MAIN_COMPILATION_NAME)
    }

    private val test: TargetCompilation = run {
        project.objects.newInstance(TargetCompilation::class.java, KotlinCompilation.TEST_COMPILATION_NAME)
    }

    private var data: TargetCompilation? = null

    private var dependency = run {
        minecraftVersion.map {
            project.extensions
                .getByType(MinecraftCodevExtension::class.java)
                .extensions
                .getByType(PatchedMinecraftCodevExtension::class.java)(it, patchesConfiguration = main.patchesConfigurationName)
        }
    }

    override val compilations by lazy {
        listOfNotNull(main, data, test).associateBy(TargetCompilation::getName)
    }

    override val remapNamespace: String
        get() = MinecraftCodevForgePlugin.SRG_MAPPINGS_NAMESPACE

    abstract val loaderVersion: Property<String>
        @Input
        get

    abstract val userdevClassifier: Property<String>
        @Input
        @Optional
        get


    init {
        main.dependencies { dependencies ->
            val userdev = minecraftVersion.flatMap { minecraftVersion ->
                loaderVersion.flatMap { forgeVersion ->
                    userdevClassifier.orElse("userdev").map { userdev ->
                        "net.minecraftforge:forge:$minecraftVersion-$forgeVersion:$userdev"
                    }
                }
            }

            project.dependencies.addProvider(main.mappingsConfigurationName, userdev)
            project.dependencies.addProvider(main.patchesConfigurationName, userdev)

            project.dependencies.addProvider(dependencies.implementation.configurationName, process(dependency, main))
        }
    }

    private fun process(dependency: Provider<out ModuleDependency>, compilation: TargetCompilation) = dependency.map {
        project.mixin(project.accessWiden(it.remapped(mappingsConfiguration = main.mappingsConfigurationName), accessWideners, compilation), mixins, compilation)
    }

    override fun test(action: Action<Compilation>?) {
        action?.execute(test)
    }

    override fun data(action: Action<Compilation>?) {
        val data = data ?: project.objects.newInstance(TargetCompilation::class.java, "data").also {
            data = it
        }

        action?.execute(data)
    }

    override fun dependencies(action: Action<ClocheDependencyHandler>) = main.dependencies(action)

    override fun mappings(action: Action<MappingsBuilder>) {
        val mappings = mutableListOf<MappingDependencyProvider>()

        action.execute(MappingsBuilder(project, mappings))

        main.dependencies {
            for (mapping in mappings) {
                project.dependencies.addProvider(main.mappingsConfigurationName, minecraftVersion.map(mapping))
            }
        }
    }
}
