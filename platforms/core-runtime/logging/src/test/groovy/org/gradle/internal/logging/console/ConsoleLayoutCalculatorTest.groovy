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
    def consoleLayoutCalculator = new ConsoleLayoutCalculator(consoleMetaData);

    def "lines should be ideal value if console is large enough"() {
        given:
        1 * consoleMetaData.getRows() >> 100

        expect:
        consoleLayoutCalculator.calculateNumWorkersForConsoleDisplay(5) == 5
    }

    def "lines should be half the console size for small consoles"() {
        given:
        1 * consoleMetaData.getRows() >> rows

        expect:
        consoleLayoutCalculator.calculateNumWorkersForConsoleDisplay(10) == lines

        where:
        rows | lines
        6    | 3
        5    | 2
        4    | 2
        3    | 1
        2    | 1
        1    | 0
    }

    def "default to 4 when console size is unknown"() {
        given:
        1 * consoleMetaData.getRows() >> 0

        expect:
        consoleLayoutCalculator.calculateNumWorkersForConsoleDisplay(5) == 4
    }
}
