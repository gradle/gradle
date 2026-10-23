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

package org.gradle.internal.nativeintegration.console

import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/gradle/gradle/issues/39562")
class ConsoleMetaDataSupportsEighthBlocksTest extends Specification {
    private ProcessEnvironment env = NativeServicesTestFixture.getInstance().get(ProcessEnvironment)
    private String originalTerm = System.getenv('TERM')

    def cleanup() {
        setTerm(originalTerm)
    }

    private void setTerm(String term) {
        if (term != null) {
            env.setEnvironmentVariable('TERM', term)
        } else {
            env.removeEnvironmentVariable('TERM')
        }
    }

    def "the font #has eighth blocks when TERM is #termName"() {
        when:
        setTerm(term)

        then:
        FallbackConsoleMetaData.ATTACHED.supportsEighthBlocks() == supported

        where:
        term             | supported
        "linux"          | false
        "linux-16color"  | false
        // GNU screen on the Linux console, with the screen.linux terminfo entry
        "screen.linux"   | false
        "screen.linux-s" | false
        "xterm-256color" | true
        // GNU screen without the screen.linux terminfo entry, and tmux, also on the Linux console
        "screen"         | true
        "tmux-256color"  | true
        null             | true
        termName = term ?: "unset"
        has = supported ? "has" : "has no"
    }
}
