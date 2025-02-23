package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.target.compilation.ClocheDependencyHandler
import earth.terrarium.cloche.api.target.CommonTarget
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

internal abstract class CommonTargetInternal @Inject constructor(
    private val name: String,
    private val project: Project,
) : CommonTarget,
    CommonSecondarySourceSetsInternal {
    val main: CommonTopLevelCompilation = run {
        project.objects.newInstance(CommonTopLevelCompilation::class.java, SourceSet.MAIN_SOURCE_SET_NAME, this)
    }

    override val sourceSet get() = main.sourceSet
    override val data get() = main.data
    override val test get() = main.test
    override val target = this

    override val client: LazyConfigurableInternal<CommonTopLevelCompilation> = project.lazyConfigurable {
        project.objects.newInstance(
            CommonTopLevelCompilation::class.java,
            ClochePlugin.CLIENT_COMPILATION_NAME,
            this,
        )
    }

    // Not lazy as it has to happen once at configuration time
    var publish = false

    override val dependsOn: DomainObjectCollection<CommonTarget> =
        project.objects.domainObjectSet(CommonTarget::class.java)

    override fun getName() = name

    override fun withJavadocJar() = main.withJavadocJar()
    override fun withSourcesJar() = main.withSourcesJar()

    override fun dependencies(action: Action<ClocheDependencyHandler>) = main.dependencies(action)
    override fun attributes(action: Action<AttributeContainer>) = main.attributes(action)

    override fun withPublication() {
        publish = true
    }

    override fun toString() = name
}
