package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import net.msrandom.minecraftcodev.core.MinecraftCodevExtension
import net.msrandom.minecraftcodev.core.MinecraftType
import net.msrandom.minecraftcodev.remapper.dependency.remapped
import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

abstract class CommonTarget : ClocheTarget, ClientTarget {
    private val main: TargetCompilation = run {
        project.objects.newInstance(TargetCompilation::class.java, KotlinCompilation.MAIN_COMPILATION_NAME)
    }

    private var client: TargetCompilation = run {
        project.objects.newInstance(TargetCompilation::class.java, "client")
    }

    private var data: TargetCompilation? = null

    private var mainDependency = run {
        minecraftVersion.map {
            project.extensions.getByType(MinecraftCodevExtension::class.java)(MinecraftType.Common, it)
        }
    }

    private var clientDependency = run {
        minecraftVersion.map {
            project.extensions.getByType(MinecraftCodevExtension::class.java)(MinecraftType.Client, it)
        }
    }

    override val compilations by lazy {
        listOfNotNull(main, if (clientMode.get() == ClientMode.SourceSet) client else null, data).associateBy(TargetCompilation::getName)
    }

    init {
        main.dependencies { dependencies ->
            project.dependencies.addProvider(dependencies.implementation.configurationName, clientMode.flatMap {
                if (it == ClientMode.Included) {
                    process(clientDependency, client)
                } else {
                    process(mainDependency, client)
                }
            })
        }

        client.dependencies { dependencies ->
            project.dependencies.addProvider(dependencies.implementation.configurationName, process(clientDependency, client))
        }

        apply {
            clientMode.convention(ClientMode.SourceSet)
        }
    }

    private fun process(dependency: Provider<out ModuleDependency>, compilation: TargetCompilation) = dependency.map {
        project.mixin(project.accessWiden(it.remapped(mappingsConfiguration = main.mappingsConfigurationName), accessWideners, compilation), mixins, compilation)
    }

    override fun client(action: Action<Compilation>) = action.execute(client)

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
