package org.gradle.script.lang.kotlin.samples

import java.io.File
import java.nio.file.Paths


internal
val samplesRootDir = Paths.get("samples").toFile()


internal
fun copySampleProject(from: File, to: File) {
    from.copyRecursively(to)
    listOf(".gradle", "build").map { File(to, it) }.filter { it.exists() }.forEach {
        it.deleteRecursively()
    }
}
