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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

@IgnoreIf({ GradleContextualExecuter.daemon })
class AntBuilderLoggingIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
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
        result.output.contains("[ant:echo] WARN message")
        result.error.contains("[ant:echo] ERROR message")

        and:
        ! result.output.contains("[ant:echo] INFO message")
        ! result.output.contains("[ant:echo] DEBUG message")
        ! result.output.contains("[ant:echo] VERBOSE message")
    }

    def "can increase verbosity of Ant logging" () {
        buildFile << """
            ant.lifecycleLogLevel = "INFO"
        """

        when:
        succeeds("antTest")

        then:
        result.output.contains("[ant:echo] INFO message")
        result.output.contains("[ant:echo] WARN message")
        result.error.contains("[ant:echo] ERROR message")

        and:
        ! result.output.contains("[ant:echo] DEBUG message")
        ! result.output.contains("[ant:echo] VERBOSE message")
    }

    def "can decrease verbosity of Ant logging" () {
        buildFile << """
            ant.lifecycleLogLevel = "ERROR"
        """

        when:
        succeeds("antTest")

        then:
        result.error.contains("[ant:echo] ERROR message")

        and:
        ! result.output.contains("[ant:echo] INFO message")
        ! result.output.contains("[ant:echo] WARN message")
        ! result.output.contains("[ant:echo] DEBUG message")
        ! result.output.contains("[ant:echo] VERBOSE message")
    }
}
