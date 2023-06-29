/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.internal.os.OperatingSystem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File


class DefaultIgnoredConfigurationInputsTest {

    private
    val rootDir = File(if (OperatingSystem.current().isWindows) "C:/test/rootDir" else "/test/rootDir")

    private
    fun createFromPaths(paths: List<String>): DefaultIgnoredConfigurationInputs {
        return DefaultIgnoredConfigurationInputs(paths.joinToString(";"), rootDir)
    }

    @Test
    fun `if created with an empty or null paths list, does not recognize an empty string`() {
        val instance = createFromPaths(emptyList())
        assertFalse(instance.isFileSystemCheckIgnoredFor(File("")))

        val instanceFromNull = DefaultIgnoredConfigurationInputs(null, rootDir)
        assertFalse(instanceFromNull.isFileSystemCheckIgnoredFor(File("")))
    }

    @Test
    fun `does not recognize arbitrary paths by default`() {
        val instance = createFromPaths(emptyList())
        assertFalse(instance.isFileSystemCheckIgnoredFor(File("test")))
    }

    @Test
    fun `does not recognize partial matches unless wildcarded`() {
        val instance = createFromPaths(listOf("abc"))
        assertTrue(instance.isFileSystemCheckIgnoredFor(File("abc")))
        assertFalse(instance.isFileSystemCheckIgnoredFor(File("abcdef")))
        assertFalse(instance.isFileSystemCheckIgnoredFor(File("123abc")))
        assertFalse(instance.isFileSystemCheckIgnoredFor(File("abc/def")))
        assertFalse(instance.isFileSystemCheckIgnoredFor(File("xyz/abc")))
    }

    @Test
    fun `recognizes relative paths against rootDirectory`() {
        val instance = createFromPaths(listOf("test/123"))
        assertTrue(instance.isFileSystemCheckIgnoredFor(rootDir.resolve("test/123")))
    }

    @Test
    fun `recognizes multiple paths`() {
        val instance = createFromPaths(listOf("path/one", "path/two"))
        assertTrue(instance.isFileSystemCheckIgnoredFor(File("path/one")))
        assertTrue(instance.isFileSystemCheckIgnoredFor(File("path/two")))
    }

    @Test
    fun `recognizes path specified with a wildcard but only within one segment`() {
        val instance = createFromPaths(listOf("path/*"))
        assertTrue(instance.isFileSystemCheckIgnoredFor(File("path/a")))
        assertTrue(instance.isFileSystemCheckIgnoredFor(File("path/a.txt")))

        assertFalse(instance.isFileSystemCheckIgnoredFor(File("path/")))
        assertFalse(instance.isFileSystemCheckIgnoredFor(File("path/two/segments")))
    }

    @Test
    fun `recognizes segments that are partially wildcarded`() {
        val instance = createFromPaths(listOf("foo*/*bar*/*baz/*.xml"))
        assertTrue(instance.isFileSystemCheckIgnoredFor(File("foo/bar/baz/.xml")))
        assertTrue(instance.isFileSystemCheckIgnoredFor(File("foo1/2bar3/4baz/abc.xml")))
    }

    @Test
    fun `recognizes double-asterisk wildcards across path segments`() {
        val instance = createFromPaths(listOf("foo/**/bar/**"))
        assertTrue(instance.isFileSystemCheckIgnoredFor(File("foo/one/two/bar/three")))
        assertTrue(instance.isFileSystemCheckIgnoredFor(File("foo/2/bar/3/4")))
    }

    @Test
    fun `recognizes user-home-based paths against the user home dir`() {
        val instance = createFromPaths(listOf("~/.gradle/foo.bar"))
        assertTrue(instance.isFileSystemCheckIgnoredFor(File(System.getProperty("user.home"), ".gradle/foo.bar")))
    }

    @Test
    fun `recognizes relative paths pointing outside the root directory`() {
        val instance = createFromPaths(listOf("../../test1"))
        assertTrue(instance.isFileSystemCheckIgnoredFor(rootDir.parentFile.parentFile.resolve("test1")))
    }
}
