package earth.terrarium.cloche

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.platform.commons.annotation.Testable
import java.io.File

@Testable
class ClocheIntegrationTests {
    @Test
    fun integrationTest() {
        GradleRunner.create()
            .withProjectDir(File("integration-test"))
            .withPluginClasspath()
            .withArguments("jar", "forgeJar", "neoforgeJar", "fabricJar", "forgeDataJar", "neoforgeDataJar", "fabricDataJar", "fabricClientJar", "-s")
            .forwardOutput()
            .withDebug(true)
            .build()
    }
}
