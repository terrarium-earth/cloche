package earth.terrarium.cloche

import org.gradle.api.Named

enum class PublicationSide : Named {
    Common,
    Client,
    Joined;

    override fun getName() = name
}
