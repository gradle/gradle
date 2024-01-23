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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.junit.Assert.assertTrue

@TestReproducibleArchives
class ArchiveTaskPermissionsIntegrationTest extends AbstractIntegrationSpec {

    @Requires(UnitTestPreconditions.FilePermissions)
    def "file and directory permissions are preserved when using #taskName task"() {
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
        file("build/child").mode == 0777
        file("build/child/reference.txt").mode == 0746
        where:
        taskName | unpackMethod
        "Zip"    | "unzipTo"
        "Tar"    | "untarTo"
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "file and directory permissions are preserved for intermediate directories when using #taskName task"() {
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
                into 'prefix'
            }
            """
        when:
        run "pack"
        file(archName).usingNativeTools()."$unpackMethod"(file("build"))
        then:
        file("build/prefix").mode == 0755
        file("build/prefix/child").mode == 0777
        file("build/prefix/child/reference.txt").mode == 0746
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
    def "file and directory permissions can be overridden when prefix is present in #taskName task"() {
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
                    filePermissions { unix("rwxr--r--") }
                    dirPermissions { unix("rwx---rwx") }
                    from 'parent'
                    into 'prefix'
                }
                """
        when:
        run "pack"
        and:
        file(archName).usingNativeTools()."$unpackMethod"(file("build"))
        then:
        file("build/prefix").permissions == "rwxr-xr-x" //TODO: investigate
        file("build/prefix/child").permissions == "rwx---rwx"
        file("build/prefix/child/reference.txt").permissions == "rwxr--r--"
        where:
        taskName | unpackMethod
        "Zip"    | "unzipTo"
        "Tar"    | "untarTo"
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "file and directory permissions can be overridden for subpaths in #taskName task"() {
        given:
        def originalPermission = "rwxrwxrwx"
        file("files/sub/a.txt").createFile().setPermissions(originalPermission)
        file("files/sub/dir/b.txt").createFile().setPermissions(originalPermission)
        file("files/c.txt").createFile().setPermissions(originalPermission)
        file("files/sub/empty").createDir().setPermissions(originalPermission)

        def mainDirPermissions = "rwxrwx---"
        def subfolderPermissions = "rwx---rwx"
        def subsubfolderPermissions = "rwx------"

        def archName = "test.${taskName.toLowerCase()}"

        and:
        buildFile << """
            task pack(type: $taskName) {
                archiveFileName = "$archName"
                destinationDirectory = projectDir

                dirPermissions { unix("${mainDirPermissions}") }
                into("prefix") {
                  dirPermissions { unix("${subfolderPermissions}") } // ignored
                  from("files") {
                    dirPermissions { unix("${subsubfolderPermissions}") }
                  }
                }
            }
            """
        when:
        run "pack"
        and:
        file(archName).usingNativeTools()."$unpackMethod"(file("unpacked"))
        then:
        file('unpacked').assertHasDescendants(
            'prefix/sub/a.txt',
            'prefix/sub/dir/b.txt',
            'prefix/c.txt',
            'prefix/sub/empty'
        )
        file("unpacked/prefix").permissions == subfolderPermissions
        file("unpacked/prefix/sub").permissions == subsubfolderPermissions
        where:
        taskName | unpackMethod
        "Zip"    | "unzipTo"
        "Tar"    | "untarTo"
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "file and directory permissions are preserved for unpacked #taskName archives"() {
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
    def "symlinked file permissions are preserved when using #taskName task"() {
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
}
