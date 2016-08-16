package org.gradle.script.lang.kotlin.support

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

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
    zipTo(zipFile.outputStream(), entries)
}

fun zipTo(outputStream: OutputStream, entries: Sequence<Pair<String, ByteArray>>) {
    ZipOutputStream(outputStream).use { zos ->
        entries.forEach { entry ->
            val (path, bytes) = entry
            zos.putNextEntry(ZipEntry(path).apply { size = bytes.size.toLong() })
            zos.write(bytes)
            zos.closeEntry()
        }
    }
}

fun zipOf(entries: Sequence<Pair<String, ByteArray>>): ByteArray =
    ByteArrayOutputStream().run {
        zipTo(this, entries)
        toByteArray()
    }

fun classEntriesFor(vararg classes: Class<*>): Sequence<Pair<String, ByteArray>> =
    classes.asSequence().map {
        val classFilePath = it.name.replace('.', '/') + ".class"
        classFilePath to it.getResource("/$classFilePath").readBytes()
    }
