plugins {
    id("earth.terrarium.cloche")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()

    maven(url = "https://maven.msrandom.net/repository/root")
}

cloche {
    metadata {
        modId.set("mod")
    }

    fabric {
        minecraftVersion.set("1.20.3")
        loaderVersion.set("0.15.11")

        dependencies {
            fabricApi("0.91.1+1.20.3")
        }
    }

    val forgeCommon = common("forgeCommon")

    forge {
        minecraftVersion.set("1.19.4")
        loaderVersion.set("45.2.15")

        dependsOn(forgeCommon)
    }

    neoforge {
        minecraftVersion.set("1.21")
        loaderVersion.set("21.0.76-beta")

        dependsOn(forgeCommon)
    }
}
