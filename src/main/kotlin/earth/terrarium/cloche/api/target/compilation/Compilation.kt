package earth.terrarium.cloche.api.target.compilation

import earth.terrarium.cloche.api.LazyConfigurable
import earth.terrarium.cloche.api.target.ClocheTarget
import earth.terrarium.cloche.api.target.TargetTreeElement
import earth.terrarium.cloche.tasks.data.MetadataFileProvider
import kotlinx.serialization.json.JsonObject
import net.peanuuutz.tomlkt.TomlTable
import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.Dependencies
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet

interface Compilation : TargetTreeElement {
    val accessWideners: ConfigurableFileCollection
        @InputFiles get

    val mixins: ConfigurableFileCollection
        @InputFiles get

    val sourceSet: SourceSet
        @Internal get

    val target: ClocheTarget
        @Internal get

    fun withJavadocJar()
    fun withSourcesJar()

    fun dependencies(action: Action<ClocheDependencyHandler>)
    fun attributes(action: Action<AttributeContainer>)
}

interface FabricSecondarySourceSets : CommonSecondarySourceSets {
    override val data: LazyConfigurable<FabricCompilation>
    override val test: LazyConfigurable<FabricCompilation>
}

interface CommonSecondarySourceSets : Compilation {
    val data: LazyConfigurable<Compilation>
    val test: LazyConfigurable<Compilation>
}

interface FabricCompilation : Compilation {
    fun withMetadataJson(action: Action<MetadataFileProvider<JsonObject>>)
}

interface FabricIncludedClient {
    val mixins: ConfigurableFileCollection
        @InputFiles get
}

interface ForgeCompilation : Compilation, Dependencies {
    val legacyClasspath: DependencyCollector

    fun withMetadataToml(action: Action<MetadataFileProvider<TomlTable>>)
}
