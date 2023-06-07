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
        mode << [0746, 0746]
        testFileName << ["reference.txt", "\u0627\u0644\u0627\u0655\u062F\u0627\u0631\u0629.txt"]
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
        description         | setting
        "permissions"       | """
                                eachFile {
                                    permissions = p
                                }
                              """
        "file mode"         | "fileMode = p.toUnixNumeric()"
        "file permissions"  | "filePermissions.set(p)"
    }

    private def withSourceFiles(String permissions) {
        file("files/sub/a.txt").createFile().setPermissions(permissions)
        file("files/sub/dir/b.txt").createFile().setPermissions(permissions)
        file("files/c.txt").createFile().setPermissions(permissions)
        file("files/sub/empty").createDir().setPermissions(permissions)
    }

    private def assertDestinationFilePermissions(String permissions) {
        file('dest').assertHasDescendants(
            'sub/a.txt',
            'sub/dir/b.txt',
            'c.txt',
            'sub/empty'
        )
        file("dest/sub/a.txt").permissions == permissions
        file("dest/sub/dir/b.txt").permissions == permissions
        file("dest/c.txt").permissions == permissions
        file("dest/sub/empty").permissions == "r--------" // eachFile doesn't cover directories
    }
}
