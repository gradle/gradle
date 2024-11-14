/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.watch

import org.gradle.testdistribution.LocalOnly
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

@LocalOnly
@Requires(UnitTestPreconditions.Symlinks)
class SymlinkFileSystemWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest {
    def setup() {
        // The daemon manages the cleanup of symlinks in the VFS between builds
        executer.requireDaemon()
    }

    @Issue("https://github.com/gradle/gradle/issues/11851")
    def "gracefully handle when declaring the same path as an input via symlinks"() {
        def actualDir = file("actualDir").createDir()
        file("symlink1").createLink(actualDir)
        file("symlink2").createLink(actualDir)

        buildFile << """
            task myTask {
                def outputFile = file("build/output.txt")
                inputs.dir("symlink1")
                inputs.dir("symlink2")
                outputs.file(outputFile)

                doLast {
                    outputFile.text = "Hello world"
                }
            }
        """

        when:
        withWatchFs().run "myTask"
        then:
        executedAndNotSkipped(":myTask")

        when:
        withWatchFs().run "myTask"
        then:
        skipped(":myTask")
    }

    @Issue("https://github.com/gradle/gradle/issues/11851")
    def "changes to #description are detected"() {
        file(fileToChange).createFile()
        file(linkSource).createLink(file(linkTarget))

        buildFile << """
            task myTask {
                def outputFile = file("build/output.txt")
                inputs.${inputDeclaration}
                outputs.file(outputFile)

                doLast {
                    outputFile.text = "Hello world"
                }
            }
        """

        when:
        withWatchFs().run "myTask"
        then:
        executedAndNotSkipped ":myTask"

        when:
        withWatchFs().run "myTask"
        then:
        skipped(":myTask")

        when:
        file(fileToChange).text = "changed"
        waitForChangesToBePickedUp()
        withWatchFs().run "myTask"
        then:
        executedAndNotSkipped ":myTask"

        where:
        description                     | linkSource                     | linkTarget       | inputDeclaration        | fileToChange
        "symlinked file"                | "symlinkedFile"                | "actualFile"     | 'file("symlinkedFile")' | "actualFile"
        "symlinked directory"           | "symlinkedDir"                 | "actualDir"      | 'dir("symlinkedDir")'   | "actualDir/file.txt"
        "symlink in a directory"        | "dirWithSymlink/symlinkInside" | "fileInside.txt" | 'dir("dirWithSymlink")' | "fileInside.txt"
    }

    @Issue("https://github.com/gradle/gradle/issues/26201")
    def "changes are detected when parent of input is symlinked"() {
        def targetDir = file("target")
        def inputFile = targetDir.file("input.txt")
        def linkedDir = file("linked-dir").createLink(targetDir)

        inputFile.text = "original"

        buildFile << """
            task myTask {
                def outputFile = file("build/output.txt")
                inputs.file("linked-dir/input.txt")
                outputs.file(outputFile)

                doLast {
                    outputFile.text = "Hello world"
                }
            }
        """

        when:
        withWatchFs().run "myTask"
        then:
        executedAndNotSkipped ":myTask"

        when:
        withWatchFs().run "myTask"
        then:
        skipped(":myTask")

        when:
        inputFile.text = "changed"
        waitForChangesToBePickedUp()
        withWatchFs().run "myTask"
        then:
        executedAndNotSkipped ":myTask"
    }

    def "file system watching works when the project dir is symlinked"() {
        def actualProjectDir = file("parent/projectDir")
        def symlink = file("symlinkedParent")
        symlink.createLink(file("parent"))

        def fileToChange = actualProjectDir.file("actualFile")
        fileToChange.createFile()

        actualProjectDir.file("build.gradle") << """
            task myTask {
                def outputFile = file("build/output.txt")
                inputs.file("actualFile")
                outputs.file(outputFile)

                doLast {
                    outputFile.text = "Hello world"
                }
            }
        """
        actualProjectDir.file("settings.gradle").createFile()
        executer.beforeExecute {
            // Use `new File` here to avoid canonicalization of the path
            def symlinkedProjectDir = new File(symlink, "projectDir")
            assert symlinkedProjectDir.absolutePath != actualProjectDir.absolutePath
            inDirectory(symlinkedProjectDir)
        }

        when:
        withWatchFs().run "myTask"
        then:
        executedAndNotSkipped ":myTask"

        when:
        withWatchFs().run "myTask"
        then:
        skipped(":myTask")

        when:
        file(fileToChange).text = "changed"
        waitForChangesToBePickedUp()
        withWatchFs().run "myTask"
        then:
        executedAndNotSkipped ":myTask"
    }
}
