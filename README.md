# Cloche
A general-purpose Minecraft Gradle plugin for all sorts of use-cases.

Cloche functions in terms of targets, a target can have any Minecraft version or mod loader setup that you compile to, all within the same project.

A plethora of customizable features are enabled by default, including but not limited to:
- Data Generation
- Separated client source-set where possible
- Tests for all different source-sets and configurations
- Run configurations generated for various different cases
- Pre-applied mixins, allowing for a better debug experience
- Mod metadata(`fabric.mod.json`, `neoforge.mods.toml`, etc) generated for all targets
- Multi-platform utilities when using multiple targets, such as Java @Expect/@Actual annotations and Kotlin multiplatform features

### Publishing and Consumption
If you publish a library/mod API with Cloche, variants are automatically configured for consumers, thus if you use the library in common, it will automatically pick the right variants for each consuming target.

## Setup
The basic structure for using this goes as follows in `build.gradle.kts`
```kt
plugins {
    id("earth.terrarium.cloche") version "VERSION"
}

cloche {
    metadata {
        // Automatically generate mod metadata file
        modId.set("modid")
        name.set("Mod ID")
        description.set("My Awesome Mod")
        license.set("MIT")
        authors.add("XYZ")
    }

    // (Target setup goes here)
}
```
You can then set up the targets in various different ways, for example:

### Neoforge 1.21.1
```kt
minecraftVersion.set("1.21.1")

// Having one target means single target mode, which means no common source sets used
neoforge {
    loaderVersion.set("21.1.26")
}
```

### Multi-loader
```kt
minecraftVersion.set("1.21.1")

common {
    // common is implicit if there's multiple targets, but can be additionally configured
    dependencies {
        implementation(group = "some.module", name = "my-library", version = "1.0.0")
    }
}

neoforge {
    loaderVersion.set("21.1.26")
}

fabric {
    loaderVersion.set("0.16.2")

    dependencies {
        fabricApi("0.102.1+1.21.1") // Optional
    }
}
```

### Multi-version
```kt
// There can be multiple targets of different versions, with a common Jar generated with their common APIs
fabric("1.21.1") {
    minecraftVersion.set("1.21.1")

    loaderVersion.set("0.16.2")

    dependencies {
        fabricApi("0.102.1+1.21.1")
    }
}

fabric("1.19.4") {
    minecraftVersion.set("1.19.4")

    loaderVersion.set("0.14.19")
    dependencies {
        fabricApi("0.79.0+1.19.4")
    }
}
```

This could be expanded to any configuration of different loaders and versions.
