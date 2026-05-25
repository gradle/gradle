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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.process.ShellScript

class SetCommandLineDeprecationIntegrationTest extends AbstractIntegrationSpec {

    private static final String SET_COMMAND_LINE_DEPRECATION = "The ExecSpec.setCommandLine() method has been deprecated. " +
        "This is scheduled to be removed in Gradle 10. Use commandLine() instead. " +
        "Consult the upgrading guide for further information: " +
        "https://docs.gradle.org/current/userguide/upgrading_version_9.html#set-command-line"

    def "Exec task setCommandLine emits deprecation warning"() {
        given:
        def script = ShellScript.builder().printText("hi").writeTo(testDirectory, "script")
        def commandLine = ShellScript.cmdToVarargLiterals(script.commandLine)
        buildFile << """
            tasks.register('runIt', Exec) {
                setCommandLine($commandLine)
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning(SET_COMMAND_LINE_DEPRECATION)
        succeeds("runIt")
    }

    def "Exec task commandLine= property assignment emits deprecation warning"() {
        given:
        def script = ShellScript.builder().printText("hi").writeTo(testDirectory, "script")
        def commandLine = ShellScript.cmdToVarargLiterals(script.commandLine)
        buildFile << """
            tasks.register('runIt', Exec) {
                commandLine = [$commandLine]
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning(SET_COMMAND_LINE_DEPRECATION)
        succeeds("runIt")
    }

    def "Exec task commandLine() does not emit deprecation warning"() {
        given:
        def script = ShellScript.builder().printText("hi").writeTo(testDirectory, "script")
        def commandLine = ShellScript.cmdToVarargLiterals(script.commandLine)
        buildFile << """
            tasks.register('runIt', Exec) {
                commandLine($commandLine)
            }
        """

        expect:
        succeeds("runIt")
    }
}
