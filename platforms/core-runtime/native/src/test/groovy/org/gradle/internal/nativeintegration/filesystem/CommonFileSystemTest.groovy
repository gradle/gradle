/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.nativeintegration.filesystem

import org.gradle.api.JavaVersion
import org.gradle.internal.file.FileException
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.FileType
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.junit.Rule
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributeView

class CommonFileSystemTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def fs = NativeServicesTestFixture.instance.get(FileSystem)

    def "unix permissions cannot be read on non existing file"() {
        def file = tmpDir.file("someFile")

        when:
        fs.getUnixMode(file)

        then:
        FileException e = thrown()
        e.message == "Could not get file mode for '$file'."
    }

    def "unix permissions cannot be set on non existing file"() {
        def file = tmpDir.file("someFile")

        when:
        fs.chmod(file, 0644)

        then:
        FileException e = thrown()
        e.message == "Could not set file mode 644 on '$file'."
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "unix permissions on files can be changed and read"() {
        def f = tmpDir.createFile("someFile\u03B1.txt")

        when:
        fs.chmod(f, mode)

        then:
        fs.getUnixMode(f) == mode
        f.mode == mode

        where:
        mode << [0644, 0600, 0751]
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "unix permissions on directories can be changed and read"() {
        def d = tmpDir.createDir("someDir\u03B1")

        when:
        fs.chmod(d, mode)

        then:
        fs.getUnixMode(d) == mode
        d.mode == mode

        where:
        mode << [0755, 0700, 0722]
    }

    @Requires(UnitTestPreconditions.NoFilePermissions)
    def "unix permissions have default values on unsupported platforms"() {
        expect:
        fs.getUnixMode(tmpDir.createFile("someFile")) == FileSystem.DEFAULT_FILE_MODE
        fs.getUnixMode(tmpDir.createDir("someDir")) == FileSystem.DEFAULT_DIR_MODE
    }

    @Requires(UnitTestPreconditions.NoFilePermissions)
    def "setting unix permissions does nothing on unsupported platforms"() {
        expect:
        fs.chmod(tmpDir.createFile("someFile"), 0644)
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "can create symlink on platforms that support symlinks"() {
        def target = tmpDir.createFile("target.txt")
        def link = tmpDir.file("link.txt")

        when:
        fs.createSymbolicLink(link, target)

        then:
        link.exists()
        link.readLink() == target.absolutePath
    }

    @Requires(UnitTestPreconditions.NoSymlinks)
    def "cannot create symlinks on platforms that do not support symlinks"() {
        def target = tmpDir.createFile("target.txt")
        def link = tmpDir.file("link.txt")

        when:
        fs.createSymbolicLink(link, target)

        then:
        thrown(FileException)
    }

    def "stats missing file"() {
        def file = tmpDir.file("missing")

        expect:
        def stat = fs.stat(file)
        stat.type == FileType.Missing
        stat.lastModified == 0
        stat.length == 0
    }

    def "stats regular file"() {
        def file = tmpDir.file("file")
        file.text = "123"

        expect:
        def stat = fs.stat(file)
        stat.type == FileType.RegularFile
        lastModified(stat) == lastModified(file)
        stat.length == 3
    }

    def "stats directory"() {
        def dir = tmpDir.file("dir").createDir()

        expect:
        def stat = fs.stat(dir)
        stat.type == FileType.Directory
        stat.lastModified == 0
        stat.length == 0
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "stats symlink"() {
        def file = tmpDir.file("file")
        file.text = "123"
        def link = tmpDir.file("link")
        link.createLink(file)

        expect:
        def stat = fs.stat(link)
        stat.type == FileType.RegularFile
        lastModified(stat) == lastModified(file)
        stat.length == 3
    }

    def lastModified(File file) {
        return maybeRoundLastModified(Files.getFileAttributeView(file.toPath(), BasicFileAttributeView, LinkOption.NOFOLLOW_LINKS).readAttributes().lastModifiedTime().toMillis())
    }

    def lastModified(FileMetadata fileMetadata) {
        return maybeRoundLastModified(fileMetadata.lastModified)
    }

    private static maybeRoundLastModified(long lastModified) {
        // Some Java 8 versions on Unix only capture the seconds in lastModified, so we cut off the milliseconds returned from the filesystem as well.
        // For example, Oracle JDK 1.8.0_181-b13 does not capture milliseconds, while OpenJDK 1.8.0_242-b08 does.
        return (JavaVersion.current().java9Compatible || OperatingSystem.current().windows)
            ? lastModified
            : lastModified.intdiv(1000) * 1000
    }
}
