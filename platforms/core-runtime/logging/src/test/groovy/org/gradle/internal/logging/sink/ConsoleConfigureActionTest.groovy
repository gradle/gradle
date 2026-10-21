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

package org.gradle.internal.logging.sink

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import spock.lang.Specification

class ConsoleConfigureActionTest extends Specification {

    def "reports program status for #consoleOutput console when stdout terminal is #stdoutTerminal and property is #property"() {
        given:
        def metaData = Stub(ConsoleMetaData) {
            isStdOutATerminal() >> stdoutTerminal
            isStdErrATerminal() >> true
        }

        expect:
        ConsoleConfigureAction.shouldReportProgramStatus(consoleOutput, metaData, property) == expected

        where:
        consoleOutput          | stdoutTerminal | property | expected
        ConsoleOutput.Auto     | true           | null     | true
        ConsoleOutput.Rich     | true           | null     | true
        ConsoleOutput.Verbose  | true           | null     | true
        ConsoleOutput.Rich     | true           | "true"   | true
        ConsoleOutput.Plain    | true           | null     | false
        ConsoleOutput.Colored  | true           | null     | false
        ConsoleOutput.Auto     | false          | null     | false
        ConsoleOutput.Rich     | false          | null     | false
        ConsoleOutput.Verbose  | false          | null     | false
        ConsoleOutput.Auto     | true           | "false"  | false
        ConsoleOutput.Rich     | true           | "false"  | false
    }
}
