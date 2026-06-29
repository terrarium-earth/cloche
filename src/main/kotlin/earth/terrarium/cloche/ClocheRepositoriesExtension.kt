package earth.terrarium.cloche

import net.msrandom.minecraftcodev.core.utils.extension
import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.create
import javax.inject.Inject

private const val NEOFORGE_RELEASES_CHANNEL = "releases"
private const val NEOFORGE_MOJANG_META = "mojang-meta"

open class ClocheRepositoriesExtension @Inject constructor(private val repositoryHandler: RepositoryHandler) {
    private fun apply(url: String, configure: Action<in MavenArtifactRepository>?): MavenArtifactRepository =
        repositoryHandler.maven {
            setUrl("https://$url/")

            configure?.execute(this)
        }

    @JvmOverloads
    @Deprecated("Define repositories as needed instead of adding every relevant repository")
    fun all(configure: Action<in MavenArtifactRepository>? = null) {
        librariesMinecraft(configure)
        mavenNeoforgedMeta(configure)

        repositoryHandler.mavenCentral {
            configure?.execute(this)
        }

        main(configure)
        mavenFabric(configure)
        mavenNeoforged(configure = configure)
        mavenForge(configure)
    }

    @JvmOverloads
    fun main(configure: Action<in MavenArtifactRepository>? = null) =
        apply("maven.msrandom.net/repository/root", configure)

    @JvmOverloads
    fun librariesMinecraft(configure: Action<in MavenArtifactRepository>? = null) =
        apply("libraries.minecraft.net", configure)

    @JvmOverloads
    fun mavenFabric(configure: Action<in MavenArtifactRepository>? = null) = apply("maven.fabricmc.net", configure)

    @JvmOverloads
    fun mavenForge(configure: Action<in MavenArtifactRepository>? = null) = apply("maven.minecraftforge.net", configure)

    @JvmOverloads
    fun mavenNeoforged(
        channel: String = NEOFORGE_RELEASES_CHANNEL,
        configure: Action<in MavenArtifactRepository>? = null
    ) = apply("maven.neoforged.net/$channel", configure)

    @JvmOverloads
    fun mavenNeoforgedMeta(configure: Action<in MavenArtifactRepository>? = null) = mavenNeoforged(NEOFORGE_MOJANG_META)

    @JvmOverloads
    fun mavenParchment(configure: Action<in MavenArtifactRepository>? = null) = apply("maven.parchmentmc.org", configure)

    companion object {
        fun register(repositoryHandler: RepositoryHandler) {
            (repositoryHandler as ExtensionAware).extensions.create(
                "cloche",
                ClocheRepositoriesExtension::class,
                repositoryHandler
            )
        }
    }
}

val RepositoryHandler.cloche: ClocheRepositoriesExtension
    get() = (this as ExtensionAware).extension<ClocheRepositoriesExtension>()

fun RepositoryHandler.cloche(action: Action<ClocheRepositoriesExtension>) =
    action.execute(cloche)
