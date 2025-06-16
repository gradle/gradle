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
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.junit.Assert.assertTrue

class ArchiveTaskPermissionsIntegrationTest extends AbstractIntegrationSpec {

    @Requires(UnitTestPreconditions.FilePermissions)
    def "file and directory permissions from file system are replaced by unix defaults when using #taskType task"() {
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
            }
        """

        when:
        run "pack"
        file(archName).usingNativeTools()."$unpackMethod"(file("build"))

        then:
        file("build/child").mode == FileSystem.DEFAULT_DIR_MODE
        file("build/child/reference.txt").mode == FileSystem.DEFAULT_FILE_MODE

        where:
        taskType | unpackMethod
        "Zip"    | "unzipTo"
        "Tar"    | "untarTo"
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "file and directory permissions are preserved when using #taskType task with file system permissions"() {
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
            }
        """

        when:
        run "pack"
        file(archName).usingNativeTools()."$unpackMethod"(file("build"))

        then:
        file("build/child").mode == 0777
        file("build/child/reference.txt").mode == 0746

        where:
        taskType | unpackMethod
        "Zip"    | "unzipTo"
        "Tar"    | "untarTo"
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "file and directory permissions are preserved when using #taskType task with file system permissions global flag"() {
        given:
        createDir('parent') {
            child {
                mode = 0777
                file('reference.txt').mode = 0746
            }
        }
        propertiesFile << """
            org.gradle.archives.use-file-system-permissions=true
        """
        def archName = "test.${taskType.toLowerCase()}"

        and:
        buildFile << """
            tasks.register("pack", $taskType) {
                archiveFileName = "$archName"
                destinationDirectory = projectDir
                from 'parent'
            }
        """

        when:
        run "pack"
        file(archName).usingNativeTools()."$unpackMethod"(file("build"))

        then:
        file("build/child").mode == 0777
        file("build/child/reference.txt").mode == 0746

        where:
        taskType | unpackMethod
        "Zip"    | "unzipTo"
        "Tar"    | "untarTo"
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "file and directory permissions can be overridden in #taskType task"() {
        given:
        createDir('parent') {
            child {
                mode = 0766
                file('reference.txt').mode = 0777
            }
        }
        def archName = "test.${taskType.toLowerCase()}"

        and:
        buildFile << """
            tasks.register("pack", $taskType) {
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
        taskType | unpackMethod
        "Zip"    | "unzipTo"
        "Tar"    | "untarTo"
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "file and directory permissions are preserved for unpacked #taskType archives by default"() {
        given:
        TestFile testDir = createDir('testdir') {
            mode = 0753
            file('reference.txt').mode = 0762
        }
        def archName = "test.${taskType.toLowerCase()}"
        testDir.usingNativeTools()."$packMethod"(file(archName))

        and:
        buildFile << """
            tasks.register("unpack", Copy) {
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
        taskType | packMethod | treeMethod
        "Zip"    | "zipTo"    | "zipTree"
        "Tar"    | "tarTo"    | "tarTree"
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "symlinked file permissions are preserved when using #taskType task with file system permissions"() {
        given:
        createDir('parent') {
            mode = 0777
            file('reference.txt').mode = 0746
            link('link', 'reference.txt')
        }
        def archName = "test.${taskType.toLowerCase()}"
        and:
        buildFile << """
            tasks.register("pack", $taskType) {
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
        taskType | unpackMethod
        "Zip"    | "unzipTo"
        "Tar"    | "untarTo"
    }

    @Requires(UnitTestPreconditions.NoFilePermissions)
    def "file and directory permissions are not preserved when dealing with #taskType archives on OS with no permission support"() {
        given:
        TestFile testDir = createDir('root') {
            def testDir = testdir {
                def testFile = file('reference.txt')
                assertTrue testFile.setReadOnly()
            }
            testDir.setReadOnly()
        }
        testDir.setReadOnly()
        def archName = "test.${taskType.toLowerCase()}"
        testDir."$packMethod"(file(archName))

        and:
        buildFile << """
            tasks.register("unpack", Copy) {
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
        taskType | packMethod | treeMethod
        "Zip"    | "zipTo"    | "zipTree"
        "Tar"    | "tarTo"    | "tarTree"
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "#description configuration overrides previous file system permissions configuration for #taskType archives"() {
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
    def "#description configuration overrides file system permissions global flag for #taskType archives"() {
        given:
        createDir('parent') {
            child {
                mode = 0777
                file('reference.txt').mode = 0746
            }
        }
        def archName = "test.${taskType.toLowerCase()}"

        and:
        propertiesFile << """
            org.gradle.archives.use-file-system-permissions=true
        """
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
        description       | taskType | unpackMethod | permissionConfiguration            | expectedFileMode | expectedDirMode
        "filePermissions" | "Zip"    | "unzipTo"    | "filePermissions { unix('0711') }" | 0711             | 0777
        "dirPermissions"  | "Zip"    | "unzipTo"    | "dirPermissions { unix('0711') }"  | 0746             | 0711
        "filePermissions" | "Tar"    | "untarTo"    | "filePermissions { unix('0711') }" | 0711             | 0777
        "dirPermissions"  | "Tar"    | "untarTo"    | "dirPermissions { unix('0711') }"  | 0746             | 0711
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "file system permissions configuration overrides previous #description configuration for #taskType archives"() {
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
        description       | taskType | unpackMethod | permissionConfiguration
        "filePermissions" | "Zip"    | "unzipTo"    | "filePermissions { unix('0711') }"
        "dirPermissions"  | "Zip"    | "unzipTo"    | "dirPermissions { unix('0711') }"
        "filePermissions" | "Tar"    | "untarTo"    | "filePermissions { unix('0711') }"
        "dirPermissions"  | "Tar"    | "untarTo"    | "dirPermissions { unix('0711') }"
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
    def "calling unset on #description property restores the unix defaults for #taskType tasks"() {
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
        description       | taskType | unpackMethod | permissionConfiguration   | expectedFileMode             | expectedDirMode
        "filePermissions" | "Zip"    | "unzipTo"    | "filePermissions.unset()" | FileSystem.DEFAULT_FILE_MODE | 0777
        "dirPermissions"  | "Zip"    | "unzipTo"    | "dirPermissions.unset()"  | 0746                         | FileSystem.DEFAULT_DIR_MODE
        "filePermissions" | "Tar"    | "untarTo"    | "filePermissions.unset()" | FileSystem.DEFAULT_FILE_MODE | 0777
        "dirPermissions"  | "Tar"    | "untarTo"    | "dirPermissions.unset()"  | 0746                         | FileSystem.DEFAULT_DIR_MODE
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "eachFile takes precedence over #description permissions for files on #taskType tasks"() {
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
                        unix('0700')
                    }
                }
                $permissionConfiguration
            }
        """

        when:
        run "pack"

        then:
        file(archName).usingNativeTools()."$unpackMethod"(file("build"))
        file("build/child/reference.txt").mode == 0700

        where:
        description   | taskType | unpackMethod | permissionConfiguration
        "file-system" | "Zip"    | "unzipTo"    | "useFileSystemPermissions()"
        "explicit"    | "Zip"    | "unzipTo"    | "filePermissions { unix('0777') }"
        "file-system" | "Tar"    | "untarTo"    | "useFileSystemPermissions()"
        "explicit"    | "Tar"    | "untarTo"    | "filePermissions { unix('0777') }"
    }
}
