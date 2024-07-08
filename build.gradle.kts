plugins {
    kotlin("jvm") version "1.9.23"
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

    maven(url = "https://maven.fabricmc.net/")
    maven(url = "https://maven.quiltmc.org/repository/release/")
    maven(url = "https://repo.spongepowered.org/repository/maven-public/")
    maven(url = "https://maven.minecraftforge.net/")
    maven(url = "https://maven.msrandom.net/repository/root/")

    mavenCentral()
    gradlePluginPortal()
}

java {
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    implementation(group = "dev.gradleplugins", name = "gradle-api", version = "8.2")

    implementation(group = "net.msrandom", name = "minecraft-codev-forge", version = "0.1.0")
    implementation(group = "net.msrandom", name = "minecraft-codev-fabric", version = "0.1.0")
    implementation(group = "net.msrandom", name = "minecraft-codev-mixins", version = "0.1.0")
    implementation(group = "net.msrandom", name = "minecraft-codev-runs", version = "0.1.0")
    implementation(group = "net.msrandom", name = "minecraft-codev-access-widener", version = "0.1.0")
    implementation(group = "net.msrandom", name = "minecraft-codev-remapper", version = "0.1.0")
    implementation(group = "net.msrandom", name = "minecraft-codev-decompiler", version = "0.1.0")
    implementation(group = "net.msrandom", name = "minecraft-codev-includes", version = "0.1.0")
    implementation(group = "net.msrandom", name = "minecraft-codev-intersections", version = "0.1.0")
    implementation(group = "net.msrandom", name = "java-virtual-source-sets", version = "1.0.0")
    implementation(group = "net.msrandom", name = "class-extensions", version = "1.5.0")

    implementation(group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version = "1.5.32")
    implementation(group = "org.apache.commons", name = "commons-lang3", version = "3.12.0")

    testImplementation(kotlin("test"))
}

publishing {
    repositories {
        mavenLocal()
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}
