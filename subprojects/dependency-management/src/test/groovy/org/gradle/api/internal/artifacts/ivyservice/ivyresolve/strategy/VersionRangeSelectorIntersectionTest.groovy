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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

import spock.lang.Specification
import spock.lang.Unroll

class VersionRangeSelectorIntersectionTest extends Specification {
    def comparator = new DefaultVersionComparator().asVersionComparator()
    def versionParser = new VersionParser()

    @Unroll
    def "intersects ranges #first and #second"() {
        given:
        def firstRange = range(first)
        def secondRange = range(second)

        when:
        def intersection = firstRange.intersect(secondRange)

        then:
        if (eLower == null) {
            assert intersection == null
        } else {
            assert intersection != null: 'Expected intersection to be defined, but got disjoint interval'
            assert intersection.lowerBound == eLower
            assert intersection.lowerInclusive == eLowerInclusive
            assert intersection.upperBound == eUpper
            assert intersection.upperInclusive == eUpperInclusive
        }

        when:
        intersection = secondRange.intersect(firstRange)

        then:
        if (eLower == null) {
            assert intersection == null
        } else {
            assert intersection != null: 'Expected intersection to be defined, but got disjoint interval'
            assert intersection.lowerBound == eLower
            assert intersection.lowerInclusive == eLowerInclusive
            assert intersection.upperBound == eUpper
            assert intersection.upperInclusive == eUpperInclusive
        }

        where:
        first    | second  | eLower | eLowerInclusive | eUpper | eUpperInclusive
        '[1,2]'  | '[1,2]' | '1'    | true            | '2'    | true
        ']1,2]'  | ']1,2]' | '1'    | false           | '2'    | true
        '[1,2['  | '[1,2[' | '1'    | true            | '2'    | false
        ']1,2['  | ']1,2[' | '1'    | false           | '2'    | false
        '[1,10]' | '[3,6]' | '3'    | true            | '6'    | true
        '[1,5]'  | '[3,5]' | '3'    | true            | '5'    | true
        '[3,5]'  | '[1,5]' | '3'    | true            | '5'    | true
        ']1,2]'  | '[1,2]' | '1'    | false           | '2'    | true
        '(,10]'  | '[1,5]' | '1'    | true            | '5'    | true
        '[0,)'   | '[1,5]' | '1'    | true            | '5'    | true

        '[1,2['  | '[1,2]' | '1'    | true            | '2'    | false
        ']1,4['  | '[2,3]' | '2'    | true            | '3'    | true

        '[1,2]'  | '[5,6]' | null   | true            | null   | true
        '(,10]'  | '[11,)' | null   | true            | null   | true
        '[1,2]'  | '[3,4]' | null   | true            | null   | true
        '[1,2['  | ']2,4]' | null   | true            | null   | true

    }


    private VersionRangeSelector range(String str) {
        new VersionRangeSelector(str, comparator, versionParser)
    }
}
