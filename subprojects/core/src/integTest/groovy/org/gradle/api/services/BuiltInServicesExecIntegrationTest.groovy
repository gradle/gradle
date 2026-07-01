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
import org.gradle.process.ExecOperations
import org.gradle.process.ShellScript

class BuiltInServicesExecIntegrationTest extends AbstractIntegrationSpec {

    // Tier 1: the direct getter on the host, in the style of getObjects()/getProviders().

    def "project exposes execOperations directly and runs a process in a task action"() {
        given:
        def commandLine = echoCommandLine("hello-project")

        buildFile << """
            tasks.register("run") {
                def execOps = execOperations
                doLast {
                    execOps.exec { it.commandLine($commandLine) }
                }
            }
        """

        when:
        succeeds("run")

        then:
        outputContains("hello-project")
    }

    def "settings exposes execOperations directly"() {
        given:
        def commandLine = echoCommandLine("hello-settings")

        settingsFile << """
            execOperations.exec { it.commandLine($commandLine) }
        """
        buildFile << "tasks.register('run')"

        when:
        succeeds("run")

        then:
        outputContains("hello-settings")
    }

    def "gradle exposes execOperations directly in an init script"() {
        given:
        def commandLine = echoCommandLine("hello-init")

        file("init.gradle") << """
            gradle.execOperations.exec { it.commandLine($commandLine) }
        """
        executer.usingInitScript(file("init.gradle"))
        buildFile << "tasks.register('run')"

        when:
        succeeds("run")

        then:
        outputContains("hello-init")
    }

    // Tier 2: builtInServices.get/find, the universal escape hatch over all public services.

    def "builtInServices.get resolves a public service"() {
        given:
        def commandLine = echoCommandLine("hello-get")

        buildFile << """
            tasks.register("run") {
                def execOps = builtInServices.get(${ExecOperations.name})
                doLast {
                    execOps.exec { it.commandLine($commandLine) }
                }
            }
        """

        when:
        succeeds("run")

        then:
        outputContains("hello-get")
    }

    def "builtInServices.find resolves a public service"() {
        given:
        def commandLine = echoCommandLine("hello-find")

        buildFile << """
            tasks.register("run") {
                def execOps = builtInServices.find(${ExecOperations.name})
                doLast {
                    execOps.exec { it.commandLine($commandLine) }
                }
            }
        """

        when:
        succeeds("run")

        then:
        outputContains("hello-find")
    }

    def "builtInServices.get rejects an internal service"() {
        buildFile << """
            builtInServices.get(org.gradle.internal.service.ServiceRegistry)
        """

        when:
        fails("help")

        then:
        failureCauseContains("only public Gradle services are available")
    }

    def "builtInServices.get on a public service not available in this scope fails with a helpful message"() {
        given:
        // WorkerExecutor is Project-scoped, so it is not available at settings scope.
        settingsFile << """
            builtInServices.get(org.gradle.workers.WorkerExecutor)
        """

        when:
        fails("help")

        then:
        failureCauseContains("is not available in this scope")
    }

    private String echoCommandLine(String text) {
        def script = ShellScript.builder().printText(text).writeTo(testDirectory, "echo-${text}")
        return ShellScript.cmdToVarargLiterals(script.commandLine)
    }
}
