package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.ClochePlugin
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

@JvmDefaultWithoutCompatibility
interface CommonTarget : ClocheTarget {
    val main: Compilation
    val data: Compilation?
    val client: Compilation?

    override val accessWideners get() = main.accessWideners
    override val mixins get() = main.mixins
    override val sourceSet get() = main.sourceSet

    fun data(action: Action<Compilation>)

    fun client(action: Action<Compilation>)

    fun withPublication()
}

internal abstract class CommonTargetInternal @Inject constructor(private val name: String) : CommonTarget {
    override val main: CommonCompilation = run {
        project.objects.newInstance(CommonCompilation::class.java, SourceSet.MAIN_SOURCE_SET_NAME, this)
    }

    override var data: CommonCompilation? = null
    override var client: CommonCompilation? = null

    val compilations: DomainObjectCollection<CommonCompilation> = project.objects.domainObjectSet(CommonCompilation::class.java)

    // Not lazy as it has to happen once at configuration time
    var publish = false

    init {
        compilations.add(main)
    }

    override val dependsOn: DomainObjectCollection<CommonTarget> = project.objects.domainObjectSet(CommonTarget::class.java)

    override fun getName() = name

    override fun withJavadocJar() = main.withJavadocJar()
    override fun withSourcesJar() = main.withSourcesJar()

    override fun dependencies(action: Action<ClocheDependencyHandler>) = main.dependencies(action)
    override fun attributes(action: Action<AttributeContainer>) = main.attributes(action)

    override fun withPublication() {
        publish = true
    }

    override fun data(action: Action<Compilation>) = action.execute(withData())
    override fun client(action: Action<Compilation>) = action.execute(withClient())

    fun withClient(): CommonCompilation {
        if (client == null) {
            val client = project.objects.newInstance(CommonCompilation::class.java, ClochePlugin.CLIENT_COMPILATION_NAME, this)

            compilations.add(client)

            this.client = client
        }

        return client!!
    }

    fun withData(): CommonCompilation {
        if (data == null) {
            val data = project.objects.newInstance(CommonCompilation::class.java, ClochePlugin.DATA_COMPILATION_NAME, this)

            compilations.add(data)

            this.data = data
        }

        return data!!
    }
}
