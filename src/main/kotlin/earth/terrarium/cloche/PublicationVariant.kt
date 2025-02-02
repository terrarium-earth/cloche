package earth.terrarium.cloche

import org.gradle.api.Named

enum class PublicationVariant : Named {
    Common,
    Client,
    Joined,
    Data;

    override fun getName() = name
}
