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

package org.gradle.api.plugins.migration.model.outcome.internal.archive

import org.gradle.api.Transformer
import org.gradle.api.plugins.migration.model.outcome.internal.DefaultBuildOutcomeAssociation
import org.gradle.api.plugins.migration.model.outcome.internal.archive.entry.ArchiveEntry
import org.gradle.api.plugins.migration.model.outcome.internal.archive.entry.ArchiveEntryComparison
import spock.lang.Specification

import static org.gradle.api.plugins.migration.model.compare.internal.ComparisonResultType.*

class GeneratedArchiveBuildOutcomeComparatorTest extends Specification {

    def transformer = Mock(Transformer)
    def comparator = new GeneratedArchiveBuildOutcomeComparator(transformer)
    def from = new GeneratedArchiveBuildOutcome("from", "from", "from", new File("from"))
    def to = new GeneratedArchiveBuildOutcome("to", "to", "to", new File("to"))

    def association = new DefaultBuildOutcomeAssociation(from, to, GeneratedArchiveBuildOutcome)

    ArchiveEntry entry(Map attrs) {
        new ArchiveEntry(attrs)
    }

    def "compare"() {
        when:
        1 * transformer.transform(from.archiveFile) >>> [[
                entry(path: "f1"),
                entry(path: "d1/"),
                entry(path: "d1/f1", size: 10), // diff
                entry(path: "d1/f2"), // only in from
                entry(path: "d2/"), // only in from
                entry(path: "f2") // only in from
        ] as Set]

        and:
        1 * transformer.transform(to.archiveFile) >>> [[
                entry(path: "f1"),
                entry(path: "d1/"),
                entry(path: "d1/f1", size: 20),
                entry(path: "d1/f3"), // only in to
                entry(path: "d3/"), // only in to
                entry(path: "f3") // only in to
        ] as Set]


        then:
        def result = comparator.compare(association)
        result.entryComparisons*.path == ["d1/", "d1/f1", "d1/f2", "d1/f3", "d2/", "d3/", "f1", "f2", "f3"]
        Map<String, ArchiveEntryComparison> indexed = result.entryComparisons.collectEntries { [it.path, it] }

        indexed["f1"].comparisonResultType == EQUAL
        indexed["f2"].comparisonResultType == FROM_ONLY
        indexed["f3"].comparisonResultType == TO_ONLY

        indexed["d1/"].comparisonResultType == EQUAL
        indexed["d1/f1"].comparisonResultType == UNEQUAL
        indexed["d1/f2"].comparisonResultType == FROM_ONLY
        indexed["d1/f3"].comparisonResultType == TO_ONLY

        indexed["d2/"].comparisonResultType == FROM_ONLY
        indexed["d3/"].comparisonResultType == TO_ONLY
    }


}
