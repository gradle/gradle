/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.typeconversion

import org.gradle.api.InvalidUserDataException
import spock.lang.Specification

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.NANOSECONDS

class TimeUnitsParserTest extends Specification {

    def parser = new TimeUnitsParser()

    def "parses time units"() {
        expect:
        def unit = parser.parseNotation(input, value)
        unit.value == normalizedValue
        unit.timeUnit == parsed

        where:
        value           |input          |parsed         | normalizedValue
        10              |'nanoseconds'  |NANOSECONDS    |10
        20              |'mILLISECONds' |MILLISECONDS   |20
        1               |'days'         |MILLISECONDS   |1 * 24 * 60 * 60 * 1000
        2               |'hours'        |MILLISECONDS   |2 * 60 * 60 * 1000
        5               |'MINUTES'      |MILLISECONDS   |5 * 60 * 1000
    }

    def "fails gracefully for invalid input"() {
        when:
        parser.parseNotation("foobar", 133)
        then:
        def ex = thrown(InvalidUserDataException)
        ex.message.contains("foobar")
    }
}
