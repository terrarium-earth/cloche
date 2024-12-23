package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.ClochePlugin
import org.gradle.api.Action
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.plugins.FeatureSpec
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

@JvmDefaultWithoutCompatibility
interface CommonTarget : ClocheTarget {
    val main: Compilation
    val data: Compilation
    val client: Compilation

    fun data(action: Action<Compilation>) {
        action.execute(data)
    }

    fun client(action: Action<Compilation>) {
        action.execute(client)
    }

    fun withPublication()
}

internal abstract class CommonTargetInternal @Inject constructor(private val name: String) : CommonTarget {
    override val main: CommonCompilation = run {
        project.objects.newInstance(CommonCompilation::class.java, SourceSet.MAIN_SOURCE_SET_NAME, this)
    }

    override val data: CommonCompilation = run {
        project.objects.newInstance(CommonCompilation::class.java, ClochePlugin.DATA_COMPILATION_NAME, this)
    }

    override val client: CommonCompilation = run {
        project.objects.newInstance(CommonCompilation::class.java, ClochePlugin.CLIENT_COMPILATION_NAME, this)
    }

    // Not lazy as it has to happen once at configuration time
    var publish = false

    override val accessWideners get() = main.accessWideners
    override val mixins get() = main.mixins

    override fun getName() = name

    override fun withJavadocJar() = main.withJavadocJar()
    override fun withSourcesJar() = main.withSourcesJar()

    override fun dependencies(action: Action<ClocheDependencyHandler>) = main.dependencies(action)
    override fun attributes(action: Action<AttributeContainer>) = main.attributes(action)

    override fun withPublication() {
        publish = true
    }
}
