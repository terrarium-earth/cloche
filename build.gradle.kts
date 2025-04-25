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
    val codevVersion = "0.5.31"
    implementation(group = "net.msrandom", name = "minecraft-codev-forge", version = codevVersion)
    implementation(group = "net.msrandom", name = "minecraft-codev-fabric", version = codevVersion)
    implementation(group = "net.msrandom", name = "minecraft-codev-mixins", version = codevVersion)
    implementation(group = "net.msrandom", name = "minecraft-codev-runs", version = codevVersion)
    implementation(group = "net.msrandom", name = "minecraft-codev-access-widener", version = codevVersion)
    implementation(group = "net.msrandom", name = "minecraft-codev-remapper", version = codevVersion)
    implementation(group = "net.msrandom", name = "minecraft-codev-decompiler", version = codevVersion)
    implementation(group = "net.msrandom", name = "minecraft-codev-includes", version = codevVersion)

    implementation(group = "net.msrandom", name = "class-extensions-gradle-plugin", version = "1.0.11")
    implementation(group = "net.msrandom", name = "jvm-virtual-source-sets", version = "1.3.1")
    implementation(group = "net.msrandom", name = "classpath-api-stubs", version = "0.1.4")

    implementation(group = "com.google.devtools.ksp", name = "com.google.devtools.ksp.gradle.plugin", version = "2.1.0-1.0.29")

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
