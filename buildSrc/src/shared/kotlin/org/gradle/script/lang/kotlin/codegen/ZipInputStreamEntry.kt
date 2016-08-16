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

package org.gradle.script.lang.kotlin.codegen

import java.io.File
import java.io.InputStream

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ZipInputStreamEntry(val zipInputStream: ZipInputStream, val zipEntry: ZipEntry)

val ZipEntry.isFile: Boolean
    get() = !isDirectory

fun forEachZipEntryIn(file: File, yield: ZipInputStreamEntry.() -> Unit) {
    file.inputStream().use { input ->
        forEachZipEntryIn(input, yield)
    }
}

fun forEachZipEntryIn(input: InputStream, yield: ZipInputStreamEntry.() -> Unit) {
    forEachZipEntryIn(ZipInputStream(input), yield)
}

fun forEachZipEntryIn(zis: ZipInputStream, yield: ZipInputStreamEntry.() -> Unit) {
    while (true) {
        val nextEntry = zis.nextEntry?.let { ZipInputStreamEntry(zis, it) }
        yield(nextEntry ?: break)
    }
}
