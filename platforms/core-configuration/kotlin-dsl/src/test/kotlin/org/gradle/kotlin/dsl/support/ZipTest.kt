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

package org.gradle.kotlin.dsl.support

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles

import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertFalse
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail
import org.junit.Test

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


class ZipTest : TestWithTempFiles() {

    @Test
    fun `unzip fails on path traversal attempts`() {

        val maliciousZip = file("malicious.zip").apply {
            ZipOutputStream(outputStream()).use { zip ->
                val content = "suspicious".toByteArray()
                zip.putNextEntry(ZipEntry("path/../../traversal.txt".replace('/', File.separatorChar)).apply { size = content.size.toLong() })
                zip.write(content)
                zip.closeEntry()
            }
        }

        try {
            unzipTo(file("output/directory"), maliciousZip)
            fail()
        } catch (ex: IllegalArgumentException) {
            assertThat(ex.message, equalTo("'path/../../traversal.txt' is not a safe archive entry or path name.".replace('/', File.separatorChar)))
        }
        assertFalse(file("output").exists())
    }
}
