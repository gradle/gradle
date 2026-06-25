/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl.serialize

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path


/**
 * A [SpoolStore] that overflows to a temporary file placed next to the main state file.
 *
 * The bytes diverted into the spool are plaintext, so [encrypt]/[decrypt] (mirroring the main
 * stream's encryption) are applied on write/read to ensure plaintext never reaches disk.
 *
 * Spool files are deleted explicitly when the [Spool] is closed; [File.deleteOnExit] is registered
 * only as a backstop in case an explicit cleanup is missed.
 */
internal
class TempFileSpoolStore(
    private val mainFile: File,
    private val encrypt: (OutputStream) -> OutputStream,
    private val decrypt: (InputStream) -> InputStream
) : SpoolStore {

    override fun newSpool(): Spool {
        val dir = mainFile.absoluteFile.parentFile.toPath()
        Files.createDirectories(dir)
        val spoolFile = Files.createTempFile(dir, "${mainFile.name}.", ".spool")
        spoolFile.toFile().deleteOnExit()
        return TempFileSpool(spoolFile, encrypt, decrypt)
    }
}


private
class TempFileSpool(
    private val file: Path,
    private val encrypt: (OutputStream) -> OutputStream,
    private val decrypt: (InputStream) -> InputStream
) : Spool {

    override fun outputStream(): OutputStream =
        encrypt(BufferedOutputStream(Files.newOutputStream(file)))

    override fun inputStream(): InputStream =
        decrypt(BufferedInputStream(Files.newInputStream(file)))

    override fun close() {
        Files.deleteIfExists(file)
    }
}
