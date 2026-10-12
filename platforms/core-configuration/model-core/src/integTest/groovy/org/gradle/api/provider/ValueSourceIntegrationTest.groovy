/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.process.ShellScript
import spock.lang.Issue

class ValueSourceIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/38399")
    def "value source can start an external process at configuration time with the process API"() {
        given:
        ShellScript testScript = ShellScript.builder().printText("Hello, world").writeTo(testDirectory, "script")

        buildFile """
            import ${ByteArrayOutputStream.name}
            import org.gradle.api.provider.*

            abstract class ProcessSource implements ValueSource<String, ValueSourceParameters.None> {
                @Override String obtain() {
                    def baos = new ByteArrayOutputStream()
                    def process = ${ShellScript.cmdToStringLiteral(testScript.getRelativeCommandLine(testDirectory))}.execute()
                    process.waitForProcessOutput(baos, System.err)
                    return baos.toString().trim()
                }
            }

            def vsResult = providers.of(ProcessSource) {}
            println("ValueSource result = \${vsResult.get()}")

            task empty() {}
        """

        when:
        run(":empty")

        then:
        outputContains("ValueSource result = Hello, world")
    }

    @Issue("https://github.com/gradle/gradle/issues/38399")
    def "value source can start an external process at configuration time with injected ExecOperations"() {
        given:
        ShellScript testScript = ShellScript.builder().printText("Hello, world").writeTo(testDirectory, "script")

        buildFile """
            import ${ByteArrayOutputStream.name}
            import org.gradle.api.provider.*
            import org.gradle.process.ExecOperations
            import javax.inject.Inject

            abstract class ProcessSource implements ValueSource<String, ValueSourceParameters.None> {
                @Inject abstract ExecOperations getExecOperations()

                @Override String obtain() {
                    def baos = new ByteArrayOutputStream()
                    getExecOperations().exec { spec ->
                        spec.commandLine(${ShellScript.cmdToVarargLiterals(testScript.commandLine)})
                        spec.standardOutput = baos
                    }
                    return baos.toString().trim()
                }
            }

            def vsResult = providers.of(ProcessSource) {}
            println("ValueSource result = \${vsResult.get()}")

            task empty() {}
        """

        when:
        run(":empty")

        then:
        outputContains("ValueSource result = Hello, world")
    }
}
