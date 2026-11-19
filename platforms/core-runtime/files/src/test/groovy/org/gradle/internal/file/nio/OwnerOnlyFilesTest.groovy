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

package org.gradle.internal.file.nio

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Specification

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.UserPrincipal

@Requires(UnitTestPreconditions.NotWindows)
class OwnerOnlyFilesTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider(getClass())

    def "#method creates a directory and its missing parents"() {
        def dir = tmp.file("parent/dir")

        when:
        create(dir)

        then:
        permissions(dir) == "rwx------"

        where:
        method                           | create
        "createDirectory"                | { File f -> OwnerOnlyFiles.createDirectory(f) }
        "createOrVerifyPrivateDirectory" | { File f -> OwnerOnlyFiles.createOrVerifyPrivateDirectory(f) }
    }

    def "#method creates a directory under a symlink to a directory"() {
        def target = tmp.createDir("target")
        def link = tmp.file("link")
        Files.createSymbolicLink(link.toPath(), target.toPath())

        when:
        create(new File(link, "dir"))

        then:
        permissions(new File(target, "dir")) == "rwx------"

        where:
        method                           | create
        "createDirectory"                | { File f -> OwnerOnlyFiles.createDirectory(f) }
        "createOrVerifyPrivateDirectory" | { File f -> OwnerOnlyFiles.createOrVerifyPrivateDirectory(f) }
    }

    def "restricts an existing directory"() {
        def dir = tmp.createDir("dir")
        Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"))

        when:
        OwnerOnlyFiles.createDirectory(dir)

        then:
        permissions(dir) == "rwx------"
    }

    def "does not turn an existing file into a directory"() {
        def file = tmp.createFile("file")

        when:
        OwnerOnlyFiles.createDirectory(file)

        then:
        def e = thrown(IOException)
        e.message == "Failed to create directory '${file}'"
        e.cause instanceof FileAlreadyExistsException
    }

    def "reports the directory it was asked to create when its parent is a file"() {
        def file = tmp.createFile("file")
        def dir = new File(file, "dir")

        when:
        OwnerOnlyFiles.createDirectory(dir)

        then:
        def e = thrown(IOException)
        e.message == "Failed to create directory '${dir}'"
    }

    def "uses an existing private directory of the current user that others cannot write to, unchanged"() {
        def dir = createDir(tmp.file("dir"), existingPermissions)

        when:
        OwnerOnlyFiles.createOrVerifyPrivateDirectory(dir)

        then:
        permissions(dir) == existingPermissions
        tmp.testDirectory.list() as List == ["dir"]

        where:
        existingPermissions << ["rwx------", "rwxr-xr-x"]
    }

    def "refuses #existing as a private directory"() {
        def location = tmp.file("location")
        create(location)
        def existingPermissions = permissions(location)

        when:
        OwnerOnlyFiles.createOrVerifyPrivateDirectory(location)

        then:
        def e = thrown(OwnerOnlyFiles.UntrustedDirectoryException)
        e.reason == reason
        e.message == "Directory '${location}' cannot be trusted: ${reason}"
        permissions(location) == existingPermissions

        where:
        existing                            | create                                                                   | reason
        "a symbolic link to a directory"    | { File f -> Files.createSymbolicLink(f.toPath(), f.parentFile.toPath()) } | "it is a symbolic link"
        "a file"                            | { File f -> f.text = "" }                                                | "it is not a directory"
        "a directory writable by its group" | { File f -> createDir(f, "rwxrwx---") }                                  | "it is writable by other users"
        "a directory writable by others"    | { File f -> createDir(f, "rwx---rwx") }                                  | "it is writable by other users"
        "a directory only others can write" | { File f -> createDir(f, "rwxr-x-w-") }                                  | "it is writable by other users"
    }

    def "refuses an existing private directory owned by another user"() {
        def dir = createDir(tmp.file("dir"), "rwx------")

        when:
        OwnerOnlyFiles.verifyPrivateDirectory(dir.toPath(), { "someone-else" } as UserPrincipal)

        then:
        def e = thrown(OwnerOnlyFiles.UntrustedDirectoryException)
        e.reason == "it is owned by another user"
    }

    def "opens a new file accessible only to the owner"() {
        def file = tmp.file("file")

        when:
        OwnerOnlyFiles.newOutputStream(file).withCloseable { it.write("content".bytes) }

        then:
        permissions(file) == "rw-------"
        file.text == "content"
    }

    def "a stream opened on an existing file does not see the content of the new one"() {
        def file = tmp.createFile("file")
        file.text = "old"
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-r--r--"))
        def earlierStream = new FileInputStream(file)

        when:
        OwnerOnlyFiles.newOutputStream(file).withCloseable { it.write("new".bytes) }

        then:
        permissions(file) == "rw-------"
        earlierStream.text == "old"

        cleanup:
        earlierStream?.close()
    }

    def "an existing file is replaced as soon as the stream opens and shows content as it is written"() {
        def file = tmp.createFile("file")
        file.text = "old"

        when:
        def stream = OwnerOnlyFiles.newOutputStream(file)

        then:
        file.isFile()
        file.text == ""

        when:
        stream.write("new".bytes)
        stream.flush()

        then:
        file.text == "new"

        when:
        stream.close()

        then:
        permissions(file) == "rw-------"
        tmp.testDirectory.list() as List == ["file"]
    }

    def "leaves no temporary file behind when the file cannot be replaced"() {
        def target = tmp.createDir("target")
        target.createFile("child")

        when:
        OwnerOnlyFiles.newOutputStream(target)

        then:
        thrown(IOException)
        tmp.testDirectory.list() as List == ["target"]
    }

    def "a stream stays open when the writing thread is interrupted"() {
        def file = tmp.file("file")
        def stream = OwnerOnlyFiles.newOutputStream(file)

        when:
        Thread.currentThread().interrupt()
        try {
            stream.write("first".bytes)
        } finally {
            Thread.interrupted()
        }
        stream.write(" second".bytes)
        stream.close()

        then:
        file.text == "first second"
    }

    private static File createDir(File dir, String permissions) {
        Files.createDirectory(dir.toPath())
        // Set separately, as the umask applies to permissions given at creation
        Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString(permissions))
        dir
    }

    private static String permissions(File file) {
        PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath()))
    }
}
