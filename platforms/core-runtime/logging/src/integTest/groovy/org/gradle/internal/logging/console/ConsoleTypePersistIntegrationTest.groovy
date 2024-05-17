/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.console

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ConsoleTypePersistIntegrationTest extends AbstractIntegrationSpec {
    def "--console can be persisted in gradle.properties"() {

        given:
        buildFile << """
            def consoleOutput = gradle.startParameter.consoleOutput
            def expected = providers.gradleProperty("expected")
            println "Console is " + consoleOutput
            assert consoleOutput.toString() == expected.get()
        """

        when:
        succeeds('help', "-Pexpected=Auto")
        then:
        assertDoesNotHaveAnsiEscapeSequence()

        when:
        file('gradle.properties') << 'org.gradle.console=rich'
        succeeds('help', "-Pexpected=Rich")
        then:
        assertHasAnsiEscapeSequence()

        when:
        // command-line wins over gradle.properties
        succeeds('help', "--console=plain", "-Pexpected=Plain")
        then:
        assertDoesNotHaveAnsiEscapeSequence()
    }

    void assertHasAnsiEscapeSequence() {
        assert output.contains(ansiEscapeSequence)
    }

    void assertDoesNotHaveAnsiEscapeSequence() {
        assert !output.contains(ansiEscapeSequence)
    }

    String getAnsiEscapeSequence() {
        '[1m'
    }
}
