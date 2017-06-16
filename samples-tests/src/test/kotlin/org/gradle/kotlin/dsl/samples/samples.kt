package org.gradle.kotlin.dsl.samples

import org.gradle.kotlin.dsl.fixtures.rootProjectDir

import java.io.File


internal
val samplesRootDir = File(rootProjectDir, "samples")


internal
fun copySampleProject(from: File, to: File) {
    from.copyRecursively(to)
    listOf(".gradle", "build").map { File(to, it) }.filter { it.exists() }.forEach {
        it.deleteRecursively()
    }
}
