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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

import static org.junit.Assert.assertTrue

@TestReproducibleArchives
class ArchiveTaskPermissionsIntegrationTest extends AbstractIntegrationSpec {

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    @Unroll
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

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    @Unroll
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
                    fileMode = 0774
                    dirMode = 0756
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

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    @Unroll
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

    @Requires(TestPrecondition.WINDOWS)
    @Unroll
    def "file and directory permissions are not preserved when dealing with #taskName archives on OS with no permission support"() {
        given:
        TestFile testDir = createDir('root') {
            def testDir = testdir{
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
