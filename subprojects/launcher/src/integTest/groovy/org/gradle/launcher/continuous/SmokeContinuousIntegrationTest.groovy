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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.environment.GradleBuildEnvironment
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Ignore
import spock.lang.Issue

class SmokeContinuousIntegrationTest extends AbstractContinuousIntegrationTest {
    def setup() {
        if (OperatingSystem.current().isWindows()) {
            ignoreShutdownTimeoutException = true
        }
    }

    @ToBeFixedForConfigurationCache
    def "basic smoke test"() {
        given:
        def markerFile = file("marker")

        when:
        markerFile.text = "original"

        buildFile << """
            task echo {
              inputs.files "marker"
              outputs.files "build/marker"
              doLast {
                println "value: " + file("marker").text
              }
            }
        """

        then:
        succeeds("echo")
        output.contains "value: original"

        when:
        waitBeforeModification(markerFile)
        markerFile.text = "changed"

        then:
        succeeds()
        output.contains "value: changed"
    }

    @ToBeFixedForConfigurationCache
    def "notifications work with quiet logging"() {
        given:
        def markerFile = file("marker")

        when:
        markerFile.text = "original"

        buildFile << """
            task echo {
              inputs.files "marker"
              outputs.files "build/marker"
              doLast {
                println "value: " + file("marker").text
              }
            }
        """

        then:
        executer.withArgument("-q")
        succeeds("echo")
        output.contains "value: original"

        when:
        waitBeforeModification(markerFile)
        markerFile.text = "changed"

        then:
        succeeds()
        output.contains "value: changed"
    }

    def "can recover from build failure"() {
        given:
        def markerFile = file("marker")
        file("inputFile").createFile()

        when:
        buildFile << """
            task build {
              def f = file("marker")
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
        succeeds "build"
        output.contains "value: original"

        when:
        waitBeforeModification(markerFile)
        markerFile.delete()

        then:
        fails()
        failure.assertHasCause "java.lang.Exception: file does not exist"

        when:
        waitBeforeModification(markerFile)
        markerFile << "changed"

        then:
        succeeds()
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
        succeeds()
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
        !gradle.running
        output.contains("Exiting continuous build as no executed tasks declared file system inputs.")
    }

    def "exits when build fails with configuration error"() {
        when:
        buildFile << """
            throw new Exception("!")
        """

        then:
        fails("a")
        !gradle.running
        output.contains("Exiting continuous build as no executed tasks declared file system inputs.")
    }

    def "exits when no executed tasks have file system inputs"() {
        when:
        buildFile << """
            task a
        """

        then:
        succeeds("a")
        !gradle.running
        output.contains("Exiting continuous build as no executed tasks declared file system inputs.")
    }

    @ToBeFixedForConfigurationCache
    def "reuses build script classes"() {
        given:
        def markerFile = file("marker")

        when:
        markerFile.text = "original"

        buildFile << """
            task echo {
              inputs.files file("marker")
              outputs.files "build/marker"
              doLast {
                println "value: " + file("marker").text
                println "reuse: " + Reuse.initialized
                Reuse.initialized = true
              }
            }
            class Reuse {
                public static Boolean initialized = false
            }
        """

        then:
        succeeds("echo")
        output.contains "value: original"
        output.contains "reuse: false"

        when:
        waitBeforeModification(markerFile)
        markerFile.text = "changed"

        then:
        succeeds()
        output.contains "value: changed"
        output.contains "reuse: true"

    }

    def "considered to be long lived process"() {
        when:
        buildFile << """
            task echo {
              doLast {
                println "isLongLivingProcess: " + services.get($GradleBuildEnvironment.name).isLongLivingProcess()
              }
            }
        """

        then:
        succeeds("echo")
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

    @ToBeFixedForConfigurationCache
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
        fails()
        failureDescriptionContains("Could not determine the dependencies of task ':b'.")
    }

    def "ignores non source when source is empty"() {
        when:
        file("source").createDir()
        buildScript """
            task build {
              inputs.files(fileTree("source"))
                .skipWhenEmpty()
                .ignoreEmptyDirectories()
              inputs.files fileTree("ancillary")
              outputs.files "build/output"
              doLast {}
            }
        """

        then:
        succeeds("build")

        when:
        file("ancillary/test.txt") << "foo"

        then:
        noBuildTriggered()

        when:
        file("source/test.txt") << "foo"

        then:
        succeeds()

        when:
        file("ancillary/test.txt") << "-bar"

        then:
        succeeds()
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
        succeeds()
        executedAndNotSkipped(":a")

        when: "file is changed"
        waitBeforeModification(aFile)
        aFile.text = "B"

        then:
        succeeds()
        executedAndNotSkipped(":a")

        when:
        waitBeforeModification(aFile)
        aFile.delete()

        then:
        succeeds()
        executedAndNotSkipped(":a")
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "exit hint does not mention enter when not on windows"() {
        when:
        file("a").touch()
        buildScript "task a { inputs.file 'a'; outputs.file 'b'; doLast {} }"

        then:
        succeeds "a"
        output.endsWith("(ctrl-d to exit)\n")
    }

    @Requires(TestPrecondition.WINDOWS)
    def "exit hint mentions enter when on windows"() {
        when:
        file("a").touch()
        buildScript "task a { inputs.file 'a'; doLast {} }"

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
        succeeds()
        executedAndNotSkipped(":inner", ":outer")

        when: "file is changed"
        waitBeforeModification(nestedFile)
        nestedFile.text = "B"

        then:
        succeeds()
        executedAndNotSkipped(":outer")
    }

}
