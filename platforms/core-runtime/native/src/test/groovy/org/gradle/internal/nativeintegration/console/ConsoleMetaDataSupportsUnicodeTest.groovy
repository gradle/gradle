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

class ConsoleMetaDataSupportsUnicodeTest extends Specification {
    private ProcessEnvironment env = NativeServicesTestFixture.getInstance().get(ProcessEnvironment)
    private Map<String, String> originalEnvVars = [:]
    private TestConsoleMetaData consoleMetaData = new TestConsoleMetaData()

    def setup() {
        // Save original environment variables
        ['LANG', 'LC_ALL', 'TERM', 'WT_SESSION', 'WT_PROFILE_ID', 'ConEmuPID'].each { varName ->
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
        ['LANG', 'LC_ALL', 'TERM', 'WT_SESSION', 'WT_PROFILE_ID', 'ConEmuPID'].each { varName ->
            env.removeEnvironmentVariable(varName)
        }
    }

    def "returns true when LANG contains UTF-8"() {
        when:
        env.setEnvironmentVariable('LANG', 'en_US.UTF-8')

        then:
        consoleMetaData.supportsUnicode()
    }

    def "returns true when LANG contains utf-8 in lowercase"() {
        when:
        env.setEnvironmentVariable('LANG', 'en_US.utf-8')

        then:
        consoleMetaData.supportsUnicode()
    }

    def "returns true when LC_ALL contains UTF-8"() {
        when:
        env.setEnvironmentVariable('LC_ALL', 'en_US.UTF-8')

        then:
        consoleMetaData.supportsUnicode()
    }

    def "returns true when LC_ALL contains utf-8 in mixed case"() {
        when:
        env.setEnvironmentVariable('LC_ALL', 'en_GB.Utf-8')

        then:
        consoleMetaData.supportsUnicode()
    }

    def "returns true when both LANG and LC_ALL contain UTF-8"() {
        when:
        env.setEnvironmentVariable('LANG', 'en_US.UTF-8')
        env.setEnvironmentVariable('LC_ALL', 'C.UTF-8')

        then:
        consoleMetaData.supportsUnicode()
    }


    def "returns true for #terminal terminal type"() {
        when:
        env.setEnvironmentVariable('TERM', terminal)

        then:
        consoleMetaData.supportsUnicode()

        where:

        terminal << [
           'xterm',
           'xterm-256color',
           'screen',
           'screen-256color',
           'tmux',
           'tmux-256color',
           'rxvt-unicode',
           'konsole',
           'gnome-256color',
           'alacritty',
           'kitty',
           'ghostty',
           'wezterm',
           'contour',
           'foot',
           'mlterm',
           'st-256color',
           'qterminal',
           'weston',
        ]
    }

    def "returns false for #terminal terminal type"() {
        when:
        env.setEnvironmentVariable('TERM', terminal)

        then:
        !consoleMetaData.supportsUnicode()

        where:

        terminal << [
            'dumb',
            'unknown',
            'vt100',
        ]
    }

    def "returns true when WT_SESSION is set"() {
        when:
        env.setEnvironmentVariable('WT_SESSION', 'some-session-id')

        then:
        consoleMetaData.supportsUnicode()
    }

    def "returns true when WT_PROFILE_ID is set"() {
        when:
        env.setEnvironmentVariable('WT_PROFILE_ID', 'some-profile-id')

        then:
        consoleMetaData.supportsUnicode()
    }

    def "returns true when ConEmuPID is set"() {
        when:
        env.setEnvironmentVariable('ConEmuPID', '12345')

        then:
        consoleMetaData.supportsUnicode()
    }

    def "returns false when no relevant environment variables are set"() {
        expect:
        !consoleMetaData.supportsUnicode()
    }

    def "returns false when LANG is set without UTF-8"() {
        when:
        env.setEnvironmentVariable('LANG', 'en_US.ISO-8859-1')

        then:
        !consoleMetaData.supportsUnicode()
    }

    def "LANG UTF-8 takes precedence over unsupported TERM (#terminal)"() {
        when:
        env.setEnvironmentVariable('LANG', 'en_US.UTF-8')
        env.setEnvironmentVariable('TERM', terminal)

        then:
        consoleMetaData.supportsUnicode()

        where:
        terminal << [
            'dumb',
            'unknown',
            'vt100',
        ]
    }

    def "LC_ALL UTF-8 takes precedence over unsupported TERM"() {
        when:
        env.setEnvironmentVariable('LC_ALL', 'en_US.UTF-8')
        env.setEnvironmentVariable('TERM', 'dumb')

        then:
        consoleMetaData.supportsUnicode()
    }

    def "supported TERM overrides non-UTF-8 LANG"() {
        when:
        env.setEnvironmentVariable('LANG', 'C')
        env.setEnvironmentVariable('TERM', 'xterm-256color')

        then:
        consoleMetaData.supportsUnicode()
    }

    def "dumb TERM returns false even with Windows Terminal indicator"() {
        when:
        env.setEnvironmentVariable('TERM', 'dumb')
        env.setEnvironmentVariable('WT_SESSION', 'abc123')

        then:
        !consoleMetaData.supportsUnicode()
    }

    def "unknown TERM returns false even with Windows Terminal indicator"() {
        when:
        env.setEnvironmentVariable('TERM', terminal)
        env.setEnvironmentVariable('WT_SESSION', 'abc123')

        then:
        !consoleMetaData.supportsUnicode()

        where:
        terminal << [
            'dumb',
            'unknown'
        ]
    }

    def "Windows Terminal indicator works without TERM"() {
        when:
        env.setEnvironmentVariable('WT_SESSION', 'abc123')

        then:
        consoleMetaData.supportsUnicode()
    }

    def "ConEmu indicator works with non-UTF-8 locale"() {
        when:
        env.setEnvironmentVariable('LANG', 'C')
        env.setEnvironmentVariable('ConEmuPID', '9876')

        then:
        consoleMetaData.supportsUnicode()
    }

    def "dumb TERM returns false even with ConEmu indicator"() {
        when:
        env.setEnvironmentVariable('TERM', 'dumb')
        env.setEnvironmentVariable('ConEmuPID', '12345')

        then:
        !consoleMetaData.supportsUnicode()
    }

    def "case insensitive matching for terminal types"() {
        when:
        env.setEnvironmentVariable('TERM', 'XTERM-256COLOR')

        then:
        consoleMetaData.supportsUnicode()
    }

    def "partial matches work for terminal types"() {
        when:
        env.setEnvironmentVariable('TERM', 'my-custom-xterm-terminal')

        then:
        consoleMetaData.supportsUnicode()
    }

    def "returns true for terminals with 256color suffix"() {
        when:
        env.setEnvironmentVariable('TERM', 'anything-256color')

        then:
        consoleMetaData.supportsUnicode()
    }

    def "empty LANG value does not trigger UTF-8 detection"() {
        when:
        env.setEnvironmentVariable('LANG', '')

        then:
        !consoleMetaData.supportsUnicode()
    }

    def "empty TERM value does not trigger terminal detection"() {
        when:
        env.setEnvironmentVariable('TERM', '')

        then:
        !consoleMetaData.supportsUnicode()
    }

    def "LANG with UTF8 without dash is not detected"() {
        when:
        env.setEnvironmentVariable('LANG', 'en_US.UTF8')

        then:
        consoleMetaData.supportsUnicode()
    }

    // Test implementation class that uses default supportsUnicode method
    private static class TestConsoleMetaData implements ConsoleMetaData {
        @Override
        boolean isStdOutATerminal() {
            return false
        }

        @Override
        boolean isStdErrATerminal() {
            return false
        }

        @Override
        int getCols() {
            return 0
        }

        @Override
        int getRows() {
            return 0
        }

        @Override
        boolean isWrapStreams() {
            return false
        }
    }
}

