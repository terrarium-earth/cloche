package earth.terrarium.cloche.model

import java.io.Serializable

sealed interface Target {
    val name: String?
    val sourceSetName: String
    val dependencies: List<CommonTarget>
}

data class CommonTarget(
    override val name: String,
    override val sourceSetName: String,
    override val dependencies: List<CommonTarget>,
    val dependants: List<MinecraftTarget>,
    val loaders: List<String>,
    val minecraftVersions: List<String>,
) : Target

data class MinecraftTarget(
    override val name: String?,
    override val sourceSetName: String,
    override val dependencies: List<CommonTarget>,
    val loader: String,
    val minecraftVersion: String,
) : Target

interface TargetsModel {
    val commons: List<CommonTarget>
    val targets: List<MinecraftTarget>
}

class TargetsModelImpl(override val commons: List<CommonTarget>, override val targets: List<MinecraftTarget>) :
    TargetsModel, Serializable
