/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.archives.TestReproducibleArchives
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.junit.Assert.assertTrue

@TestReproducibleArchives
class ArchiveTaskPermissionsIntegrationTest extends AbstractIntegrationSpec {

    @Requires(UnitTestPreconditions.FilePermissions)
    def "file and directory permissions are not preserved when using #taskName task by default"() {
        given:
        createDir('parent') {
            child {
                mode = 0777
                file('reference.txt').mode = 0746
            }
        }
        def archName = "test.${taskName.toLowerCase()}"
        and:
        buildFile << """
            task pack(type: $taskName) {
                archiveFileName = "$archName"
                destinationDirectory = projectDir
                from 'parent'
            }
            """
        when:
        run "pack"
        file(archName).usingNativeTools()."$unpackMethod"(file("build"))
        then:
        file("build/child").mode == FileSystem.DEFAULT_DIR_MODE
        file("build/child/reference.txt").mode == FileSystem.DEFAULT_FILE_MODE
        where:
        taskName | unpackMethod
        "Zip"    | "unzipTo"
        "Tar"    | "untarTo"
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "file and directory permissions are preserved when using #taskName task with file system permissions"() {
        given:
        createDir('parent') {
            child {
                mode = 0777
                file('reference.txt').mode = 0746
            }
        }
        def archName = "test.${taskName.toLowerCase()}"
        and:
        buildFile << """
            task pack(type: $taskName) {
                archiveFileName = "$archName"
                destinationDirectory = projectDir
                from 'parent'
                useFileSystemPermissions()
            }
            """
        when:
        run "pack"
        file(archName).usingNativeTools()."$unpackMethod"(file("build"))
        then:
        file("build/child").mode == 0777
        file("build/child/reference.txt").mode == 0746
        where:
        taskName | unpackMethod
        "Zip"    | "unzipTo"
        "Tar"    | "untarTo"
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "file and directory permissions can be overridden in #taskName task"() {
        given:
        createDir('parent') {
            child {
                mode = 0766
                file('reference.txt').mode = 0777
            }
        }
        def archName = "test.${taskName.toLowerCase()}"

        and:
        buildFile << """
                task pack(type: $taskName) {
                    archiveFileName = "$archName"
                    destinationDirectory = projectDir
                    filePermissions { unix("0774") }
                    dirPermissions { unix("0756") }
                    from 'parent'
                }
                """
        when:
        run "pack"
        and:
        file(archName).usingNativeTools()."$unpackMethod"(file("build"))
        then:
        file("build/child").mode == 0756
        file("build/child/reference.txt").mode == 0774
        where:
        taskName | unpackMethod
        "Zip"    | "unzipTo"
        "Tar"    | "untarTo"
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "file and directory permissions are preserved for unpacked #taskName archives by default"() {
        given:
        TestFile testDir = createDir('testdir') {
            mode = 0753
            file('reference.txt').mode = 0762
        }
        def archName = "test.${taskName.toLowerCase()}"
        testDir.usingNativeTools()."$packMethod"(file(archName))
        and:
        buildFile << """
            task unpack(type: Copy) {
                from $treeMethod("$archName")
                into 'unpacked'
            }
            """

        when:
        run "unpack"
        and:
        then:
        file("unpacked/testdir").mode == 0753
        file("unpacked/testdir/reference.txt").mode == 0762
        where:
        taskName | packMethod | treeMethod
        "Zip"    | "zipTo"    | "zipTree"
        "Tar"    | "tarTo"    | "tarTree"
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "symlinked file permissions are preserved when using #taskName task with file system permissions"() {
        given:
        createDir('parent') {
            mode = 0777
            file('reference.txt').mode = 0746
            link('link', 'reference.txt')
        }
        def archName = "test.${taskName.toLowerCase()}"
        and:
        buildFile << """
            task pack(type: $taskName) {
                archiveFileName = "$archName"
                destinationDirectory = projectDir
                from 'parent'
                useFileSystemPermissions()
            }
            """
        when:
        run "pack"
        file(archName).usingNativeTools()."$unpackMethod"(file("build"))
        then:
        file("build/reference.txt").mode == 0746
        file("build/link").mode == file("build/reference.txt").mode
        where:
        taskName | unpackMethod
        "Zip"    | "unzipTo"
        "Tar"    | "untarTo"
    }

    @Requires(UnitTestPreconditions.NoFilePermissions)
    def "file and directory permissions are not preserved when dealing with #taskName archives on OS with no permission support"() {
        given:
        TestFile testDir = createDir('root') {
            def testDir = testdir {
                def testFile = file('reference.txt')
                assertTrue testFile.setReadOnly()
            }
            testDir.setReadOnly()
        }
        testDir.setReadOnly()
        def archName = "test.${taskName.toLowerCase()}"
        testDir."$packMethod"(file(archName))
        and:
        buildFile << """
            task unpack(type: Copy) {
                from $treeMethod("$archName")
                into 'unpacked'
            }
            """
        when:
        run "unpack"
        then:

        def testOutputFile = file("unpacked/testdir/reference.txt")
        testOutputFile.canWrite()

        def testOutputDir = file("unpacked/testdir")
        testOutputDir.canWrite()

        where:
        taskName | packMethod | treeMethod
        "Zip"    | "zipTo"    | "zipTree"
        "Tar"    | "tarTo"    | "tarTree"
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "#description configuration for #taskType overrides file system permissions for #taskType archives"() {
        given:
        createDir('parent') {
            child {
                mode = 0777
                file('reference.txt').mode = 0746
            }
        }
        def archName = "test.${taskType.toLowerCase()}"
        and:
        buildFile << """
            tasks.register("pack", $taskType) {
                archiveFileName = "$archName"
                destinationDirectory = projectDir
                from 'parent'
                useFileSystemPermissions()
                $permissionConfiguration
            }
            """
        when:
        run "pack"

        then:
        file(archName).usingNativeTools()."$unpackMethod"(file("build"))
        file("build/child").mode == expectedDirMode
        file("build/child/reference.txt").mode == expectedFileMode

        where:
        description       | taskType | unpackMethod | permissionConfiguration            | expectedFileMode | expectedDirMode
        "filePermissions" | "Zip"    | "unzipTo"    | "filePermissions { unix('0711') }" | 0711             | 0777
        "dirPermissions"  | "Zip"    | "unzipTo"    | "dirPermissions { unix('0711') }"  | 0746             | 0711
        "filePermissions" | "Tar"    | "untarTo"    | "filePermissions { unix('0711') }" | 0711             | 0777
        "dirPermissions"  | "Tar"    | "untarTo"    | "dirPermissions { unix('0711') }"  | 0746             | 0711
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "can set absent provider for #description permissions for #taskType and that will configure file system permissions"() {
        given:
        createDir('parent') {
            child {
                mode = 0777
                file('reference.txt').mode = 0746
            }
        }
        def archName = "test.${taskType.toLowerCase()}"
        and:
        buildFile << """
            tasks.register("pack", $taskType) {
                archiveFileName = "$archName"
                destinationDirectory = projectDir
                from 'parent'
                $permissionConfiguration
            }
            """

        when:
        run "pack"

        then:
        file(archName).usingNativeTools()."$unpackMethod"(file("build"))
        file("build/child").mode == expectedDirMode
        file("build/child/reference.txt").mode == expectedFileMode

        where:
        description       | taskType | unpackMethod | permissionConfiguration               | expectedFileMode             | expectedDirMode
        "filePermissions" | "Zip"    | "unzipTo"    | "filePermissions = provider { null }" | 0746                         | FileSystem.DEFAULT_DIR_MODE
        "dirPermissions"  | "Zip"    | "unzipTo"    | "dirPermissions = provider { null }"  | FileSystem.DEFAULT_FILE_MODE | 0777
        "filePermissions" | "Tar"    | "untarTo"    | "filePermissions = provider { null }" | 0746                         | FileSystem.DEFAULT_DIR_MODE
        "dirPermissions"  | "Tar"    | "untarTo"    | "dirPermissions = provider { null }"  | FileSystem.DEFAULT_FILE_MODE | 0777
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "eachFile overrides subsequential #description permission setting for files on #taskType"() {
        given:
        createDir('parent') {
            child {
                mode = 0777
                file('reference.txt').mode = 0746
            }
        }
        def archName = "test.${taskType.toLowerCase()}"
        and:
        buildFile << """
            tasks.register("pack", $taskType) {
                archiveFileName = "$archName"
                destinationDirectory = projectDir
                from 'parent'
                eachFile {
                    permissions {
                        user.execute = true
                        user.read = true
                        user.write = true
                        group.read = false
                        group.write = false
                        other.read = false
                        other.write = false
                        other.execute = false
                        other.read = false
                    }
                }
                $permissionConfiguration
            }
            """

        when:
        run "pack"

        then:
        file(archName).usingNativeTools()."$unpackMethod"(file("build"))
        file("build/child/reference.txt").permissions == "rwx------"

        where:
        description    | taskType | unpackMethod | permissionConfiguration
        "fileSystem"   | "Zip"    | "unzipTo"    | "useFileSystemPermissions()"
        "reproducible" | "Zip"    | "unzipTo"    | "filePermissions { unix('0777') }"
        "fileSystem"   | "Tar"    | "untarTo"    | "useFileSystemPermissions()"
        "reproducible" | "Tar"    | "untarTo"    | "filePermissions { unix('0777') }"
    }
}
