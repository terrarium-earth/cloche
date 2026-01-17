import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    groovy

    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = "earth.terrarium"
version = "1.0-SNAPSHOT"

val gradleToolingExtension: SourceSet by sourceSets.creating

val gradleToolingExtensionJar = tasks.register<Jar>(gradleToolingExtension.jarTaskName) {
    from(gradleToolingExtension.output)

    archiveClassifier.set(gradleToolingExtension.name)
}

tasks.named(gradleToolingExtension.getCompileTaskName("groovy"), GroovyCompile::class) {
    classpath += files(gradleToolingExtension.kotlin.destinationDirectory)
}

repositories {
    mavenLocal()
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }

    maven(url = "https://maven.msrandom.net/repository/root/")
}

dependencies {
    compileOnly("com.jetbrains.intellij.platform:eel:latest.release")
    compileOnly("com.jetbrains.intellij.platform:external-system-impl:latest.release")
    compileOnly("com.jetbrains.intellij.platform:eel-provider:latest.release")

    gradleToolingExtension.implementationConfigurationName(kotlin("stdlib"))

    gradleToolingExtension.compileOnlyConfigurationName(group = "com.jetbrains.intellij.gradle", name = "gradle-tooling-extension", version = "latest.release") {
        exclude("org.jetbrains.intellij.deps", "gradle-api")
    }

    implementation(files(gradleToolingExtensionJar))

    intellijPlatform {
        intellijIdeaCommunity("2024.3.1")

        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.gradle")

        // plugin("net.msrandom.java-virtual-sourcesets:1.0-SNAPSHOT")
        // plugin("net.msrandom.java-multiplatform:1.0-SNAPSHOT")
        // plugin("net.msrandom.kotlin-class-extensions:1.0-SNAPSHOT")
        plugin("net.msrandom.minecraft-codev:1.0-SNAPSHOT")
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }

    withType<KotlinCompile> {
        compilerOptions.jvmTarget = JvmTarget.JVM_11
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
