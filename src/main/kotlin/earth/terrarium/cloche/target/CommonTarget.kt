package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.ClochePlugin
import org.gradle.api.Action
import org.gradle.api.plugins.FeatureSpec
import org.gradle.api.tasks.SourceSet

interface CommonTarget : ClocheTarget {
    val main: Compilation
    val data: Compilation
    val client: Compilation

    fun withPublication()
}

internal abstract class CommonTargetInternal : CommonTarget {
    override val main: CommonCompilation = run {
        project.objects.newInstance(CommonCompilation::class.java, SourceSet.MAIN_SOURCE_SET_NAME)
    }

    override val data: CommonCompilation = run {
        project.objects.newInstance(CommonCompilation::class.java, ClochePlugin.DATA_COMPILATION_NAME)
    }

    override val client: CommonCompilation = run {
        project.objects.newInstance(CommonCompilation::class.java, ClochePlugin.CLIENT_COMPILATION_NAME)
    }

    var publish = false

    override val accessWideners get() = main.accessWideners
    override val mixins get() = main.mixins

    override fun dependencies(action: Action<ClocheDependencyHandler>) = main.dependencies(action)
    override fun java(action: Action<FeatureSpec>) = main.java(action)

    override fun withPublication() {
        publish = true
    }
}
