/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.performance.fixture

import org.gradle.performance.measure.Amount
import org.gradle.performance.measure.Duration
import spock.lang.Specification

import static org.gradle.performance.fixture.PrettyCalculator.percentChange

class PrettyCalculatorSpec extends Specification {

    def "knows percentage change"() {
        expect:
        percentChange(Amount.valueOf(current, Duration.SECONDS), Amount.valueOf(prevous, Duration.SECONDS)) == percent

        where:
        current  | prevous | percent
        1        | 1       | 0
        3        | 1       | 200
        1        | 3       | -66.67
        2        | 3       | -33.33
        5        | 4       | 25
        2.2      | 3.567   | -38.32
        12.00001 | 10.23   | 17.30
        //not strictly true, but for our purposes should do:
        0        | 3       | -100
        300      | 0       | 100
    }
}