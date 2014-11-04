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

package org.gradle.api.plugins.buildcomparison.outcome.internal.archive.entry

import org.gradle.api.plugins.buildcomparison.compare.internal.ComparisonResultType
import spock.lang.Specification

class ArchiveEntryComparisonTest extends Specification {

    ArchiveEntry entry(Map attrs) {
        new ArchiveEntry(attrs)
    }

    def sortPath
    def path
    def from = entry(path: path, size: 10)
    def to = entry(path: path, size: 10)

    ArchiveEntryComparison comparison(String sortPath = sortPath, String path = path, ArchiveEntry from = from, ArchiveEntry to = to) {
        new ArchiveEntryComparison(sortPath, path, from, to)
    }


    def "comparisons"() {
        expect:
        comparison().comparisonResultType == ComparisonResultType.EQUAL

        when:
        from.size += 1

        then:
        comparison().comparisonResultType == ComparisonResultType.UNEQUAL

        when:
        from = null

        then:
        comparison().comparisonResultType == ComparisonResultType.TARGET_ONLY

        when:
        from = to
        to = null

        then:
        comparison().comparisonResultType == ComparisonResultType.SOURCE_ONLY
    }

    def "from or to must be non null"() {
        given:
        from = null
        to = null

        when:
        comparison()

        then:
        thrown IllegalArgumentException
    }

    def comparable() {
        given:
        sortPath = "a"
        def a = comparison()
        sortPath = "b"
        def b = comparison()

        expect:
        a.compareTo(b) < 0
        b.compareTo(a) > 0

        when:
        sortPath = null
        path = "a"
        b = comparison()

        then:
        a.compareTo(b) == 0
        b.compareTo(a) == 0

        when:
        sortPath = "A"
        path = null
        b = comparison()

        then:
        a.compareTo(b) == 0
        b.compareTo(a) == 0

        when:
        a = b
        sortPath = "b"
        b = comparison()

        then:
        a.compareTo(b) < 0
        b.compareTo(a) > 0
    }
}
