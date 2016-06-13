package org.gradle.script.lang.kotlin.support

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun zipTo(zipFile: File, baseDir: File) {
    val files = baseDir.walkTopDown().filter { it.isFile }
    zipTo(zipFile, baseDir, files)
}

fun zipTo(zipFile: File, baseDir: File, files: Sequence<File>) {
    val entries = files.map { file ->
        val path = file.relativeTo(baseDir).path
        val bytes = file.readBytes()
        path to bytes
    }
    zipTo(zipFile, entries)
}

fun zipTo(zipFile: File, entries: Sequence<Pair<String, ByteArray>>) {
    ZipOutputStream(zipFile.outputStream()).use { zos ->
        entries.forEach { entry ->
            val (path, bytes) = entry
            zos.putNextEntry(ZipEntry(path).apply { size = bytes.size.toLong() })
            zos.write(bytes)
            zos.closeEntry()
        }
    }
}
