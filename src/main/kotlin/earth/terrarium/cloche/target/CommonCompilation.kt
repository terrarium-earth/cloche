package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClocheDependencyHandler
import earth.terrarium.cloche.ClocheExtension
import earth.terrarium.cloche.TargetAttributes
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.plugins.FeatureSpec
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

internal abstract class CommonCompilation @Inject constructor(
    private val name: String,
    val target: CommonTargetInternal,
    project: Project,
) : CompilationInternal {
    override val capabilityGroup = project.group.toString()

    override val capabilityName: String = if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
        project.name
    } else {
        "${project.name}-$name"
    }

    override val dependencySetupActions = mutableListOf<Action<ClocheDependencyHandler>>()
    override val javaFeatureActions = mutableListOf<Action<FeatureSpec>>()
    override val attributeActions = mutableListOf<Action<AttributeContainer>>()

    override fun getName() = name

    override fun attributes(attributes: AttributeContainer) {
        super.attributes(attributes)

        attributes.attribute(TargetAttributes.MOD_LOADER, ClocheExtension::common.name)
    }
}
