package earth.terrarium.cloche.target

import earth.terrarium.cloche.api.target.ClocheTarget
import org.gradle.api.provider.Provider

interface ClocheTargetInternal: ClocheTarget {
    val hasSeparateClient: Provider<Boolean>
}
