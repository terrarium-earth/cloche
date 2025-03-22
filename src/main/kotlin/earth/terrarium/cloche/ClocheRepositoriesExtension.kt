package earth.terrarium.cloche

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.ExtensionAware
import javax.inject.Inject

private const val NEOFORGE_RELEASES_CHANNEL = "releases"

open class ClocheRepositoriesExtension @Inject constructor(private val repositoryHandler: RepositoryHandler) {
    private fun apply(url: String, configure: Action<in MavenArtifactRepository>?): MavenArtifactRepository =
        repositoryHandler.maven {
            it.setUrl("https://$url/")

            configure?.execute(it)
        }

    @JvmOverloads
    fun all(configure: Action<in MavenArtifactRepository>? = null) {
        librariesMinecraft(configure)

        repositoryHandler.mavenCentral {
            configure?.execute(it)
        }

        main(configure)
        mavenFabric(configure)
        mavenNeoforged(configure = configure)
        mavenForge(configure)
    }

    fun all(@DelegatesTo(MavenArtifactRepository::class) configure: Closure<*>) = all {
        configure.rehydrate(it, this, this).call()
    }

    @JvmOverloads
    fun main(configure: Action<in MavenArtifactRepository>? = null) =
        apply("maven.msrandom.net/repository/root", configure)

    fun main(@DelegatesTo(MavenArtifactRepository::class) configure: Closure<*>) = main {
        configure.rehydrate(it, this, this).call()
    }

    @JvmOverloads
    fun librariesMinecraft(configure: Action<in MavenArtifactRepository>? = null) =
        apply("libraries.minecraft.net", configure)

    fun librariesMinecraft(@DelegatesTo(MavenArtifactRepository::class) configure: Closure<*>) = librariesMinecraft {
        configure.rehydrate(it, this, this).call()
    }

    @JvmOverloads
    fun mavenFabric(configure: Action<in MavenArtifactRepository>? = null) = apply("maven.fabricmc.net", configure)

    fun mavenFabric(@DelegatesTo(MavenArtifactRepository::class) configure: Closure<*>) = mavenFabric {
        configure.rehydrate(it, this, this).call()
    }

    @JvmOverloads
    fun mavenForge(configure: Action<in MavenArtifactRepository>? = null) = apply("maven.minecraftforge.net", configure)

    fun mavenForge(@DelegatesTo(MavenArtifactRepository::class) configure: Closure<*>) = mavenForge {
        configure.rehydrate(it, this, this).call()
    }

    @JvmOverloads
    fun mavenNeoforged(
        channel: String = NEOFORGE_RELEASES_CHANNEL,
        configure: Action<in MavenArtifactRepository>? = null
    ) = apply("maven.neoforged.net/$channel", configure)

    @JvmOverloads
    fun mavenNeoforged(
        channel: String = NEOFORGE_RELEASES_CHANNEL,
        @DelegatesTo(MavenArtifactRepository::class) configure: Closure<*>
    ) = mavenNeoforged(channel) {
        configure.rehydrate(it, this, this).call()
    }

    companion object {
        fun register(repositoryHandler: RepositoryHandler) {
            (repositoryHandler as ExtensionAware).extensions.create(
                "cloche",
                ClocheRepositoriesExtension::class.java,
                repositoryHandler
            )
        }
    }
}

val RepositoryHandler.cloche: ClocheRepositoriesExtension
    get() = (this as ExtensionAware).extensions.getByType(ClocheRepositoriesExtension::class.java)

fun RepositoryHandler.cloche(action: Action<ClocheRepositoriesExtension>) =
    action.execute(cloche)

@Suppress("unused")
class RepositoryHandlerGroovyExtensions {
    fun getCloche(repositoryHandler: RepositoryHandler) =
        repositoryHandler.cloche

    fun cloche(
        repositoryHandler: RepositoryHandler,
        @DelegatesTo(ClocheRepositoriesExtension::class) configure: Closure<*>,
    ) = repositoryHandler.cloche {
        configure.rehydrate(it, repositoryHandler, repositoryHandler).call()
    }
}
