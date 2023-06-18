plugins {
    kotlin("jvm") version "1.8.0"
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

    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    implementation(group = "net.msrandom", name = "minecraft-codev-forge", version = "1.0.0")
    implementation(group = "net.msrandom", name = "minecraft-codev-fabric", version = "1.0.0")
    implementation(group = "net.msrandom", name = "minecraft-codev-mixins", version = "1.0.0")
    implementation(group = "net.msrandom", name = "minecraft-codev-runs", version = "1.0.0")
    implementation(group = "net.msrandom", name = "minecraft-codev-access-widener", version = "1.0.0")
    implementation(group = "net.msrandom", name = "minecraft-codev-remapper", version = "1.0.0")
    implementation(group = "net.msrandom", name = "class-extensions", version = "1.3")

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
    jvmToolchain(8)
}
