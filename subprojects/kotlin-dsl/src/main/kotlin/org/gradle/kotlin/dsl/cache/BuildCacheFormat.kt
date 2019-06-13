/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.cache

import org.gradle.kotlin.dsl.support.normalisedPathRelativeTo
import org.gradle.kotlin.dsl.support.useToRun
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream


internal
data class PackMetadata(
    val buildInvocationId: String,
    val executionTimeMillis: Long
)


internal
fun pack(inputDir: File, metadata: PackMetadata, outputStream: OutputStream): Long =

    DataOutputStream(GZIPOutputStream(outputStream)).useToRun {

        writeInt(packFormatVersion)
        packMetadata(metadata)
        packFilesFrom(inputDir)
    }


private
const val packFormatVersion = 1


internal
fun unpack(inputStream: InputStream, outputDir: File): Pair<PackMetadata, Long> =

    DataInputStream(GZIPInputStream(inputStream)).useToRun {

        val packedVersion = readInt()
        require(packedVersion == packFormatVersion) {
            "Unsupported cache format version, expected version $packFormatVersion, got version $packedVersion."
        }

        unpackMetadata() to unpackFilesTo(outputDir)
    }


private
fun DataOutputStream.packMetadata(metadata: PackMetadata) {
    writeUTF(metadata.buildInvocationId)
    writeLong(metadata.executionTimeMillis)
}


private
fun DataInputStream.unpackMetadata(): PackMetadata {
    val buildInvocationId = readUTF()
    val executionTimeMillis = readLong()
    return PackMetadata(buildInvocationId, executionTimeMillis)
}


private
fun DataOutputStream.packFilesFrom(inputDir: File): Long {

    var entryCount = 0L

    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    inputDir.walkTopDown().drop(1).forEach { file ->

        val path = file.normalisedPathRelativeTo(inputDir)
        val isFile = file.isFile

        writeUTF(path)
        writeBoolean(isFile)

        if (isFile) {
            writeLong(file.length())
            file.copyTo(this, buffer)
        }

        entryCount += 1
    }

    writeUTF("")

    return entryCount
}


private
fun DataInputStream.unpackFilesTo(outputDir: File): Long {

    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    var entryCount = 0L

    while (true) {

        val path = readUTF()
        if (path.isEmpty()) break

        val isFile = readBoolean()

        val file = File(outputDir, path)
        if (isFile) {
            val length = readLong()
            copyTo(file, length, buffer)
        } else {
            file.mkdir()
        }

        entryCount += 1
    }

    return entryCount
}


private
fun File.copyTo(out: OutputStream, buffer: ByteArray) {
    inputStream().use { input ->
        var read = input.read(buffer)
        while (read >= 0) {
            out.write(buffer, 0, read)
            read = input.read(buffer)
        }
    }
}


private
fun InputStream.copyTo(file: File, length: Long, buffer: ByteArray) {
    file.outputStream().use { output ->
        var remaining = length
        val bufferSize = buffer.size.toLong()
        while (remaining > 0) {
            val read = read(buffer, 0, remaining.coerceAtMost(bufferSize).toInt())
            output.write(buffer, 0, read)
            remaining -= read
        }
    }
}
