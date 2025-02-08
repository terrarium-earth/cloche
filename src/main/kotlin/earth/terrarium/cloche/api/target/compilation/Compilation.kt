package earth.terrarium.cloche.api.target.compilation

import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.target.ClocheTarget
import earth.terrarium.cloche.api.target.TargetTreeElement
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import javax.inject.Inject

interface Compilation : TargetTreeElement {
    val accessWideners: ConfigurableFileCollection
        @InputFiles get

    val mixins: ConfigurableFileCollection
        @InputFiles get

    val sourceSet: SourceSet
        @Internal get

    val target: ClocheTarget
        @Internal get

    val project: Project
        @Inject
        get

    fun withJavadocJar()
    fun withSourcesJar()

    fun dependencies(action: Action<ClocheDependencyHandler>)
    fun attributes(action: Action<AttributeContainer>)
}

interface TargetSecondarySourceSets : CommonSecondarySourceSets

interface CommonSecondarySourceSets : Compilation {
    val data: LazyConfigurable<Compilation>
    val test: LazyConfigurable<Compilation>
}
