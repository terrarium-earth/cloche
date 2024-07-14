package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.ClochePlugin
import net.msrandom.minecraftcodev.fabric.MinecraftCodevFabricPlugin
import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet

abstract class CommonTarget : ClocheTarget {
    val main: Compilation = run {
        project.objects.newInstance(CommonCompilation::class.java, SourceSet.MAIN_SOURCE_SET_NAME)
    }

    val test: Compilation = run {
        project.objects.newInstance(CommonCompilation::class.java, SourceSet.TEST_SOURCE_SET_NAME)
    }

    val data: Compilation = run {
        project.objects.newInstance(CommonCompilation::class.java, ClochePlugin.DATA_COMPILATION_NAME)
    }

    val client: Compilation = run {
        project.objects.newInstance(CommonCompilation::class.java, ClochePlugin.CLIENT_COMPILATION_NAME)
    }

    override val remapNamespace: String?
        get() = if (useIntermediary.get()) MinecraftCodevFabricPlugin.INTERMEDIARY_MAPPINGS_NAMESPACE else null

    abstract val useIntermediary: Property<Boolean>

    override val accessWideners get() = main.accessWideners

    override val mixins get() = main.mixins

    init {
        apply {
            useIntermediary.convention(true)
        }
    }

    override fun dependencies(action: Action<ClocheDependencyHandler>) {
        main.dependencies(action)
    }
}
