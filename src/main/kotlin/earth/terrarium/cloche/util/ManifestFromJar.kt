package earth.terrarium.cloche.util

import org.gradle.api.file.RegularFile
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.java.archives.Manifest
import org.gradle.api.provider.Provider
import java.util.jar.JarFile

// TODO This should be in codev
fun Manifest.fromJars(fileOperations: FileOperations, vararg jarFiles: Provider<RegularFile>) {
    // This is unsafe in the context of a normal provider, as you can typically query file providers without task execution, but this one returns different results before and after execution.
    //  However, in this context it is used in the merge spec for a manifest, which is only queried at execution time and thus *should* be safe
    val manifestFileProviders = jarFiles.map { jarProvider ->
        jarProvider.map { jarFile ->
            fileOperations.zipTree(jarFile)
                .matching {
                    it.include(JarFile.MANIFEST_NAME)
                }
                .singleFile
        }
    }.toTypedArray()

    from(*manifestFileProviders)
}
