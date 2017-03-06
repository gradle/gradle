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

import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import spock.lang.Specification

class ConsoleLayoutCalculatorTest extends Specification {
    def consoleMetaData = Mock(ConsoleMetaData)

    def "lines should be org.gradle.workers.max value if console is large enough"() {
        given:
        System.setProperty("org.gradle.workers.max", "5")
        1 * consoleMetaData.getRows() >> 100

        expect:
        ConsoleLayoutCalculator.calculateNumWorkersForConsoleDisplay(consoleMetaData) == 5

        cleanup:
        System.clearProperty("org.gradle.workers.max")
    }

    def "lines should be available processors if no property set and console is large enough"() {
        given:
        1 * consoleMetaData.getRows() >> 100

        expect:
        ConsoleLayoutCalculator.calculateNumWorkersForConsoleDisplay(consoleMetaData) == Runtime.runtime.availableProcessors()
    }

    def "lines should be half the console size for small consoles"() {
        given:
        1 * consoleMetaData.getRows() >> 4

        expect:
        ConsoleLayoutCalculator.calculateNumWorkersForConsoleDisplay(consoleMetaData) == 2
    }
}
