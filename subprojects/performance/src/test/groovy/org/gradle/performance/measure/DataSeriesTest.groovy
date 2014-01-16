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

package org.gradle.performance.measure

import spock.lang.Specification

class DataSeriesTest extends Specification {
    def "can calculate average and min and max"() {
        def v1 = DataAmount.kbytes(10)
        def v2 = DataAmount.kbytes(20)
        def v3 = DataAmount.kbytes(30)
        def series = new DataSeries([v1, v2, v3])

        expect:
        series.average == v2
        series.min == v1
        series.max == v3
    }
}
