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

class SmokeContinuousIntegrationTest extends AbstractContinuousIntegrationTest {

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
        output.contains "Continuous mode is an incubating feature."
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

    def "exits when build fails before any tasks execute"() {
        when:

        buildFile << """
            task a {
              doLast { }
            }

            'script error
        """

        then:
        fails("a")
        !(":a" in executedTasks)
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

}
