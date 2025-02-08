package earth.terrarium.cloche.target

import earth.terrarium.cloche.COMMON
import earth.terrarium.cloche.ClochePlugin
import earth.terrarium.cloche.TargetAttributes
import earth.terrarium.cloche.api.target.TARGET_NAME_PATH_SEPARATOR
import earth.terrarium.cloche.api.target.compilation.CommonSecondarySourceSets
import net.msrandom.minecraftcodev.core.utils.extension
import org.gradle.api.Project
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import javax.inject.Inject

internal interface CommonSecondarySourceSetsInternal : CommonSecondarySourceSets {
    override val data: LazyConfigurableInternal<CommonCompilation>
    override val test: LazyConfigurableInternal<CommonCompilation>
}

internal abstract class CommonCompilation @Inject constructor(
    private val name: String,
    override val target: CommonTargetInternal,
) : CompilationInternal() {
    override val sourceSet: SourceSet =
        project.extension<SourceSetContainer>().maybeCreate(sourceSetName(name, target))

    override fun getName() = name

    override fun attributes(attributes: AttributeContainer) {
        super.attributes(attributes)

        attributes.attribute(TargetAttributes.MOD_LOADER, COMMON)
    }
}

internal abstract class CommonTopLevelCompilation @Inject constructor(
    name: String,
    target: CommonTargetInternal,
) : CommonCompilation(name, target), CommonSecondarySourceSetsInternal {
    private fun name(value: String) = if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
        value
    } else {
        name + TARGET_NAME_PATH_SEPARATOR + value
    }

    override val data: LazyConfigurableInternal<CommonCompilation> = project.lazyConfigurable {
        project.objects.newInstance(CommonCompilation::class.java, name(ClochePlugin.DATA_COMPILATION_NAME), target)
    }

    override val test: LazyConfigurableInternal<CommonCompilation> = project.lazyConfigurable {
        project.objects.newInstance(CommonCompilation::class.java, name(SourceSet.TEST_SOURCE_SET_NAME), target)
    }

    override val sourceSet: SourceSet
        get() = super<CommonCompilation>.sourceSet

    override val target: CommonTargetInternal
        get() = super<CommonCompilation>.target
}
