/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.classpath


import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.util.zip.ZipEntry

class InPlaceClasspathBuilderTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(InPlaceClasspathBuilderTest)
    InPlaceClasspathBuilder builder = new InPlaceClasspathBuilder()

    def "creates an empty jar"() {
        def file = tmpDir.file("thing.zip")

        when:
        builder.jar(file) {}

        then:
        def zip = new ZipTestFixture(file)
        zip.hasDescendants()
        zip.hasDirs()
    }

    def "can construct jar with entries"() {
        def file = tmpDir.file("thing.zip")

        when:
        builder.jar(file) {
            it.put("a.class", "bytes".bytes)
            it.put("dir/b.class", "bytes".bytes)
            it.put("some/dir/c.class", "bytes".bytes)
        }

        then:
        def zip = new ZipTestFixture(file)
        zip.hasDescendants("a.class", "dir/b.class", "some/dir/c.class")
        zip.hasDirs("dir", "some", "some/dir")
    }

    def "overrides previously constructed jar"() {
        def file = tmpDir.file("thing.zip")

        when:
        builder.jar(file) {
            it.put("a.class", "bytes".bytes)
            it.put("dir/b.class", "bytes".bytes)
            it.put("some/dir/c.class", "bytes".bytes)
        }
        def zip = new ZipTestFixture(file)

        then:
        zip.hasDescendants("a.class", "dir/b.class", "some/dir/c.class")
        zip.hasDirs("dir", "some", "some/dir")

        when:
        builder.jar(file) {
            it.put("a2.class", "bytes".bytes)
            it.put("dir2/b2.class", "bytes".bytes)
            it.put("some2/dir2/c2.class", "bytes".bytes)
        }
        zip = new ZipTestFixture(file)

        then:
        zip.hasDescendants("a2.class", "dir2/b2.class", "some2/dir2/c2.class")
        zip.hasDirs("dir2", "some2", "some2/dir2")
    }

    def "can construct jar with multiple entries in directory"() {
        def file = tmpDir.file("thing.zip")

        when:
        builder.jar(file) {
            it.put("a.class", "bytes".bytes)
            it.put("dir/b.class", "bytes".bytes)
            it.put("dir/c.class", "bytes".bytes)
            it.put("dir/sub/d.class", "bytes".bytes)
        }

        then:
        def zip = new ZipTestFixture(file)
        zip.hasDescendants("a.class", "dir/b.class", "dir/c.class", "dir/sub/d.class")
        zip.hasDirs("dir", "dir/sub")
    }

    def "can construct jar with duplicate entries"() {
        def file = tmpDir.file("thing.zip")

        when:
        builder.jar(file) {
            it.put("a.txt", "bytes".bytes)
            it.put("a.txt", "other bytes".bytes)
            it.put("dir/b.txt", "bytes".bytes)
            it.put("dir/b.txt", "other bytes".bytes)
        }

        then:
        def zip = new ZipTestFixture(file)
        zip.hasDescendants("a.txt", "a.txt", "dir/b.txt", "dir/b.txt")
        zip.hasDirs("dir")
    }

    def "can construct jar with explicitly specified compression method for entries"() {
        def file = tmpDir.file("thing.zip")

        when:
        builder.jar(file) {
            it.put("store.txt", "bytes".bytes, ClasspathEntryVisitor.Entry.CompressionMethod.STORED)
            it.put("undefined.txt", "bytes".bytes, ClasspathEntryVisitor.Entry.CompressionMethod.UNDEFINED)
            it.put("dir/deflated.txt", "bytes".bytes, ClasspathEntryVisitor.Entry.CompressionMethod.DEFLATED)
        }

        then:
        def zip = new ZipTestFixture(file)
        zip.hasCompression("store.txt", ZipEntry.STORED)
        zip.hasCompression("undefined.txt", ZipEntry.DEFLATED)
        zip.hasCompression("dir/deflated.txt", ZipEntry.DEFLATED)
    }
}
