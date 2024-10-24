/**
 * TODO: Remove once with Gradle 9.0, used so org.gradle.kotlin.dsl.* is kept
 */
@file:Suppress("UnusedImport")

// This file contains members that we should consider to pull upstream
// and make available to all Kotlin DSL users
package gradlebuild.basics.kotlindsl

import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
/**
 * Used to import assign for Gradle 9.0
 * TODO: Remove once with Gradle 9.0
 */
import org.gradle.kotlin.dsl.*


/**
 * `dir / "sub"` is the same as `dir.resolve("sub")`.
 *
 * @see [File.resolve]
 */
operator fun File.div(child: String): File =
    resolve(child)


fun ExecOperations.execAndGetStdout(workingDir: File, ignoreExitValue: Boolean, vararg args: String): String {
    val out = ByteArrayOutputStream()
    exec {
        isIgnoreExitValue = ignoreExitValue
        commandLine(*args)
        standardOutput = out
        this.workingDir = workingDir
    }
    return out.toString().trim()
}


fun ExecOperations.execAndGetStdoutIgnoringError(vararg args: String) = execAndGetStdout(File("."), true, *args)


fun ExecOperations.execAndGetStdout(vararg args: String) = execAndGetStdout(File("."), false, *args)
