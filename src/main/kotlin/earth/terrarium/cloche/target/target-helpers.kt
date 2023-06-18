package earth.terrarium.cloche.target

import net.msrandom.minecraftcodev.accesswidener.dependency.accessWidened
import net.msrandom.minecraftcodev.mixins.dependency.mixin
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.ConfigurableFileCollection

fun Project.accessWiden(dependency: ModuleDependency, accessWideners: ConfigurableFileCollection, compilation: TargetCompilation): ModuleDependency {
    if (accessWideners.isEmpty) {
        return dependency
    }

    val configuration = compilation.accessWidenersConfigurationName
    dependencies.add(configuration, accessWideners)

    return dependency.accessWidened(accessWidenersConfiguration = configuration)
}

fun Project.mixin(dependency: ModuleDependency, mixins: ConfigurableFileCollection, compilation: TargetCompilation): ModuleDependency {
    if (mixins.isEmpty) {
        return dependency
    }

    val configuration = compilation.mixinsConfigurationName
    dependencies.add(configuration, mixins)

    return dependency.mixin(mixinsConfiguration = configuration)
}
