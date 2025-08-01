import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.0"
    `java-gradle-plugin`
    `maven-publish`
    idea

    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
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
    implementation(group = "net.msrandom", name = "minecraft-codev-core", version = "0.6.2")
    implementation(group = "net.msrandom", name = "minecraft-codev-forge", version = "0.6.3")
    implementation(group = "net.msrandom", name = "minecraft-codev-fabric", version = "0.6.3")
    implementation(group = "net.msrandom", name = "minecraft-codev-mixins", version = "0.5.32")
    implementation(group = "net.msrandom", name = "minecraft-codev-runs", version = "0.6.4")
    implementation(group = "net.msrandom", name = "minecraft-codev-access-widener", version = "0.5.32")
    implementation(group = "net.msrandom", name = "minecraft-codev-remapper", version = "0.6.3")
    implementation(group = "net.msrandom", name = "minecraft-codev-decompiler", version = "0.5.32")
    implementation(group = "net.msrandom", name = "minecraft-codev-includes", version = "0.6.0")

    implementation(group = "net.msrandom", name = "class-extensions-gradle-plugin", version = "1.0.11")
    implementation(group = "net.msrandom", name = "jvm-virtual-source-sets", version = "1.3.3")
    implementation(group = "net.msrandom", name = "classpath-api-stubs", version = "0.1.8")

    implementation(group = "com.google.devtools.ksp", name = "com.google.devtools.ksp.gradle.plugin", version = "2.1.21-2.0.2")

    implementation(group = "com.moandjiezana.toml", name = "toml4j", version = "0.7.2")

    implementation(group = "org.apache.commons", name = "commons-lang3", version = "3.12.0")

    implementation(kotlin("gradle-plugin"))

    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

tasks.withType<KotlinCompile> {
    compilerOptions.freeCompilerArgs.addAll("-Xcontext-receivers", "-Xjvm-default=all")
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
    jvmToolchain(11)
}
