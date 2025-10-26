package earth.terrarium.cloche.target

import earth.terrarium.cloche.COMMON
import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.api.attributes.TargetAttributes
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.compilation.CommonSecondarySourceSets
import net.msrandom.minecraftcodev.core.utils.extension
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

internal interface CommonSecondarySourceSetsInternal : CommonSecondarySourceSets {
    override val data: LazyConfigurableInternal<CommonCompilation>
    override val test: LazyConfigurableInternal<CommonCompilation>
}

internal abstract class CommonCompilation @Inject constructor(
    private val name: String,
    override val target: CommonTargetInternal,
    override val isTest: Boolean,
) : CompilationInternal() {
    override val sourceSet: SourceSet = run {
        val sourceSet = project.extension<SourceSetContainer>().maybeCreate(sourceSetName(target, name))

        if (sourceSet.localRuntimeConfigurationName !in project.configurations.names) {
            project.configurations.dependencyScope(sourceSet.localRuntimeConfigurationName)
        }

        if (sourceSet.localImplementationConfigurationName !in project.configurations.names) {
            project.configurations.dependencyScope(sourceSet.localImplementationConfigurationName)
        }

        sourceSet
    }

    override fun getName() = name

    override fun attributes(attributes: AttributeContainer) {
        super.attributes(attributes)

        attributes.attribute(TargetAttributes.MOD_LOADER, COMMON)
    }
}

internal abstract class CommonTopLevelCompilation @Inject constructor(
    name: String,
    target: CommonTargetInternal,
) : CommonCompilation(name, target, false), CommonSecondarySourceSetsInternal {
    private fun name(value: String) = if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
        value
    } else {
        name + TARGET_NAME_PATH_SEPARATOR + value
    }

    override val data: LazyConfigurableInternal<CommonCompilation> = project.lazyConfigurable {
        project.objects.newInstance(name(ClochePlugin.DATA_COMPILATION_NAME), target, false)
    }

    override val test: LazyConfigurableInternal<CommonCompilation> = project.lazyConfigurable {
        project.objects.newInstance(name(SourceSet.TEST_SOURCE_SET_NAME), target, true)
    }

    override val sourceSet: SourceSet
        get() = super<CommonCompilation>.sourceSet

    override val target: CommonTargetInternal
        get() = super<CommonCompilation>.target
}
