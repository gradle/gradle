/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.launcher.daemon.server.health

import spock.lang.Specification

class IntegerTextUtilTest extends Specification {

    def "ordinal"() {
        expect:
        ordinal == IntegerTextUtil.ordinal(input)

        where:
        input | ordinal
        0     | "0th"
        1     | "1st"
        2     | "2nd"
        3     | "3rd"
        4     | "4th"
        10    | "10th"
        11    | "11th"
        12    | "12th"
        13    | "13th"
        14    | "14th"
        20    | "20th"
        21    | "21st"
        22    | "22nd"
        23    | "23rd"
        24    | "24th"
        100   | "100th"
        1001  | "1001st"
        10012 | "10012th"
        10013 | "10013th"
        10014 | "10014th"
    }
}
