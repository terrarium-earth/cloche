package earth.terrarium.cloche

import earth.terrarium.cloche.metadata.ModMetadata
import earth.terrarium.cloche.target.*
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject
import kotlin.reflect.KClass

open class ClocheExtension @Inject constructor(private val project: Project, objects: ObjectFactory) {
    val minecraftVersion: Property<String> = objects.property(String::class.java)
    val useKotlin: Property<Boolean> = objects.property(Boolean::class.java)
    val extensionPattern: Property<String> = objects.property(String::class.java)

    val commonTargets: NamedDomainObjectContainer<CommonTarget> = objects.domainObjectContainer(CommonTarget::class.java)
    val targets: MinecraftTargetContainer = objects.newInstance(MinecraftTargetContainer::class.java)

    val metadata: ModMetadata = objects.newInstance(ModMetadata::class.java)

    internal val mappingActions = mutableListOf<Action<MappingsBuilder>>()

    private val usedTargets = hashSetOf<Class<out MinecraftTarget>>()

    @JvmOverloads
    fun common(name: String = ::common.name, configure: Action<CommonTarget>? = null) = commonTargets.findByName(name)
        ?.also { configure?.execute(it) }
        ?: configure?.let { commonTargets.create(name, it) }
        ?: commonTargets.create(name)

    @JvmOverloads
    fun fabric(name: String = ::fabric.name, configure: Action<FabricTarget>? = null) = target(name, {
        project.repositories.maven {
            it.url = project.uri("https://maven.fabricmc.net/")
        }
    }, configure)

    @JvmOverloads
    fun forge(name: String = ::forge.name, configure: Action<ForgeTarget>? = null) = target(name, {
        project.repositories.maven {
            it.url = project.uri("https://maven.minecraftforge.net/")

            it.metadataSources { sources ->
                sources.gradleMetadata()
                sources.mavenPom()
                sources.artifact()
            }
        }
    }, configure)

    @JvmOverloads
    fun neoforge(name: String = ::neoforge.name, configure: Action<NeoForgeTarget>? = null) = target(name, {
        project.repositories.maven {
            it.url = project.uri("https://maven.neoforged.net/")
        }
    }, configure)

    @JvmOverloads
    fun quilt(name: String = ::quilt.name, configure: Action<QuiltTarget>? = null) = target(name, {
        project.repositories.maven {
            it.url = project.uri("https://maven.quiltmc.org/repository/release/")
        }
    }, configure)

    fun <T : MinecraftTarget> target(name: String, type: Class<T>, setupTargetType: () -> Unit = {}, configure: Action<T>? = null): T {
        if (usedTargets.add(type)) {
            setupTargetType()
        }

        return targets.withType(type)
            .findByName(name)
            ?.also { configure?.execute(it) }
            ?: configure?.let { targets.create(name, type, it) }
            ?: targets.create(name, type)
    }

    @JvmSynthetic
    fun <T : MinecraftTarget> target(name: String, type: KClass<T>, setupTargetType: () -> Unit = {}, configure: Action<T>? = null) = target(name, type.java, setupTargetType, configure)

    inline fun <reified T : MinecraftTarget> target(name: String, noinline setupTargetType: () -> Unit = {}, configure: Action<T>? = null) =
        target(name, T::class.java, setupTargetType, configure)

    fun mappings(action: Action<MappingsBuilder>) {
        mappingActions.add(action)
    }

    fun metadata(action: Action<ModMetadata>) {
        action.execute(metadata)
    }
}
