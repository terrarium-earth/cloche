package earth.terrarium.cloche.target

import net.msrandom.minecraftcodev.accesswidener.dependency.accessWidened
import net.msrandom.minecraftcodev.accesswidener.dependency.accessWidenersConfigurationName
import org.gradle.api.artifacts.ModuleDependency

fun accessWiden(dependency: ModuleDependency, compilation: TargetCompilation) =
    dependency.accessWidened(accessWidenersConfiguration = compilation.sourceSet.accessWidenersConfigurationName)

fun mixin(dependency: ModuleDependency, compilation: TargetCompilation) =
    dependency//.mixin(mixinsConfiguration = compilation.mixinsConfigurationName)
