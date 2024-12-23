import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.20"
    `java-gradle-plugin`
    `maven-publish`
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

    maven(url = "https://maven.msrandom.net/repository/root/")

    mavenCentral()
    gradlePluginPortal()
}

java {
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    implementation(group = "net.msrandom", name = "minecraft-codev-forge", version = "0.4.1")
    implementation(group = "net.msrandom", name = "minecraft-codev-fabric", version = "0.4.1")
    implementation(group = "net.msrandom", name = "minecraft-codev-mixins", version = "0.4.1")
    implementation(group = "net.msrandom", name = "minecraft-codev-runs", version = "0.4.1")
    implementation(group = "net.msrandom", name = "minecraft-codev-access-widener", version = "0.4.1")
    implementation(group = "net.msrandom", name = "minecraft-codev-remapper", version = "0.4.1")
    implementation(group = "net.msrandom", name = "minecraft-codev-decompiler", version = "0.4.1")
    implementation(group = "net.msrandom", name = "minecraft-codev-includes", version = "0.4.1")
    implementation(group = "net.msrandom", name = "minecraft-codev-intersections", version = "0.4.1")
    implementation(group = "net.msrandom", name = "java-virtual-source-sets", version = "1.1.1")

    implementation(group = "org.apache.commons", name = "commons-lang3", version = "3.12.0")

    implementation(group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version = "2.0.21")

    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

tasks.withType<KotlinCompile> {
    compilerOptions.freeCompilerArgs.addAll("-Xcontext-receivers", "-Xjvm-default=all")
}

publishing {
    repositories {
        mavenLocal()

        maven("https://maven.msrandom.net/repository/root/") {
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
    jvmToolchain(8)
}
