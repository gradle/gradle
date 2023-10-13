/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.environment.GradleBuildEnvironment
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Ignore
import spock.lang.Issue

class SmokeContinuousIntegrationTest extends AbstractContinuousIntegrationTest {

    def "detects no changes when no files are in the project"() {
        given:
        def markerFile = file("input/marker")
        buildFile << """
            task myTask {
              def inputFile = file("input/marker")
              inputs.files inputFile
              outputs.files "build/marker"
              doLast {
                println "exists: " + inputFile.exists()
              }
            }
        """

        when:
        succeeds("myTask")
        then:
        output.contains "exists: false"

        when:
        waitBeforeModification(markerFile)
        markerFile.text = "created"
        then:
        // There are different reason why this doesn't work for hierarchical and non-hierarchical watchers.
        // We may want to support this use-case at some point.
        // For hierarchical watchers, we don't watch the project directory if the VFS only contains missing files in there.
        // For non-hierarchical watchers, we watch the parent directory of the missing file, the project directory in this case.
        // When the `input` directory is created, we invalidate the VFS and stop watching since the missing snapshot now has been removed.
        // Though we don't consider the path `input` as an input to the build, since it is a parent of the declared input
        // `input/marker`. So we don't trigger a rebuild.
        exitsContinuousBuildSinceNotWatchingAnyLocationsExceptForConfigCache()
    }

    def "does not detect changes when no snapshotting happens (#description)"() {
        given:
        def markerFile = file("input/marker")
        buildFile << """
            task myTask {
              def inputFile = file("input/marker")
              inputs.files inputFile
              doLast {
                println "value: " + inputFile.text
              }
            }
        """

        when:
        markerFile.text = "original"
        succeeds("myTask")
        then:
        output.contains "value: original"

        when:
        waitBeforeModification(markerFile)
        markerFile.text = "changed"
        then:
        // If we don't snapshot anything, then we are also not watching anything,
        // since we only watch things in the VFS.
        // This is a limitation of the current implementation.
        exitsContinuousBuildSinceNotWatchingAnyLocationsExceptForConfigCache()

        where:
        description      | taskConfiguration
        "no outputs"     | ""
        "untracked task" | "outputs.file('output/marker'); doNotTrackState('for test')"
    }

    def "basic smoke test"() {
        given:
        def markerFile = file("input/marker")

        when:
        markerFile.text = "original"

        buildFile << """
            task myTask {
              def inputFile = file("input/marker")
              inputs.files inputFile
              outputs.files "build/marker"
              doLast {
                println "value: " + inputFile.text
              }
            }
        """

        then:
        succeeds("myTask")
        output.contains "value: original"

        when:
        waitBeforeModification(markerFile)
        markerFile.text = "changed"

        then:
        buildTriggeredAndSucceeded()
        output.contains "value: changed"
    }

    def "detects changes in filtered file tree inputs"() {
        def sources = file("sources").createDir()
        def excludedFile = sources.file("sub/some/excluded").createFile()
        def includedFile = sources.file("sub/some/included.txt").createFile()

        buildFile << taskOnlyIncludingTxtFiles

        when:
        succeeds("myTask")
        then:
        outputContains("includedFiles: 1")

        when:
        includedFile.text = "changed"
        then:
        buildTriggeredAndSucceeded()
        outputContains("includedFiles: 1")

        when:
        excludedFile.text = "changed"
        then:
        noBuildTriggered()

        when:
        sources.file("sub/some/otherIncluded.txt").createFile()
        then:
        buildTriggeredAndSucceeded()
        outputContains("includedFiles: 2")

        when:
        sources.file("sub/other/included.txt").createFile()
        then:
        if (OperatingSystem.current().linux) {
            // On Linux, we don't watch sub, since it is a filtered directory,
            // and it doesn't contain any file or unfiltered directory
            noBuildTriggered()
        } else {
            buildTriggeredAndSucceeded()
            outputContains("includedFiles: 3")
        }
    }

    def "does not detect changes in filtered file tree inputs when first matching file is created"() {
        def sources = file("sources").createDir()
        sources.file("sub/some/excluded").createFile()
        def includedFile = sources.file("sub/some/included.txt")

        buildFile << taskOnlyIncludingTxtFiles

        when:
        succeeds("myTask")
        then:
        outputContains("includedFiles: 0")

        when:
        includedFile.text = "created"
        then:
        exitsContinuousBuildSinceNotWatchingAnyLocationsExceptForConfigCache()
    }

    String taskOnlyIncludingTxtFiles = """
            task myTask {
              def inputFileTree = fileTree("sources").include("**/*.txt")
              inputs.files(inputFileTree)
                .ignoreEmptyDirectories()
                .withPropertyName("sources")
              outputs.files "build/marker"
              doLast {
                println "includedFiles: " + inputFileTree.files.size()
              }
            }
        """

    def "notifications work with quiet logging"() {
        given:
        def markerFile = file("input/marker")

        when:
        markerFile.text = "original"

        buildFile << """
            task myTask {
              def inputFile = file("input/marker")
              inputs.files inputFile
              outputs.files "build/marker"
              doLast {
                println "value: " + inputFile.text
              }
            }
        """

        then:
        executer.withArgument("-q")
        succeeds("myTask")
        output.contains "value: original"

        when:
        waitBeforeModification(markerFile)
        markerFile.text = "changed"

        then:
        buildTriggeredAndSucceeded()
        output.contains "value: changed"
    }

    def "can recover from build failure"() {
        given:
        def markerFile = file("input/marker")
        file("inputFile").createFile()

        when:
        buildFile << """
            task myTask {
              def f = file("input/marker")
              inputs.files f
              inputs.files "inputFile"
              outputs.files "build/marker"
              doLast {
                if (f.file) {
                  println "value: " + f.text
                } else {
                  throw new Exception("file does not exist")
                }
              }
            }
        """
        markerFile << "original"

        then:
        succeeds "myTask"
        output.contains "value: original"

        when:
        waitBeforeModification(markerFile)
        markerFile.delete()

        then:
        buildTriggeredAndFailed()
        failure.assertHasCause "java.lang.Exception: file does not exist"

        when:
        waitBeforeModification(markerFile)
        markerFile << "changed"

        then:
        buildTriggeredAndSucceeded()
        output.contains "value: changed"
    }

    def "does not trigger when changes is made to task that is not required"() {
        given:
        def aFile = file("a").touch()
        def bFile = file("b").touch()

        when:
        buildFile << """
            task a {
              inputs.file "a"
              outputs.files "build/marker"
              doLast {}
            }
            task b {
              inputs.file "b"
              outputs.files "build/marker"
              doLast {}
            }
        """

        then:
        succeeds("a")
        executed(":a")

        when:
        aFile << "original"

        then:
        buildTriggeredAndSucceeded()
        executed(":a")

        and:
        succeeds("b")
        executed(":b")

        when:
        waitBeforeModification(aFile)
        aFile.text = "changed"

        then:
        noBuildTriggered()
    }

    def "exits when build fails with compile error"() {
        when:
        buildFile << """
            'script error
        """

        then:
        fails("a")
        exitContinuousBuildSinceNoDeclaredInputs()
    }

    def "exits when build fails with configuration error"() {
        when:
        buildFile << """
            throw new Exception("!")
        """

        then:
        fails("a")
        exitContinuousBuildSinceNoDeclaredInputs()
    }

    def "exits when no executed tasks have file system inputs"() {
        when:
        buildFile << """
            task a
        """

        then:
        succeeds("a")
        exitContinuousBuildSinceNoDeclaredInputs()
    }

    def "reuses build script classes"() {
        given:
        def markerFile = file("input/marker")

        when:
        markerFile.text = "original"

        buildFile << """
            task myTask {
              def inputFile = file("input/marker")
              inputs.files inputFile
              outputs.files "build/marker"
              doLast {
                println "value: " + inputFile.text
                println "reuse: " + Reuse.initialized
                Reuse.initialized = true
              }
            }
            class Reuse {
                public static Boolean initialized = false
            }
        """

        then:
        succeeds("myTask")
        output.contains "value: original"
        output.contains "reuse: false"

        when:
        waitBeforeModification(markerFile)
        markerFile.text = "changed"

        then:
        buildTriggeredAndSucceeded()
        output.contains "value: changed"
        output.contains "reuse: true"

    }

    def "considered to be long lived process"() {
        when:
        buildFile << """
            task myTask {
              doLast {
                println "isLongLivingProcess: " + services.get($GradleBuildEnvironment.name).isLongLivingProcess()
              }
            }
        """

        then:
        succeeds("myTask")
        output.contains "isLongLivingProcess: true"
    }

    def "failure to determine inputs has a reasonable message"() {
        when:
        buildScript """
            task a {
                inputs.files files({ throw new Exception("boom") })
                outputs.files "build/marker"
                doLast {}
            }
        """

        then:
        fails("a")
        failureDescriptionContains("Could not determine the dependencies of task ':a'.")
    }

    def "failure to determine inputs has a reasonable message when an earlier task succeeds"() {
        when:
        buildScript """
            task a {
                inputs.files file("inputA")
                outputs.files "build/outputA"
                doLast {}
            }
            task b {
                inputs.files files({ throw new Exception("boom") })
                outputs.files "build/outputB"
                dependsOn a
                doLast {}
            }
        """

        then:
        fails("b")
        failureDescriptionContains("Could not determine the dependencies of task ':b'.")
    }

    def "failure to determine inputs cancels build and has a reasonable message after initial success"() {
        when:
        def bFlag = file("bFlag")
        file("inputA").createFile()
        buildScript """
            task a {
                inputs.files file("inputA")
                outputs.files "build/outputA"
                doLast {}
            }
            task b {
                def bFlag = file("bFlag")
                inputs.files files({
                    if (!bFlag.exists()) {
                        return bFlag
                    }

                    throw new Exception("boom")
                })
                outputs.files "build/outputB"
                dependsOn a

                doLast { }
            }
        """

        then:
        succeeds("b")

        when:
        bFlag.text = "b executed"
        then:
        buildTriggeredAndFailed()
        failureDescriptionContains("Could not determine the dependencies of task ':b'.")
    }

    def "ignores non source when source is empty"() {
        when:
        file("source").createDir()
        buildScript """
            task myTask {
              inputs.files(fileTree("source"))
                .skipWhenEmpty()
                .ignoreEmptyDirectories()
              inputs.files fileTree("ancillary")
              outputs.files "build/output"
              doLast {}
            }
        """

        then:
        succeeds("myTask")

        when:
        file("ancillary/test.txt") << "foo"

        then:
        noBuildTriggered()

        when:
        file("source/test.txt") << "foo"

        then:
        buildTriggeredAndSucceeded()

        when:
        file("ancillary/test.txt") << "-bar"

        then:
        buildTriggeredAndSucceeded()
    }

    @Ignore("This goes into a continuous loop since .gradle files change")
    def "project directory can be used as input"() {
        given:
        def aFile = file("A")
        buildFile << """
        task before {
            def outputFile = new File(buildDir, "output.txt")
            outputs.file outputFile
            outputs.upToDateWhen { false }

            doLast {
                outputFile.parentFile.mkdirs()
                outputFile.text = "OK"
            }
        }

        task a {
            dependsOn before
            inputs.dir projectDir
            outputs.files "build/output"
            doLast {}
        }
        """

        expect:
        succeeds("a")
        executedAndNotSkipped(":a")

        when:
        aFile.text = "A"

        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped(":a")

        when: "file is changed"
        waitBeforeModification(aFile)
        aFile.text = "B"

        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped(":a")

        when:
        waitBeforeModification(aFile)
        aFile.delete()

        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped(":a")
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "exit hint does not mention enter when not on windows"() {
        when:
        file("a").touch()
        buildScript "task a { inputs.file 'a'; outputs.file 'b'; doLast {} }"

        then:
        succeeds "a"
        output.contains("Waiting for changes to input files... (ctrl-d to exit)\n")
    }

    @Requires(UnitTestPreconditions.Windows)
    def "exit hint mentions enter when on windows"() {
        when:
        file("a").touch()
        buildScript """
            task a {
                inputs.file 'a'
                outputs.file 'build/b'
                doLast {}
            }
        """

        then:
        succeeds "a"
        output.endsWith("(ctrl-d then enter to exit)\n")
    }

    @Issue("GRADLE-3415")
    def "watches for changes when some task has a single input file in the parent directory of another task's input directory"() {
        given:
        def topLevelFile = file("src/topLevel.txt").createFile()
        def nestedFile = file("src/subdirectory/nested.txt").createFile()
        buildFile << """
        task inner {
            inputs.file "src/topLevel.txt"
            outputs.file "build/inner.txt"
            doLast {}
        }

        task outer {
            dependsOn inner
            inputs.dir "src"
            outputs.file "build/outer.txt"
            doLast {}
        }
        """

        expect:
        succeeds("outer")
        executedAndNotSkipped(":inner", ":outer")

        when:
        waitBeforeModification(topLevelFile)
        topLevelFile.text = "hello"

        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped(":inner", ":outer")

        when: "file is changed"
        waitBeforeModification(nestedFile)
        nestedFile.text = "B"

        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped(":outer")
    }

    def "fails when using continuous build and watching is disabled"() {
        buildFile << """
            task myTask
        """

        expect:
        fails("myTask", "--no-watch-fs")
        failureDescriptionContains("Continuous build does not work when file system watching is disabled")
    }

    private void exitContinuousBuildSinceNoDeclaredInputs() {
        assert !gradle.running
        assert output.contains("Exiting continuous build as Gradle did not detect any file system inputs.")
    }

    private void exitsContinuousBuildSinceNotWatchingAnyLocationsExceptForConfigCache() {
        if (GradleContextualExecuter.configCache) {
            // When using the configuration cache, the build files are stored in the VFS, so we start watching.
            // That means for hierarchical watchers, we'll also detect the change above.
            // For Linux though, the reason why we don't detect the change still apply.
            if (OperatingSystem.current().linux) {
                noBuildTriggered()
            } else {
                buildTriggeredAndSucceeded()
            }
        } else {
            exitsContinuousBuildSinceNotWatchingAnyLocations()
        }
    }

    private void exitsContinuousBuildSinceNotWatchingAnyLocations() {
        assert !gradle.running
        assert output.contains("Exiting continuous build as Gradle does not watch any file system locations.")
    }
}
