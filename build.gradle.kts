import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    kotlin("plugin.serialization") version embeddedKotlinVersion

    `kotlin-dsl`
    `java-gradle-plugin`

    `maven-publish`
    idea
}

gradlePlugin {
    plugins {
        val pluginName = "cloche"

        create(pluginName) {
            id = "$group.$pluginName"
            implementationClass = "earth.terrarium.cloche.ClochePlugin"
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()

    maven(url = "https://maven.fabricmc.net/")
    maven(url = "https://maven.neoforged.net/")
    maven(url = "https://maven.msrandom.net/repository/cloche/")

    gradlePluginPortal()
}

java {
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    implementation(group = "net.msrandom", name = "minecraft-codev-core", version = "0.6.5")
    implementation(group = "net.msrandom", name = "minecraft-codev-forge", version = "0.6.8")
    implementation(group = "net.msrandom", name = "minecraft-codev-fabric", version = "0.6.9")
    implementation(group = "net.msrandom", name = "minecraft-codev-mixins", version = "0.6.0")
    implementation(group = "net.msrandom", name = "minecraft-codev-runs", version = "0.6.7")
    implementation(group = "net.msrandom", name = "minecraft-codev-access-widener", version = "0.6.0")
    implementation(group = "net.msrandom", name = "minecraft-codev-remapper", version = "0.6.8")
    implementation(group = "net.msrandom", name = "minecraft-codev-decompiler", version = "0.6.0")
    implementation(group = "net.msrandom", name = "minecraft-codev-includes", version = "0.6.3")

    implementation(group = "net.msrandom", name = "class-extensions-gradle-plugin", version = "1.0.12")
    implementation(group = "net.msrandom", name = "jvm-virtual-source-sets", version = "1.3.5")
    implementation(group = "net.msrandom", name = "classpath-api-stubs", version = "0.1.11")

    implementation(group = "net.peanuuutz.tomlkt", name = "tomlkt", version = "0.5.0")
    implementation(group = "org.apache.groovy", name = "groovy-toml", version = "5.0.2")

    implementation(group = "org.apache.commons", name = "commons-lang3", version = "3.18.0")

    implementation(kotlin("gradle-plugin"))

    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

tasks.compileKotlin {
    compilerOptions {
        jvmDefault = JvmDefaultMode.ENABLE
        compilerOptions.freeCompilerArgs.add("-Xcontext-receivers")
    }
}

publishing {
    repositories {
        mavenLocal()

        maven("https://maven.msrandom.net/repository/cloche/") {
            credentials {
                val mavenUsername: String? by project
                val mavenPassword: String? by project

                username = mavenUsername
                password = mavenPassword
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()

    dependsOn(tasks.pluginUnderTestMetadata)
}

kotlin {
    jvmToolchain(17)
}
