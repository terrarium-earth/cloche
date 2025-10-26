# Cloche
A general-purpose Minecraft Gradle plugin for all sorts of use-cases.

Cloche functions in terms of targets, a target can have any Minecraft version or mod loader setup that you compile to, all within the same project.

A plethora of easily configurable features, including but not limited to:
- Separated client source-set where possible
- Simple Data Generation
- Tests for all different source-sets and configurations
- Run configurations generated for various different cases
- Pre-applied mixins, allowing for a better debug experience (WIP)
- Mod metadata(`fabric.mod.json`, `neoforge.mods.toml`, etc) generated for all targets
- Multi-platform utilities when using multiple targets, such as Java @Expect/@Actual annotations and Kotlin multiplatform features
  - Part of the [jvm-multiplatform](https://github.com/MsRandom/jvm-multiplatform) tool suite

### Publishing and Consumption
If you publish a library/mod API with Cloche, variants are automatically configured for consumers, thus if you use the library in common, it will automatically pick the right variants for each consuming target.

## Setup
The basic structure for using Cloche in a `build.gradle`(`.kts`) build script is generally as follows:
```kt
plugins {
    id("earth.terrarium.cloche") version "VERSION"
}

// Group and version can be in gradle.properties as well
group = "net.xyz"
version = "1.0.0"

// Add the relevant repositories, depending on what targets you have
repositories {
  cloche.librariesMinecraft() // libraries.minecraft.net, always recommended first as Mojang sometimes publishes non-standard classifiers there which are needed on certain platforms

  mavenCentral() // Maven central second for best reliability

  cloche {
    main() // General multiplatform or configuration libraries, generally not needed in single-target neoforge

    // Neoforge specific mavens (if neoforge targets are added)
    mavenNeoforgedMeta()
    mavenNeoforged(/* releases */)

    mavenFabric() // maven.fabricmc.net (if fabric targets are added)
    mavenForge() // maven.minecraftforge.net (if forge targets are added)

    mavenParchment() // maven.parchmentmc.org (if parchment is used)
  }
}

cloche {
    metadata {
        // Automatically generate mod metadata file
        modId = "modid"
        name = "Mod Name"
        description = "My Awesome Mod"
        license = "MIT"

        author("XYZ")
    }

    // (Target setup goes here)
}
```

You can then set up the targets in various different ways, for example:

### Neoforge 1.21.1
```kt
minecraftVersion = "1.21.1"

singleTarget {
    // Single target mode
    neoforge {
        loaderVersion = "21.1.26"
    }
}
```

### Source sets and run configurations
Within a target, you can configure data, tests and client source sets and runs(everything below is optional)
```kt
fabric {
    data()
    test()

    // For separate client sourceset
    client {
        data()
        test()
    }

    // otherwise
    includedClient()

    runs {
        // 6 available types of autoconfigured run configurations
        server()
        client()

        data()
        clientData()

        test()
        clientTest()
    }
}

neoforge {
    data()
    test()

    // no client configuration as forge-like targets always include client classes

    runs {
        // Same as above
    }
}
```

### Multi-loader
```kt
minecraftVersion = "1.21.1"

common {
    // common is implicit if not in single target mode, but can be additionally configured
    dependencies {
        implementation(dependencyFactory.create(group = "some.module", name = "my-library", version = "1.0.0"))
    }
}

neoforge {
    loaderVersion = "21.1.26"
}

fabric {
    loaderVersion = "0.16.2"

    dependencies {
        fabricApi("0.102.1+1.21.1") // Optional
    }
}
```

### Multi-version
```kt
// There can be multiple targets of different versions, with a common Jar generated with their common APIs
fabric("1.21.1") {
    minecraftVersion = "1.21.1"

    loaderVersion = "0.16.2"

    dependencies {
        fabricApi("0.102.1+1.21.1")
    }
}

fabric("1.19.4") {
    minecraftVersion = "1.19.4"

    loaderVersion = "0.14.19"

    dependencies {
        fabricApi("0.79.0+1.19.4")
    }
}
```

### Note on naming
When you have multiple combinations of mod loaders & minecraft versions(as is common for multi-version mods supporting both fabric and forge/neoforge),
you can use the `:` character to split the directory structure, ie `fabric:1.20.1` for classifier `fabric-1.20.1` and directory structure `src/fabric/1.20.1`

This could be expanded to any configuration of different loaders and versions.
