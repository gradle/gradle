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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

import static org.junit.Assert.assertTrue

class CopyPermissionsIntegrationTest extends AbstractIntegrationSpec implements UnreadableCopyDestinationFixture {

    @Requires(UnitTestPreconditions.FilePermissions)
    def "file permissions are preserved in copy action"() {
        given:
        def testSourceFile = file(testFileName)
        testSourceFile << "test file content"
        testSourceFile.mode = mode
        and:
        buildFile << """
        task copy(type: Copy) {
            from "${testSourceFile.absolutePath}"
            into ("build/tmp")
        }
        """

        when:
        run "copy"
        then:
        file("build/tmp/${testFileName}").mode == mode

        where:
        mode | testFileName
        0746 | "reference.txt"
        0746 | "\u0627\u0644\u0627\u0655\u062F\u0627\u0631\u0629.txt"
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "directory permissions are preserved in copy action"() {
        given:
        TestFile parent = getTestDirectory().createDir("testparent")
        TestFile child = parent.createDir("testchild")
        child.file("reference.txt") << "test file"

        child.mode = mode
        and:
        buildFile << """
            task copy(type: Copy) {
                from "testparent"
                into ("build/tmp")
            }
            """
        when:
        run "copy"
        then:
        file("build/tmp/testchild").mode == mode
        where:
        mode << [0755, 0776]
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "symlinked file permissions are preserved in copy action"() {
        given:
        def mode = 0746
        def testSourceFile = file(testFileName)
        testSourceFile << "test file content"
        testSourceFile.mode = mode

        def testSourceFileLink = file("${testFileName}_link").createLink(testSourceFile.getRelativePathFromBase())

        and:
        buildFile << """
        task copy(type: Copy) {
            from "${testSourceFile.absolutePath}"
            from "${testSourceFileLink.absolutePath}"
            into ("build/tmp")
        }
        """

        when:
        run "copy"

        then:
        file("build/tmp/${testFileName}").mode == mode
        file("build/tmp/${testFileName}_link").mode == mode

        where:
        testFileName << ["reference.txt", "\u0627\u0644\u0627\u0655\u062F\u0627\u0631\u0629.txt"]
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "fileMode can be modified in copy task"() {
        given:

        file("reference.txt") << 'test file"'
        file("reference.txt").mode = 0777
        and:
        buildFile << """
             task copy(type: Copy) {
                 from "reference.txt"
                 into ("build/tmp")
                 fileMode = $mode
             }
            """
        when:
        run "copy"

        then:
        file("build/tmp/reference.txt").mode == mode

        when:
        run "copy"

        then:
        skipped(":copy")
        file("build/tmp/reference.txt").mode == mode

        when:
        file("reference.txt").text = "new"
        run "copy"

        then:
        executedAndNotSkipped(":copy")
        file("build/tmp/reference.txt").mode == mode

        where:
        mode << [0755, 0776]
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "file permissions can be modified with eachFile closure"() {
        given:
        def testSourceFile = file("reference.txt") << 'test file"'
        testSourceFile.mode = 0746
        and:
        buildFile << """
            task copy(type: Copy) {
                from "reference.txt"
                eachFile {
		            it.mode = 0755
	            }
                into ("build/tmp")
            }
            """
        when:
        run "copy"
        then:
        file("build/tmp/reference.txt").mode == 0755

        when:
        run "copy"
        then:
        skipped(":copy")
        file("build/tmp/reference.txt").mode == 0755

        when:
        testSourceFile.text = "new"
        run "copy"
        then:
        executedAndNotSkipped(":copy")
        file("build/tmp/reference.txt").mode == 0755
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "fileMode can be modified in copy action"() {
        given:
        file("reference.txt") << 'test file"'

        and:
        buildFile << """
            task copy {
                doLast {
                    copy {
                        from 'reference.txt'
                        into 'build/tmp'
                        fileMode = $mode
                    }
                }
            }
            """

        when:
        run "copy"

        then:
        file("build/tmp/reference.txt").mode == mode
        where:
        mode << [0755, 0776]

    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirMode can be modified in copy task"() {
        given:
        TestFile parent = getTestDirectory().createDir("testparent")
        TestFile child = parent.createDir("testchild")
        child.file("reference.txt") << "test file"

        child.mode = 0777
        and:
        buildFile << """
            task copy(type: Copy) {
                from "testparent"
                into ("build/tmp")
                dirMode = $mode
            }
            """
        when:
        run "copy"
        then:
        file("build/tmp/testchild").mode == mode

        when:
        run "copy"
        then:
        skipped(":copy")
        file("build/tmp/testchild").mode == mode

        when:
        parent.file("other/file.txt") << "test file"
        run "copy"
        then:
        executedAndNotSkipped(":copy")
        file("build/tmp/other").mode == mode

        where:
        mode << [0755, 0776]
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions only affect subfolders2"() {
        given:
        withSourceFiles("rwxrwxrwx")

        and:
        buildFile << """
            task copy(type:Copy) {
              destinationDir = file("dest")
              filePermissions { unix("rwxrwxrwx") }
              dirPermissions { unix("rwxrwxrwx") }
              from("files"){
                filePermissions { unix("rwxrwxrw-") }
                dirPermissions { unix("rwxrwxrw-") }
                into(""){
                  filePermissions { unix("rwxrwxr--") }
                  dirPermissions { unix("rwxrwxr--") }
                  from("files"){
                    filePermissions { unix("rwxrwx---") }
                    dirPermissions { unix("rwxrwx---") }
                    into("."){
                      filePermissions { unix("rwxrw----") }
                      dirPermissions { unix("rwxrw----") }
                      from("files"){
                        filePermissions { unix("rwxr-----") }
                        dirPermissions { unix("rwxr-----") }
                        into("../dest")
                      }
                    }
                  }
                }
              }
              dirPermissions { unix("---------") }
              duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
            """
        when:
        run "copy"
        then:
        assertDescendants()
        file("dest/c.txt").permissions == "rwxr-----"
        file("dest").permissions == "rwxr-----"
        file("dest/sub").permissions == "rwxr-----"
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions only affect subfolders"() {
        given:
        withSourceFiles("rwxrwxrwx")
        def topLevelPermissions = "rwxrwx---"

        and:
        buildFile << """
            task copy(type:Copy) {
              destinationDir = file("dest")
              from("files")
              dirPermissions { unix("${topLevelPermissions}") }
            }
            """
        when:
        run "copy"
        then:
        assertDescendants()
        file("dest").permissions == "rwxr-xr-x" // default
        file("dest/sub").permissions == topLevelPermissions
        file("dest/sub/dir").permissions == topLevelPermissions
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions only affect subfolders - spec nested in from1"() {
        given:
        withSourceFiles("rwxrwxrwx")
        def permissions = "rwxrwx---"
        def topLevelPermissions = "rwx------"

        and:
        buildFile << """
            task copy(type:Copy) {
              destinationDir = file("dest")
              from("files"){
                dirPermissions { unix("${permissions}") }
              }
              dirPermissions { unix("${topLevelPermissions}") }
            }
            """
        when:
        run "copy"
        then:
        assertDescendants()
        file("dest").permissions == "rwxr-xr-x" // default
        file("dest/sub").permissions == permissions
        file("dest/sub/dir").permissions == permissions
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions only affect subfolders - spec nested in from2"() {
        given:
        withSourceFiles("rwxrwxrwx")
        def permissions = "rwxrwx---"
        def fromPermissions = "rwx---rwx"
        def topLevelPermissions = "rwx------"

        and:
        buildFile << """
            task copy(type:Copy) {
              destinationDir = file("dest")
              from("files"){
                dirPermissions { unix("${fromPermissions}") }
                into("."){ // this creates a child spec and should be ignored
                  dirPermissions { unix("${permissions}") }
                }
              }
              dirPermissions { unix("${topLevelPermissions}") }
            }
            """
        when:
        run "copy"
        then:
        assertDescendants()
        file("dest").permissions == "rwxr-xr-x" // default
        file("dest/sub").permissions == fromPermissions
        file("dest/sub/dir").permissions == fromPermissions
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions only affect subfolders - spec nested in from3"() {
        given:
        withSourceFiles("rwxrwxrwx")
        def permissions = "rwxrwx---"
        def fromPermissions = "rwx---rwx"
        def topLevelPermissions = "rwx------"

        and:
        buildFile << """
            task copy(type:Copy) {
              destinationDir = file("dest")
              from("files"){
                dirPermissions { unix("${fromPermissions}") }
                into(""){ // this creates a child spec and should be ignored
                  dirPermissions { unix("${permissions}") }
                }
              }
              dirPermissions { unix("${topLevelPermissions}") }
            }
            """
        when:
        run "copy"
        then:
        assertDescendants()
        file("dest").permissions == "rwxr-xr-x" // default
        file("dest/sub").permissions == fromPermissions
        file("dest/sub/dir").permissions == fromPermissions
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions only affect subfolders - spec nested in from4"() {
        given:
        withSourceFiles("rwxrwxrwx")
        def permissions = "rwxrwx---"
        def fromPermissions = "rwx---rwx"
        def topLevelPermissions = "rwx------"

        and:
        buildFile << """
            task copy(type:Copy) {
              destinationDir = file("dest")
              from("files"){
                dirPermissions { unix("${fromPermissions}") }
                into("../dest"){
                  dirPermissions { unix("${permissions}") }
                }
              }
              dirPermissions { unix("${topLevelPermissions}") }
            }
            """
        when:
        run "copy"
        then:
        assertDescendants()
        file("dest").permissions == "rwxr-xr-x" // default
        file("dest/sub").permissions == fromPermissions
        file("dest/sub/dir").permissions == fromPermissions
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions only affect subfolders - spec nested in from with into"() {
        given:
        withSourceFiles("rwxrwxrwx")
        def permissions = "rwxrwx---"
        def topLevelPermissions = "rwx------"
        def fromPermissions = "rwx---rwx"

        and:
        buildFile << """
            task copy(type:Copy) {
              destinationDir = file("dest")
              from("files"){
                dirPermissions { unix("${fromPermissions}") }
                into("prefix"){ // this creates a child spec and should be ignored
                  dirPermissions { unix("${permissions}") }
                }
              }
              dirPermissions { unix("${topLevelPermissions}") }
            }
            """
        when:
        run "copy"
        then:
        assertDescendants()
        file("dest").permissions == "rwxr-xr-x" // default
        file("dest/sub").permissions == fromPermissions
        file("dest/sub/dir").permissions == fromPermissions
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions only affect subfolders - nested empty path spec1"() {
        given:
        withSourceFiles("rwxrwxrwx")
        def permissions = "rwxrwx---"
        def topLevelPermissions = "rwx------"

        and:
        buildFile << """
            task copy(type:Copy) {
              destinationDir = file("dest")
              into("") {
                from("files")
                dirPermissions { unix("${permissions}") }
              }
              dirPermissions { unix("${topLevelPermissions}") }
            }
            """
        when:
        run "copy"
        then:
        assertDescendants()
        file("dest").permissions == "rwxr-xr-x" // default
        file("dest/sub").permissions == permissions
        file("dest/sub/dir").permissions == permissions
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions only affect subfolders - nested empty path spec"() {
        given:
        withSourceFiles("rwxrwxrwx")
        def permissions = "rwxrwx---"
        def topLevelPermissions = "rwx------"

        and:
        buildFile << """
            task copy(type:Copy) {
              destinationDir = file("dest")
              into("") { into(".") { into("") { into(".") { into("") { into(".") { into("") { into(".") {
                from("files")
                dirPermissions { unix("${permissions}") }
              }}}}}}}}
              dirPermissions { unix("${topLevelPermissions}") }
            }
            """
        when:
        run "copy"
        then:
        assertDescendants()
        file("dest").permissions == "rwxr-xr-x" // default
        file("dest/sub").permissions == permissions
        file("dest/sub/dir").permissions == permissions
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions only affect subfolders - nested empty path spec2"() {
        given:
        withSourceFiles("rwxrwxrwx")
        def permissions = "rwxrwx---"
        def topLevelPermissions = "rwx------"

        and:
        buildFile << """
            task copy(type:Copy) {
              destinationDir = file("dest")
              into("prefix") {
                from("files")
                dirPermissions { unix("${permissions}") }
              }
              dirPermissions { unix("${topLevelPermissions}") }
            }
            """
        when:
        run "copy"
        then:
        assertDescendants("prefix/")
        file("dest").permissions == "rwxr-xr-x" // default, but expecting topLevelPermissions FIXME
        file("dest/prefix").permissions == permissions
        file("dest/prefix/sub").permissions == permissions
        file("dest/prefix/sub/dir").permissions == permissions
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions only affect subfolders - include pattern"() {
        given:
        withSourceFiles("rwxrwxrwx")
        def permissions = "rwxrwx---"
        def topLevelPermissions = "rwx------"

        and:
        buildFile << """
            task copy(type:Copy) {
              destinationDir = file("dest")
              from("."){
                dirPermissions { unix("${permissions}") }
                include("files/**")
              }
              dirPermissions { unix("${topLevelPermissions}") }
            }
            """
        when:
        run "copy"
        then:
        assertDescendants("files/")
        file("dest").permissions == "rwxr-xr-x" // default
        file("dest/files").permissions == permissions
        file("dest/files/sub").permissions == permissions
        file("dest/files/sub/dir").permissions == permissions
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions only affect subfolders - spec"() {
        given:
        withSourceFiles("rwxrwxrwx")
        def permissions = "rwxrwx---"
        def topLevelPermissions = "rwx------"

        and:
        buildFile << """
            CopySpec spec = copySpec {
                from("files")
                dirPermissions { unix("${permissions}") }
            }
            task copy(type:Copy) {
              destinationDir = file("dest")
              with spec
              dirPermissions { unix("${topLevelPermissions}") }
            }
            """
        when:
        run "copy"
        then:
        assertDescendants()
        file("dest").permissions == "rwxr-xr-x" // default
        file("dest/sub").permissions == permissions
        file("dest/sub/dir").permissions == permissions
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions can be modified for subpaths with single from"() {
        given:
        withSourceFiles("rwxrwxrwx")

        def mainDirPermissions = "rwxrwx---"
        def subfolderPermissions = "rwx---rwx"
        def subsubfolderPermissions = "rwx------"

        and:
        buildFile << """
            task copy(type:Copy) {
              destinationDir = file("dest")
              dirPermissions { unix("${mainDirPermissions}") }
              into("prefix/sub") {
                dirPermissions { unix("${subfolderPermissions}") }
                from ("files/sub") {
                  dirPermissions { unix("${subsubfolderPermissions}") }
                }
              }
            }
            """
        when:
        run "copy"
        then:
        file("dest").assertHasDescendants('prefix/sub/a.txt', 'prefix/sub/dir/b.txt', 'prefix/sub/empty')
        file("dest").permissions == "rwxr-xr-x" // default, but I'd expect mainDirPermissions FIXME
        file("dest/prefix").permissions == subfolderPermissions
        file("dest/prefix/sub").permissions == file("dest/prefix").permissions
        file("dest/prefix/sub/dir").permissions == subsubfolderPermissions
    }

    //TODO: cover legit nested "into" case

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions can be modified for subpaths"() { //TODO: how to edit permissions for dirs
        given:
        withSourceFiles("rwxrwxrwx")

        def mainDirPermissions = "rwxrwx---"
        def subfolderPermissions = "rwx---rwx"
        def subsubfolderPermissions = "rwx------"

        and:
        buildFile << """
            task copy(type: Copy) {
                into("dest")
                from("files"){
                    exclude("sub")
                }
                from("files/sub"){
                    exclude("dir")
                    into("sub")
                    dirPermissions {
                        unix("${subfolderPermissions}") //ignored for sub/dir because it is in unrelated spec
                    }
                }
                from("files/sub/dir"){
                    into("sub/dir")
                    dirPermissions {
                        unix("${subsubfolderPermissions}")
                    }
                }
                dirPermissions {
                  unix("${mainDirPermissions}") // ignored for dest
                }
            }
            """
        when:
        run "copy"
        then:
        assertDescendants()
        file("dest").permissions == "rwxr-xr-x"
        file("dest/sub").permissions == subfolderPermissions
        file("dest/sub/dir").permissions == subsubfolderPermissions
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions can be modified for subpaths2"() { //TODO: exdit permissions for dirs
        given:
        withSourceFiles("rwxrwxrwx")

        def mainDirPermissions = "rwxrwx---"
        def subfolderPermissions = "rwxrw----"
        def subfolder2Permissions = "rwxrw---x"
        def subsubfolderPermissions = "rwx------"

        and:
        buildFile << """
            task copy(type: Copy) {
                into("dest")
                from("files"){
                    dirPermissions {
                        unix("${subfolderPermissions}")
                    }
                    into("subfolder1")
                }
                from("files"){
                    dirPermissions {
                        unix("${subfolder2Permissions}")
                    }
                    into("subfolder2")
                }
                dirPermissions {
                  unix("${mainDirPermissions}")
                }
            }
            """
        when:
        run "copy"
        then:
        file("dest").permissions == "rwxr-xr-x"
        file("dest/subfolder1").permissions == subfolderPermissions
        file("dest/subfolder2").permissions == subfolder2Permissions

        file("dest/subfolder1/sub").permissions == subfolderPermissions
        file("dest/subfolder2/sub").permissions == subfolder2Permissions
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions can be modified for subpaths5"() { //TODO: exdit permissions for dirs
        given:
        withSourceFiles("rwxrwxrwx")

        def mainDirPermissions = "rwxrwx---"
        def subfolderPermissions = "rwxrw----"
        def subfolder2Permissions = "rwxrw---x"
        def subsubfolderPermissions = "rwx------"

        and:
        buildFile << """
            task copy(type: Copy) {
                into("dest")
                into("subfolder1"){
                    dirPermissions {
                        unix("${subfolderPermissions}")
                    }
                    from("files"){
                        dirPermissions {
                            unix("${subsubfolderPermissions}")
                        }
                    }
                }
                into("subfolder2"){
                    dirPermissions {
                        unix("${subfolder2Permissions}")
                    }
                    from("files"){
                        dirPermissions {
                            unix("${subsubfolderPermissions}")
                        }
                    }
                }
                dirPermissions {
                  unix("${mainDirPermissions}")
                }
            }
            """
        when:
        run "copy"
        then:
        file("dest").permissions == "rwxr-xr-x" // fixme: should be mainDirPermissions
        file("dest/subfolder1").permissions == subfolderPermissions
        file("dest/subfolder2").permissions == subfolder2Permissions

        file("dest/subfolder1/sub").permissions == subsubfolderPermissions
        file("dest/subfolder2/sub").permissions == subsubfolderPermissions
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "dirPermissions can be modified for subpaths6"() { //TODO: exdit permissions for dirs
        given:
        withSourceFiles("rwxrwxrwx")

        def topLevelPermissions = "rwxrwx---"
        def prefixPermissions = "rwx------"
        def filesPermissions = "rwx---rwx"

        and:
        buildFile << """
            task copy(type:Copy) {
              destinationDir = file("dest")
              into("prefix") {
                from("files"){
                   dirPermissions { unix("${filesPermissions}") }
                }
                dirPermissions { unix("${prefixPermissions}") }
              }
              dirPermissions { unix("${topLevelPermissions}") }
            }
            """
        when:
        run "copy"
        then:
        assertDescendants("prefix/")
        file("dest").permissions == "rwxr-xr-x" // fixme: should be topLevelPermissions
        file("dest/prefix").permissions == prefixPermissions
        file("dest/prefix/sub").permissions == filesPermissions
    }


    @Requires(UnitTestPreconditions.Windows)
    def "file permissions are not preserved on OS without permission support"() {
        given:
        def testSourceFile = file("reference.txt") << 'test file"'
        assertTrue testSourceFile.setReadOnly()
        and:
        buildFile << """
        task copy(type: Copy) {
            from "reference.txt"
            into ("build/tmp")
        }
        """
        when:
        withDebugLogging()
        run "copy"
        then:
        def testTargetFile = file("build/tmp/reference.txt")
        testTargetFile.exists()
        testTargetFile.canWrite()
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    @Issue('https://github.com/gradle/gradle/issues/2639')
    def "excluded files' permissions should be ignored"() {
        given:
        getTestDirectory().createFile('src/unauthorized/file')
        getTestDirectory().createFile('src/authorized')
        getTestDirectory().createDir('dest')
        file('src/unauthorized').mode = 0000

        and:
        buildFile << """
            task copy(type: Copy) {
                from('src'){
                    exclude 'unauthorized'
                }
                into 'dest'
            }
            """

        when:
        run "copy"

        then:
        file('dest').assertHasDescendants('authorized')

        cleanup:
        file('src/unauthorized').mode = 0777
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    @Issue('https://github.com/gradle/gradle/issues/9576')
    def "unreadable #type not produced by task fails"() {
        given:
        def input = file("readableFile.txt").createFile()

        def outputDirectory = file("output")
        def unreadableOutput = file("${outputDirectory.name}/unreadable${type.capitalize()}")
        create(unreadableOutput).makeUnreadable()

        buildFile << """
            task copy(type: Copy) {
                from '${input.name}'
                into '${outputDirectory.name}'
            }
        """

        when:
        executer.withStackTraceChecksDisabled()
        runAndFail "copy"
        then:
        expectUnreadableCopyDestinationFailure()
        failureHasCause(expectedError(unreadableOutput))

        cleanup:
        unreadableOutput.makeReadable()

        where:
        type        | create              | expectedError
        'file'      | { it.createFile() } | { "Failed to create MD5 hash for file '${it.absolutePath}' as it does not exist." }
        'directory' | { it.createDir() }  | { "java.nio.file.AccessDeniedException: ${it.absolutePath}" }
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    @Issue('https://github.com/gradle/gradle/issues/9576')
    def "can copy into destination directory with unreadable file when using doNotTrackState"() {
        given:
        def input = file("readableFile.txt").createFile()

        def outputDirectory = file("output")
        def unreadableOutput = file("${outputDirectory.name}/unreadableFile")
        unreadableOutput.createFile().makeUnreadable()

        buildFile << """
            task copy(type: Copy) {
                from '${input.name}'
                into '${outputDirectory.name}'
                doNotTrackState("Destination contains unreadable files")
            }
        """

        when:
        run "copy"
        then:
        executedAndNotSkipped(":copy")
        outputDirectory.list().contains input.name

        when:
        run "copy"
        then:
        executedAndNotSkipped(":copy")
        outputDirectory.list().contains input.name

        cleanup:
        unreadableOutput.makeReadable()
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "permissions block overrides mode"() {
        given:
        withSourceFiles("r--------")
        buildScript '''
            task (copy, type:Copy) {
               from 'files'
               into 'dest'
               eachFile {
                    mode = 0777
                    permissions {}
               }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        assertDestinationFilePermissions("rw-r--r--")
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "permissions block sets sensible defaults"() {
        given:
        withSourceFiles("r--------")
        buildScript '''
            task (copy, type:Copy) {
               from 'files'
               into 'dest'
               eachFile {
                    permissions {}
               }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        assertDestinationFilePermissions("rw-r--r--")
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "permissions block can customize permissions (Groovy DSL)"() {
        given:
        withSourceFiles("r--------")
        buildScript '''
            task (copy, type:Copy) {
               from 'files'
               into 'dest'
               eachFile {
                    permissions {
                        user {
                            write = false
                        }
                        user.execute = true
                        group.execute = true
                        other {
                            write = true
                        }
                    }
               }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        assertDestinationFilePermissions("r-xr-xrw-")
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "permissions block can customize permissions (Kotlin DSL)"() {
        given:
        withSourceFiles("r--------")

        buildFile.delete()
        buildKotlinFile.text = '''
            tasks.register<Copy>("copy") {
               from("files")
               into("dest")
               eachFile {
                    permissions {
                        user {
                            write = false
                        }
                        user.execute = true
                        group.execute = true
                        other {
                            write = true
                        }
                    }
               }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        assertDestinationFilePermissions("r-xr-xrw-")
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "permissions can be created via factory (#description)"(String description, String setting) {
        given:
        withSourceFiles("r--------")
        buildScript """
            def p = project.services.get(FileSystemOperations).directoryPermissions {
                user {
                    write = false
                }
                user.execute = false
                group.write = false
                other {
                    execute = false
                }
            }

            task (copy, type:Copy) {
               from 'files'
               into 'dest'
               ${setting}
            }
        """.stripIndent()

        when:
        run 'copy'

        then:
        assertDestinationFilePermissions("r-xr-xrw-")

        where:
        description        | setting
        "permissions"      | """
                                eachFile {
                                    permissions = p
                                }
                              """
        "file mode"        | "fileMode = p.toUnixNumeric()"
        "file permissions" | "filePermissions.set(p)"
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "permissions are set correctly for intermediate directories"() {
        given:
        withSourceFiles("r--------")

        buildFile.delete()
        buildKotlinFile.text = '''
            tasks.register<Copy>("copy") {
               into("dest")
               into("prefix1/prefix2") {
                 from("files")
                 dirPermissions {
                   unix("rwx---rwx")
                 }
               }
               dirPermissions {
                  unix("rwx------")
               }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file("dest").permissions == "rwxr-xr-x" // default 755, but should be "rwx------" FIXME
        file("dest/prefix1").permissions == "rwx---rwx"
        file("dest/prefix1/prefix2").permissions == "rwx---rwx"
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "permissions are preserved for intermediate directories when target exists"() {
        given:
        withSourceFiles("r--------")
        def dest = getTestDirectory().createDir('dest')
        def originalPermissions = "rwxrwxrwx"
        dest.setPermissions(originalPermissions)

        def prefix = getTestDirectory().createDir('dest/prefix')
        def prefixOriginalPermissions = "rwxrwx---"
        prefix.setPermissions(prefixOriginalPermissions)

        buildFile.delete()
        buildKotlinFile.text = '''
            tasks.register<Copy>("copy") {
               into("dest/prefix")
               from("files")
               dirPermissions {
                  unix("rwx------")
               }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file("dest").permissions == originalPermissions
        file("dest/prefix").permissions == prefixOriginalPermissions
    }

    private def withSourceFiles(String permissions) {
        file("files/sub/a.txt").createFile().setPermissions(permissions)
        file("files/sub/dir/b.txt").createFile().setPermissions(permissions)
        file("files/c.txt").createFile().setPermissions(permissions)
        file("files/sub/empty").createDir().setPermissions(permissions)
    }

    private def assertDescendants(String prefix = "") {
        file('dest').assertHasDescendants(
            "${prefix}sub/a.txt",
            "${prefix}sub/dir/b.txt",
            "${prefix}c.txt",
            "${prefix}sub/empty"
        )
    }

    private def assertDestinationFilePermissions(String permissions) {
        assertDescendants()
        file("dest/sub/a.txt").permissions == permissions
        file("dest/sub/dir/b.txt").permissions == permissions
        file("dest/c.txt").permissions == permissions
        file("dest/sub/empty").permissions == "r--------" // eachFile doesn't cover directories
    }
}
