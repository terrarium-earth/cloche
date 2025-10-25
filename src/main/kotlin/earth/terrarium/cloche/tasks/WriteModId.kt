package earth.terrarium.cloche.tasks

import net.msrandom.minecraftcodev.core.utils.getAsPath
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

@CacheableTask
abstract class WriteModId : DefaultTask() {
    abstract val modId: Property<String>
        @Optional
        @Input
        get

    abstract val outputFile: RegularFileProperty
        @OutputFile get

    @TaskAction
    fun write() {
        val outputPath = outputFile.getAsPath()

        if (modId.isPresent) {
            outputPath.writeText(modId.get())
        } else {
            outputPath.deleteIfExists()
        }
    }
}
