package earth.terrarium.cloche.target

import earth.terrarium.cloche.COMMON
import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.TargetAttributes
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.attributes.AttributeContainer
import javax.inject.Inject

internal abstract class CommonCompilation @Inject constructor(
    private val name: String,
    val target: CommonTargetInternal,
    project: Project,
) : CompilationInternal {
    override val dependencySetupActions = project.objects.domainObjectSet(Action::class.java) as DomainObjectCollection<Action<ClocheDependencyHandler>>
    override val attributeActions = project.objects.domainObjectSet(Action::class.java) as DomainObjectCollection<Action<AttributeContainer>>

    override var withJavadoc: Boolean = false
    override var withSources: Boolean = false

    override fun getName() = name

    override fun attributes(attributes: AttributeContainer) {
        super.attributes(attributes)

        attributes.attribute(TargetAttributes.MOD_LOADER, COMMON)
    }
}
