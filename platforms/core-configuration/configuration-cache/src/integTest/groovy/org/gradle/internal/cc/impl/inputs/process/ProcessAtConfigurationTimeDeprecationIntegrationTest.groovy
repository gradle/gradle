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

package org.gradle.internal.cc.impl.inputs.process

import org.gradle.internal.cc.impl.AbstractConfigurationCacheIntegrationTest
import org.gradle.internal.cc.impl.fixtures.ExternalProcessAtConfigurationTimeDeprecations
import org.gradle.process.ShellScript
import org.gradle.process.TestJavaMain
import org.gradle.util.internal.TextUtil
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/38399")
class ProcessAtConfigurationTimeDeprecationIntegrationTest extends AbstractConfigurationCacheIntegrationTest implements ExternalProcessAtConfigurationTimeDeprecations {

    def "starting an external process at configuration time via #trigger is deprecated when the configuration cache is off"() {
        given:
        def script = ShellScript.builder().printText("Hello from script").writeTo(testDirectory, "script")
        def commandLine = ShellScript.cmdToVarargLiterals(script.commandLine)

        buildFile """
            abstract class Ops {
                @Inject abstract ExecOperations getExecOps()

                String[] command = [$commandLine]

                void execOperationsExec() {
                    execOps.exec { commandLine(command) }
                }
                void execOperationsJavaexec() {
                    execOps.javaexec {
                        mainClass.set("${TestJavaMain.class.name}")
                        classpath("${TextUtil.escapeString(TestJavaMain.classLocation)}")
                        args("Hello", "from", "Java")
                    }
                }
                void processBuilderStart() {
                    new ProcessBuilder(command).start().waitFor()
                }
                void processBuilderStartPipeline() {
                    ProcessBuilder.startPipeline([new ProcessBuilder(command)]).each { it.waitFor() }
                }
                void runtimeExec() {
                    Runtime.runtime.exec(command).waitFor()
                }
                void runtimeExecEnvpOverload() {
                    Runtime.runtime.exec(command, null).waitFor()
                }
                void stringArrayExecute() {
                    command.execute().waitFor()
                }
                void listExecute() {
                    command.toList().execute().waitFor()
                }
            }

            objects.newInstance(Ops).${trigger}()

            tasks.register("noop") {}
        """

        and:
        expectExternalProcessAtConfigurationTimeDeprecation()

        expect:
        run("noop")

        where:
        trigger << [
            "execOperationsExec",
            "execOperationsJavaexec",
            "processBuilderStart",
            "processBuilderStartPipeline",
            "runtimeExec",
            "runtimeExecEnvpOverload",
            "stringArrayExecute",
            "listExecute",
        ]
    }

    def "starting an external process at configuration time is deprecated when the configuration cache is explicitly disabled"() {
        given:
        def script = ShellScript.builder().printText("Hello from script").writeTo(testDirectory, "script")
        def commandLine = ShellScript.cmdToVarargLiterals(script.commandLine)

        buildFile """
            String[] command = [$commandLine]
            new ProcessBuilder(command).start().waitFor()

            tasks.register("noop") {}
        """

        and:
        expectExternalProcessAtConfigurationTimeDeprecation()

        expect:
        run("noop", DISABLE_SYS_PROP)
    }
}
