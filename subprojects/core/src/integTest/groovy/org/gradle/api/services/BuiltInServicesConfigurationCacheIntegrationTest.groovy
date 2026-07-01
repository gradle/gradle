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

package org.gradle.api.services

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.process.ShellScript

class BuiltInServicesConfigurationCacheIntegrationTest extends AbstractIntegrationSpec {

    def "a directly-exposed execOperations captured at configuration time is used in a task action across configuration cache invocations"() {
        given:
        def commandLine = echoCommandLine("hello-direct")

        buildFile << """
            def execOps = execOperations

            tasks.register("run") {
                doLast {
                    execOps.exec { it.commandLine($commandLine) }
                }
            }
        """

        when: "the configuration cache entry is stored"
        succeeds("run")

        then:
        outputContains("hello-direct")

        when: "the configuration cache entry is reused"
        succeeds("run")

        then:
        outputContains("hello-direct")
    }

    def "a service obtained via builtInServices.get captured at configuration time is used in a task action across configuration cache invocations"() {
        given:
        def commandLine = echoCommandLine("hello-escape-hatch")

        buildFile << """
            def execOps = builtInServices.get(org.gradle.process.ExecOperations)

            tasks.register("run") {
                doLast {
                    execOps.exec { it.commandLine($commandLine) }
                }
            }
        """

        when: "the configuration cache entry is stored"
        succeeds("run")

        then:
        outputContains("hello-escape-hatch")

        when: "the configuration cache entry is reused"
        succeeds("run")

        then:
        outputContains("hello-escape-hatch")
    }

    private String echoCommandLine(String text) {
        def script = ShellScript.builder().printText(text).writeTo(testDirectory, "echo-${text}")
        return ShellScript.cmdToVarargLiterals(script.commandLine)
    }
}
