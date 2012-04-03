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
import org.gradle.util.Requires
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile
import org.gradle.util.TestPrecondition
import org.junit.Rule

class ArchiveTaskPermissionsIntegrationTest extends AbstractIntegrationSpec {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "file and directory permissions are preserved when zipped"() {
        given:
        sampleFileTree(dirMode, fileMode)
        and:
        buildFile << """
            task zip(type: Zip) {
                archiveName = "test.zip"
                from 'testparent'
            }
            """
        when:
        run "zip"
        and:
        file("test.zip").usingNativeTools().unzipTo(file("build"))
        then:
        file("build/testchild").mode == dirMode
        file("build/testchild/reference.txt").mode == fileMode
        where:
        fileMode << [0746]
        dirMode << [0777]
    }

    def sampleFileTree(int dirMode, int fileMode) {
        TestFile parent = getTestDir().createDir("testparent")
        TestFile child = parent.createDir("testchild")
        def refFile = child.file("reference.txt") << "test file"

        child.mode = dirMode
        refFile.mode = fileMode

    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "file and directory permissions are preserved when tarred"() {
        given:
        TestFile parent = getTestDir().createDir("testparent")
        TestFile child = parent.createDir("testchild")
        def refFile = child.file("reference.sh") << "test file"

        child.mode = dirMode
        refFile.mode = fileMode

        and:
        buildFile << """
            task tar(type: Tar) {
                archiveName = "test.tar"
                from 'testparent'
            }
            """
        when:
        run "tar"
        and:
        file("test.tar").usingNativeTools().untarTo(file("build"))
        then:
        file("build/testchild").mode == dirMode
        file("build/testchild/reference.sh").mode == fileMode
        where:
        fileMode << [0774]
        dirMode << [0756]
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "file and directory permissions are preserved when jarred"() {
        given:
        TestFile parent = getTestDir().createDir("testparent")
        TestFile child = parent.createDir("testchild")
        def refFile = child.file("reference.sh") << "test file"

        child.mode = dirMode
        refFile.mode = fileMode

        and:
        buildFile << """
                task jar(type: Jar) {
                    archiveName = "test.jar"
                    from 'testparent'
                }
                """
        when:
        run "jar"
        and:
        file("test.jar").usingNativeTools().unzipTo(file("build"))
        then:
        file("build/testchild").mode == dirMode
        file("build/testchild/reference.sh").mode == fileMode
        where:
        fileMode << [0774]
        dirMode << [0756]
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "file and directory permissions can be overridden in jar task"() {
        given:
        TestFile parent = getTestDir().createDir("testparent")
        TestFile child = parent.createDir("testchild")
        def refFile = child.file("reference.sh") << "test file"

        child.mode = 0766
        refFile.mode = 0777

        and:
        buildFile << """
                task jar(type: Jar) {
                    archiveName = "test.jar"
                    fileMode = $fileMode
                    dirMode = $dirMode
                    from 'testparent'
                }
                """
        when:
        run "jar"
        and:
        file("test.jar").usingNativeTools().unzipTo(file("build"))
        then:
        file("build/testchild").mode == dirMode
        file("build/testchild/reference.sh").mode == fileMode
        where:
        fileMode << [0774]
        dirMode << [0756]
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "file and directory permissions can be overridden in tar task"() {
        given:
        TestFile parent = getTestDir().createDir("testparent")
        TestFile child = parent.createDir("testchild")
        def refFile = child.file("reference.sh") << "test file"

        child.mode = 0766
        refFile.mode = 0777

        and:
        buildFile << """
                    task tar(type: Tar) {
                        archiveName = "test.tar"
                        fileMode = $fileMode
                        dirMode = $dirMode
                        from 'testparent'
                    }
                    """
        when:
        run "tar"
        and:
        file("test.tar").usingNativeTools().untarTo(file("build"))
        then:
        file("build/testchild").mode == dirMode
        file("build/testchild/reference.sh").mode == fileMode
        where:
        fileMode << [0774]
        dirMode << [0756]
    }


    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "file and directory permissions can be overridden in zip task"() {
        given:
        TestFile parent = getTestDir().createDir("testparent")
        TestFile child = parent.createDir("testchild")
        def refFile = child.file("reference.sh") << "test file"

        child.mode = 0766
        refFile.mode = 0777

        and:
        buildFile << """
                    task zip(type: Zip) {
                        archiveName = "test.zip"
                        fileMode = $fileMode
                        dirMode = $dirMode
                        from 'testparent'
                    }
                    """
        when:
        run "zip"
        and:
        file("test.zip").usingNativeTools().unzipTo(file("build"))
        then:
        file("build/testchild").mode == dirMode
        file("build/testchild/reference.sh").mode == fileMode
        where:
        fileMode << [0774]
        dirMode << [0756]
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "file and directory permissions are preserved when unzipped"() {
        given:
        TestFile child = getTestDir().createDir("testdir")
        def refFile = child.file("reference.txt") << "test file"
        child.mode = dirMode
        refFile.mode = fileMode
        child.usingNativeTools().zipTo(file("test.zip"))
        and:
        buildFile << """
            task unzip(type: Copy) {
                from zipTree("test.zip")
                into 'unpacked'
            }
            """

        when:
        run "unzip"
        and:
        then:
        file("unpacked/testdir").mode == dirMode
        file("unpacked/testdir/reference.txt").mode == fileMode
        where:
        fileMode << [0762]
        dirMode << [0753]
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "file and directory permissions are preserved when untarred"() {
        given:
        TestFile child = getTestDir().createDir("testdir")
        def refFile = child.file("reference.txt") << "test file"
        child.mode = dirMode
        refFile.mode = fileMode
        child.usingNativeTools().tarTo(file("test.tar"))
        and:
        buildFile << """
            task untar(type: Copy) {
                from tarTree("test.tar")
                into 'unpacked'
            }
            """

        when:
        run "untar"
        and:
        then:
        file("unpacked/testdir").mode == dirMode
        file("unpacked/testdir/reference.txt").mode == fileMode
        where:
        fileMode << [0762]
        dirMode << [0753]
    }
}
