/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.file

import org.gradle.api.UncheckedIOException
import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.internal.file.FileMetadata.AccessType.DIRECT
import static org.gradle.internal.file.FileMetadata.AccessType.VIA_SYMLINK

@UsesNativeServices
abstract class AbstractFileMetadataAccessorTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    abstract FileMetadataAccessor getAccessor()

    abstract void assertSameLastModified(FileMetadata fileMetadata, File file)

    void assertSameAccessType(FileMetadata fileMetadata, AccessType accessType) {
        assert fileMetadata.accessType == accessType
    }

    def "stats missing file"() {
        def file = tmpDir.file("missing")

        expect:
        def stat = accessor.stat(file)
        stat.type == FileType.Missing
        stat.lastModified == 0
        stat.length == 0
        assertSameAccessType(stat, DIRECT)
    }

    def "stats regular file"() {
        def file = tmpDir.file("file")
        file.text = "123"

        expect:
        def stat = accessor.stat(file)
        stat.type == FileType.RegularFile
        assertSameLastModified(stat, file)
        stat.length == 3
        assertSameAccessType(stat, DIRECT)
    }

    def "stats directory"() {
        def dir = tmpDir.file("dir").createDir()

        expect:
        def stat = accessor.stat(dir)
        stat.type == FileType.Directory
        stat.lastModified == 0
        stat.length == 0
        assertSameAccessType(stat, DIRECT)
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "stats symlink"() {
        def file = tmpDir.file("file")
        file.text = "123"
        def link = tmpDir.file("link")
        link.createLink(file)

        expect:
        def stat = accessor.stat(link)
        stat.type == FileType.RegularFile
        assertSameLastModified(stat, file)
        stat.length == 3
        assertSameAccessType(stat, VIA_SYMLINK)
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "stats symlink to directory"() {
        def dir = tmpDir.createDir("dir")
        def link = tmpDir.file("link")
        link.createLink(dir)

        expect:
        def stat = accessor.stat(link)
        stat.type == FileType.Directory
        stat.lastModified == 0
        stat.length == 0
        assertSameAccessType(stat, VIA_SYMLINK)
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "stats broken symlink"() {
        def file = tmpDir.file("file")
        def link = tmpDir.file("link")
        link.createLink(file)

        expect:
        def stat = accessor.stat(link)
        stat.type == FileType.Missing
        stat.lastModified == 0
        stat.length == 0
        assertSameAccessType(stat, VIA_SYMLINK)
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "stats symlink pointing to symlink pointing to file"() {
        def file = tmpDir.file("file")
        file.text = "123"
        def link = tmpDir.file("link")
        link.createLink(file)
        def linkToLink = tmpDir.file("linkToLink")
        linkToLink.createLink(link)

        expect:
        def stat = accessor.stat(linkToLink)
        stat.type == FileType.RegularFile
        assertSameLastModified(stat, file)
        stat.length == 3
        assertSameAccessType(stat, VIA_SYMLINK)
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "stats symlink pointing to broken symlink"() {
        def file = tmpDir.file("file")
        def link = tmpDir.file("link")
        link.createLink(file)
        def linkToLink = tmpDir.file("linkToBrokenLink")
        linkToLink.createLink(link)

        expect:
        def stat = accessor.stat(linkToLink)
        stat.type == FileType.Missing
        stat.lastModified == 0
        stat.length == 0
        assertSameAccessType(stat, VIA_SYMLINK)
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "stats a symlink cycle"() {
        def first = tmpDir.file("first")
        def second = tmpDir.file("second")
        def third = tmpDir.file("third")
        first.createLink(second)
        second.createLink(third)
        third.createLink(first)

        expect:
        def stat = accessor.stat(first)
        stat.type == FileType.Missing
        stat.lastModified == 0
        stat.length == 0
        assertSameAccessType(stat, VIA_SYMLINK)
    }

    @Requires(UnitTestPreconditions.UnixDerivative)
    def "stats named pipes"() {
        def pipe = tmpDir.file("testPipe").createNamedPipe()

        when:
        accessor.stat(pipe)
        then:
        thrown(UncheckedIOException)
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "stat a file in an unreadable directory"() {
        def unreadableDir = tmpDir.createDir("unreadableDir")
        def fileInDir = unreadableDir.createFile("inDir")
        unreadableDir.makeUnreadable()

        expect:
        def stat = accessor.stat(fileInDir)
        stat.type == FileType.RegularFile
        assertSameLastModified(stat, fileInDir)
        stat.length == 0
        assertSameAccessType(stat, DIRECT)

        cleanup:
        unreadableDir.makeReadable()
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "stat an unreadable file"() {
        def unreadableFile = tmpDir.createFile("unreadable")
        unreadableFile.makeUnreadable()

        expect:
        def stat = accessor.stat(unreadableFile)
        stat.type == FileType.RegularFile
        assertSameLastModified(stat, unreadableFile)
        stat.length == 0
        assertSameAccessType(stat, DIRECT)

        cleanup:
        unreadableFile.makeReadable()
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "stat an unreadable directory"() {
        def unreadableDir = tmpDir.createDir("unreadable")
        unreadableDir.makeUnreadable()

        expect:
        def stat = accessor.stat(unreadableDir)
        stat.type == FileType.Directory
        stat.lastModified == 0
        stat.length == 0
        assertSameAccessType(stat, DIRECT)

        cleanup:
        unreadableDir.makeReadable()
    }
}
