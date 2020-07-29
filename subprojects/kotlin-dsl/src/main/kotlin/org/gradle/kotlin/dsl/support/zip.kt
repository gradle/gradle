/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.support

import org.gradle.api.internal.file.archive.ZipCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES

import org.gradle.util.TextUtil.normaliseFileSeparators

import java.io.File
import java.io.InputStream
import java.io.OutputStream

import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream


fun zipTo(zipFile: File, baseDir: File) {
    zipTo(zipFile, baseDir, baseDir.walkReproducibly())
}


internal
fun File.walkReproducibly(): Sequence<File> = sequence {

    require(isDirectory)

    yield(this@walkReproducibly)

    var directories: List<File> = listOf(this@walkReproducibly)
    while (directories.isNotEmpty()) {
        val subDirectories = mutableListOf<File>()
        directories.forEach { dir ->
            dir.listFilesOrdered().partition { it.isDirectory }.let { (childDirectories, childFiles) ->
                yieldAll(childFiles)
                childDirectories.let {
                    yieldAll(it)
                    subDirectories.addAll(it)
                }
            }
        }
        directories = subDirectories
    }
}


private
fun zipTo(zipFile: File, baseDir: File, files: Sequence<File>) {
    zipTo(zipFile, fileEntriesRelativeTo(baseDir, files))
}


private
fun fileEntriesRelativeTo(baseDir: File, files: Sequence<File>): Sequence<Pair<String, ByteArray>> =
    files.filter { it.isFile }.map { file ->
        val path = file.normalisedPathRelativeTo(baseDir)
        val bytes = file.readBytes()
        path to bytes
    }


internal
fun File.normalisedPathRelativeTo(baseDir: File) =
    normaliseFileSeparators(relativeTo(baseDir).path)


fun zipTo(zipFile: File, entries: Sequence<Pair<String, ByteArray>>) {
    zipTo(zipFile.outputStream(), entries)
}


private
fun zipTo(outputStream: OutputStream, entries: Sequence<Pair<String, ByteArray>>) {
    ZipOutputStream(outputStream).use { zos ->
        entries.forEach { entry ->
            val (path, bytes) = entry
            zos.putNextEntry(ZipEntry(path).apply {
                time = CONSTANT_TIME_FOR_ZIP_ENTRIES
                size = bytes.size.toLong()
            })
            zos.write(bytes)
            zos.closeEntry()
        }
    }
}


fun unzipTo(outputDirectory: File, zipFile: File) {
    ZipFile(zipFile).use { zip ->
        val outputDirectoryCanonicalPath = outputDirectory.canonicalPath
        for (entry in zip.entries()) {
            unzipEntryTo(outputDirectory, outputDirectoryCanonicalPath, zip, entry)
        }
    }
}


private
fun unzipEntryTo(outputDirectory: File, outputDirectoryCanonicalPath: String, zip: ZipFile, entry: ZipEntry) {
    val output = outputDirectory.resolve(entry.name)
    if (!output.canonicalPath.startsWith(outputDirectoryCanonicalPath)) {
        throw ZipException("Zip entry '${entry.name}' is outside of the output directory")
    }
    if (entry.isDirectory) {
        output.mkdirs()
    } else {
        output.parentFile.mkdirs()
        zip.getInputStream(entry).use { it.copyTo(output) }
    }
}


private
fun InputStream.copyTo(file: File): Long =
    file.outputStream().use { copyTo(it) }
