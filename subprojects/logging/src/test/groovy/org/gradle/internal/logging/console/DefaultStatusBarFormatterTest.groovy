/*
 * Copyright 2016 the original author or authors.
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
import spock.lang.Subject

class DefaultStatusBarFormatterTest extends Specification {

    def consoleMetaData = Mock(ConsoleMetaData)
    @Subject statusBarFormatter = new DefaultStatusBarFormatter(consoleMetaData)

    def "formats operations"() {
        def op1 = new ProgressOperation("shortDescr1", "status1", null)
        def op2 = new ProgressOperation(null, null, op1)
        def op3 = new ProgressOperation("shortDescr2", "status2", op2)

        expect:
        statusBarFormatter.format(op3) == "> status1 > status2"
        statusBarFormatter.format(op2) == "> status1"
        statusBarFormatter.format(op1) == "> status1"
    }

    def "uses shortDescr if no status available"() {
        expect:
        statusBarFormatter.format(new ProgressOperation("shortDescr1", null, null)) == "> shortDescr1"
        statusBarFormatter.format(new ProgressOperation("shortDescr2", '', null)) == "> shortDescr2"
    }

    def "trims output to one less than the max console width"() {
        when:
        _ * consoleMetaData.getCols() >> 10
        then:
        statusBarFormatter.format(new ProgressOperation("shortDescr1", "these are more than 10 characters", null)) == "> these a"
    }

    def "empty message is supported"() {
        expect:
        statusBarFormatter.format(new ProgressOperation(null, null, null)) == ""
    }
}
