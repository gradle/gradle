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

import org.gradle.internal.environment.GradleBuildEnvironment
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class SmokeContinuousIntegrationTest extends Java7RequiringContinuousIntegrationTest {

    def "basic smoke test"() {
        when:
        file("marker").text = "original"

        buildFile << """
            task echo {
              inputs.files file("marker")
              doLast {
                println "value: " + file("marker").text
              }
            }
        """

        then:
        succeeds("echo")
        output.contains "Continuous build is an incubating feature."
        output.contains "value: original"

        when:
        file("marker").text = "changed"

        then:
        succeeds()
        output.contains "value: changed"
    }

    def "can recover from build failure"() {
        when:
        executer.withStackTraceChecksDisabled()
        buildFile << """
            task build {
              def f = file("marker")
              inputs.files f
              doLast {
                if (f.file) {
                  println "value: " + f.text
                } else {
                  throw new Exception("file does not exist")
                }
              }
            }
        """
        def markerFile = file("marker") << "original"

        then:
        succeeds "build"
        output.contains "value: original"

        when:
        markerFile.delete()

        then:
        fails()
        errorOutput.contains "file does not exist"

        when:
        markerFile << "changed"

        then:
        succeeds()
        output.contains "value: changed"
    }

    def "does not trigger when changes is made to task that is not required"() {
        when:
        buildFile << """
            task a {
              inputs.file "a"
              doLast {}
            }
            task b {
              inputs.file "b"
              doLast {}
            }
        """

        then:
        succeeds("a")
        ":a" in executedTasks

        when:
        file("a") << "original"

        then:
        succeeds()
        ":a" in executedTasks

        and:
        succeeds("b")
        ":b" in executedTasks

        when:
        file("a").text = "changed"

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

    def "reuses build script classes"() {
        when:
        file("marker").text = "original"

        buildFile << """
            task echo {
              inputs.files file("marker")
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
        file("marker").text = "changed"

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
                doLast {}
            }
        """

        then:
        fails("a")
        failureDescriptionContains("Could not determine the dependencies of task ':a'.")
    }

    def "ignores non source when source is empty"() {
        when:
        buildScript """
            task build {
              inputs.source fileTree("source")
              inputs.files fileTree("ancillary")
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

    def "project directory can be used as input"() {
        given:
        buildFile << """
        task a {
            inputs.dir projectDir
            doLast {}
        }
        """

        expect:
        succeeds("a")
        executedAndNotSkipped(":a")

        when:
        file("A").text = "A"

        then:
        succeeds()
        executedAndNotSkipped(":a")

        when: "file is changed"
        file("A").text = "B"

        then:
        succeeds()
        executedAndNotSkipped(":a")

        when:
        file("A").delete()

        then:
        succeeds()
        executedAndNotSkipped(":a")
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "exit hint does not mention enter when not on windows"() {
        when:
        buildScript "task a { inputs.file 'a'; doLast {} }"

        then:
        succeeds "a"
        output.endsWith("(ctrl-d to exit)\n")
    }

    @Requires(TestPrecondition.WINDOWS)
    def "exit hint mentions enter when on windows"() {
        when:
        buildScript "task a { inputs.file 'a'; doLast {} }"

        then:
        succeeds "a"
        output.endsWith("(ctrl-d then enter to exit)\r\n")
    }

}
