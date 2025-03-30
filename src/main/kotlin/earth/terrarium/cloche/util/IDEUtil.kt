package earth.terrarium.cloche.util

fun isIDEDetected(): Boolean {
    return isIntelliJIDEADetected() || isVisualStudioCodeDetected() || isEclipseDetected()
}

fun isIntelliJIDEADetected(): Boolean {
    return System.getProperty("idea.sync.active", "false").toBoolean()
}

/**
 * Checks if Visual Studio Code is detected.
 * Credit goes to the NeoForged project, see [NeoForged/ModDevGradle](https://github.com/neoforged/ModDevGradle/blob/12c58a2a8f8fbf03552fe1c7a215ffbd2dd6bcc2/src/main/java/net/neoforged/moddevgradle/internal/utils/IdeDetection.java#L51-L85).
 */
fun isVisualStudioCodeDetected(): Boolean {
    return System.getenv("VSCODE_PID") != null
}

/**
 * Checks if Eclipse is detected.
 * Credit goes to the NeoForged project, see [NeoForged/ModDevGradle](https://github.com/neoforged/ModDevGradle/blob/12c58a2a8f8fbf03552fe1c7a215ffbd2dd6bcc2/src/main/java/net/neoforged/moddevgradle/internal/utils/IdeDetection.java#L51-L85).
 */
fun isEclipseDetected(): Boolean {
    return System.getProperty("eclipse.application") != null
}
