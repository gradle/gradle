/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class AntBuilderLoggingIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.requireIsolatedDaemons()
        buildFile """
            ant.saveStreams = false
            task antTest {
                doLast {
                    ant.echo(message: "VERBOSE message", level: "verbose")
                    ant.echo(message: "DEBUG message", level: "debug")
                    ant.echo(message: "INFO message", level: "info")
                    ant.echo(message: "WARN message", level: "warn")
                    ant.echo(message: "ERROR message", level: "error")
                }
            }
        """
    }

    def "logs normally without lifecycle log level set" () {
        when:
        succeeds("antTest")

        then:
        outputContains("[ant:echo] WARN message")
        result.assertHasErrorOutput("[ant:echo] ERROR message")

        and:
        outputDoesNotContain("INFO message")
        outputDoesNotContain("DEBUG message")
        outputDoesNotContain("VERBOSE message")
    }

    def "can increase verbosity of Ant logging" () {
        buildFile << """
            ant.lifecycleLogLevel = "INFO"
        """

        when:
        succeeds("antTest")

        then:
        outputContains("[ant:echo] INFO message")
        outputContains("[ant:echo] WARN message")
        result.assertHasErrorOutput("[ant:echo] ERROR message")

        and:
        outputDoesNotContain("DEBUG message")
        outputDoesNotContain("VERBOSE message")
    }

    def "can decrease verbosity of Ant logging" () {
        buildFile << """
            ant.lifecycleLogLevel = "ERROR"
        """

        when:
        succeeds("antTest")

        then:
        result.assertHasErrorOutput("[ant:echo] ERROR message")

        and:
        result.assertNotOutput("WARN message")
        result.assertNotOutput("INFO message")
        result.assertNotOutput("DEBUG message")
        result.assertNotOutput("VERBOSE message")
    }
}
