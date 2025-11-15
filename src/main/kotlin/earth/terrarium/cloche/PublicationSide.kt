package earth.terrarium.cloche

import org.gradle.api.Named

enum class PublicationSide : Named {
    Common,
    Client;

    override fun getName() = name
}
