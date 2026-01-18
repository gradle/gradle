/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.nativeintegration.console

import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import spock.lang.Specification

class ConsoleMetaDataSupportsTaskbarProgressTest extends Specification {
    private ProcessEnvironment env = NativeServicesTestFixture.getInstance().get(ProcessEnvironment)
    private Map<String, String> originalEnvVars = [:]

    def setup() {
        // Save original environment variables
        ['ConEmuPID', 'TERM', 'TERM_PROGRAM', 'TERM_PROGRAM_VERSION'].each { varName ->
            originalEnvVars[varName] = System.getenv(varName)
        }
        // Clear all relevant environment variables for clean testing
        clearAllRelevantEnvVars()
    }

    def cleanup() {
        // Restore original environment variables
        originalEnvVars.each { key, value ->
            if (value != null) {
                env.setEnvironmentVariable(key, value)
            } else {
                env.removeEnvironmentVariable(key)
            }
        }
    }

    private void clearAllRelevantEnvVars() {
        ['ConEmuPID', 'TERM', 'TERM_PROGRAM', 'TERM_PROGRAM_VERSION'].each { varName ->
            env.removeEnvironmentVariable(varName)
        }
    }

    def "returns true when ConEmuPID is set to any value"() {
        when:
        env.setEnvironmentVariable('ConEmuPID', pid)

        then:
        ConsoleMetaData.evaluateTaskBarProgressSupport()

        where:
        pid << ['1', '99999', 'abc', '']
    }

    def "returns true when TERM contains ghostty in different cases"() {
        when:
        env.setEnvironmentVariable('TERM', termValue)

        then:
        ConsoleMetaData.evaluateTaskBarProgressSupport()

        where:
        termValue << ['ghostty', 'GHOSTTY', 'Ghostty', 'ghostty-256color', 'xterm-ghostty']
    }

    def "returns true when TERM contains kitty in different cases"() {
        when:
        env.setEnvironmentVariable('TERM', termValue)

        then:
        ConsoleMetaData.evaluateTaskBarProgressSupport()

        where:
        termValue << ['kitty', 'KITTY', 'Kitty', 'xterm-kitty', 'kitty-direct']
    }

    def "returns false when TERM does not contain ghostty"() {
        when:
        env.setEnvironmentVariable('TERM', termValue)

        then:
        !ConsoleMetaData.evaluateTaskBarProgressSupport()

        where:
        termValue << ['xterm', 'xterm-256color', 'dumb', 'screen']
    }

    def "returns true for iTerm.app with versions >= 3.6.6"() {
        when:
        env.setEnvironmentVariable('TERM_PROGRAM', 'iTerm.app')
        env.setEnvironmentVariable('TERM_PROGRAM_VERSION', version)

        then:
        ConsoleMetaData.evaluateTaskBarProgressSupport()

        where:
        version << [
            '3.6.6',
            '3.6.7',
            '3.6.10',
            '3.7.0',
            '3.8.0',
            '3.10.0',
            '4.0.0',
            '4.1.0',
            '10.0.0'
        ]
    }

    def "returns false for iTerm.app with versions < 3.6.6"() {
        when:
        env.setEnvironmentVariable('TERM_PROGRAM', 'iTerm.app')
        env.setEnvironmentVariable('TERM_PROGRAM_VERSION', version)

        then:
        !ConsoleMetaData.evaluateTaskBarProgressSupport()

        where:
        version << [
            '3.6.5',
            '3.6.4',
            '3.6.0',
            '3.5.14',
            '3.5.0',
            '3.4.0',
            '3.0.0',
            '2.9.0',
            '1.0.0'
        ]
    }

    def "returns false for iTerm.app with missing version"() {
        when:
        env.setEnvironmentVariable('TERM_PROGRAM', 'iTerm.app')

        then:
        !ConsoleMetaData.evaluateTaskBarProgressSupport()
    }

    def "returns false for iTerm.app with empty version"() {
        when:
        env.setEnvironmentVariable('TERM_PROGRAM', 'iTerm.app')
        env.setEnvironmentVariable('TERM_PROGRAM_VERSION', '')

        then:
        !ConsoleMetaData.evaluateTaskBarProgressSupport()
    }

    def "returns false for iTerm.app with invalid version format"() {
        when:
        env.setEnvironmentVariable('TERM_PROGRAM', 'iTerm.app')
        env.setEnvironmentVariable('TERM_PROGRAM_VERSION', version)

        then:
        !ConsoleMetaData.evaluateTaskBarProgressSupport()

        where:
        version << ['invalid', 'a.b.c', 'not-a-version', '...']
    }

    def "returns false for different TERM_PROGRAM values"() {
        when:
        env.setEnvironmentVariable('TERM_PROGRAM', program)
        env.setEnvironmentVariable('TERM_PROGRAM_VERSION', '3.6.6')

        then:
        !ConsoleMetaData.evaluateTaskBarProgressSupport()

        where:
        program << ['Terminal.app', 'vscode', 'Hyper', 'Alacritty', 'not-iterm']
    }

    def "returns false when no environment variables are set"() {
        expect:
        !ConsoleMetaData.evaluateTaskBarProgressSupport()
    }

    def "ConEmuPID takes precedence over iTerm version check"() {
        when:
        env.setEnvironmentVariable('ConEmuPID', '12345')
        env.setEnvironmentVariable('TERM_PROGRAM', 'iTerm.app')
        env.setEnvironmentVariable('TERM_PROGRAM_VERSION', '1.0.0')

        then:
        ConsoleMetaData.evaluateTaskBarProgressSupport()
    }

    def "ConEmuPID takes precedence over TERM check"() {
        when:
        env.setEnvironmentVariable('ConEmuPID', '12345')
        env.setEnvironmentVariable('TERM', 'xterm')

        then:
        ConsoleMetaData.evaluateTaskBarProgressSupport()
    }

    def "ghostty TERM takes precedence over unsupported iTerm version"() {
        when:
        env.setEnvironmentVariable('TERM', 'ghostty')
        env.setEnvironmentVariable('TERM_PROGRAM', 'iTerm.app')
        env.setEnvironmentVariable('TERM_PROGRAM_VERSION', '3.0.0')

        then:
        ConsoleMetaData.evaluateTaskBarProgressSupport()
    }

    def "handles iTerm version with two components"() {
        when:
        env.setEnvironmentVariable('TERM_PROGRAM', 'iTerm.app')
        env.setEnvironmentVariable('TERM_PROGRAM_VERSION', version)

        then:
        result == ConsoleMetaData.evaluateTaskBarProgressSupport()

        where:
        version | result
        '3.7'   | true
        '3.6'   | false  // 3.6 < 3.6.6 because patch defaults to 0
        '4.0'   | true
        '2.9'   | false
    }
}
