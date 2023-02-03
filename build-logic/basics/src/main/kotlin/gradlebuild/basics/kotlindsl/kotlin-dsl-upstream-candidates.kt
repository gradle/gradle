// This file contains members that we should consider to pull upstream
// and make available to all Kotlin DSL users
package gradlebuild.basics.kotlindsl

import org.gradle.api.Project
import java.io.ByteArrayOutputStream
import java.io.File


/**
 * `dir / "sub"` is the same as `dir.resolve("sub")`.
 *
 * @see [File.resolve]
 */
operator fun File.div(child: String): File =
    resolve(child)


fun Project.execAndGetStdout(workingDir: File, vararg args: String): String {
    val out = ByteArrayOutputStream()
    exec {
        isIgnoreExitValue = true
        commandLine(*args)
        standardOutput = out
        this.workingDir = workingDir
    }
    return out.toString().trim()
}


fun Project.execAndGetStdout(vararg args: String) = execAndGetStdout(File("."), *args)
