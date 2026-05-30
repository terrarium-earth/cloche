package earth.terrarium.cloche.target

import earth.terrarium.cloche.ClochePlugin
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Configures JVM target version for Java and Kotlin compilation tasks.
 *
 * Sets the Java compiler release option and, if the Kotlin JVM plugin is applied,
 * sets the Kotlin compiler jvmTarget option.
 *
 * @param sourceSet The source set whose compile tasks should be configured.
 * @param jvmVersion A provider for the JVM version (e.g., 17, 21).
 */
internal fun Project.configureJvmTarget(sourceSet: SourceSet, jvmVersion: Provider<Int>) {
    tasks.named<JavaCompile>(sourceSet.compileJavaTaskName) {
        options.release.set(jvmVersion)
    }

    plugins.withId(ClochePlugin.KOTLIN_JVM_PLUGIN_ID) {
        tasks.named<KotlinCompile>(sourceSet.getCompileTaskName("kotlin")) {
            compilerOptions.jvmTarget.set(jvmVersion.map {
                JvmTarget.fromTarget(JavaVersion.toVersion(it).toString())
            })
        }
    }
}
