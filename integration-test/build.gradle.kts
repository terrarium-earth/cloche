plugins {
    id("earth.terrarium.cloche")
}

cloche {
    neoforge {
        metadata {
            modId.set("mod")
        }

        minecraftVersion.set("1.21.1")
        loaderVersion.set("21.1.72")
    }
}
