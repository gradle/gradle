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

package org.gradle.api.plugins.buildcomparison.outcome.internal.archive

import org.gradle.api.Transformer
import org.gradle.api.internal.filestore.AbstractFileStoreEntry
import org.gradle.api.plugins.buildcomparison.outcome.internal.DefaultBuildOutcomeAssociation
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.entry.ArchiveEntry
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.entry.ArchiveEntryComparison
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.plugins.buildcomparison.compare.internal.ComparisonResultType.*

class GeneratedArchiveBuildOutcomeComparatorTest extends Specification {

    @Rule TemporaryFolder dir = new TemporaryFolder()

    def transformer = Mock(Transformer)
    def comparator = new GeneratedArchiveBuildOutcomeComparator(transformer)

    def existingFrom = outcome("from")
    def existingTo = outcome("to")

    protected DefaultBuildOutcomeAssociation associate(from, to) {
        new DefaultBuildOutcomeAssociation(from, to, GeneratedArchiveBuildOutcome)
    }

    ArchiveEntry entry(Map attrs) {
        new ArchiveEntry(attrs)
    }

    void mockEntries(File archive, ArchiveEntry... entries) {
        interaction {
            _ * transformer.transform(archive) >>> [entries as Set]
        }
    }

    def "compare entries"() {
        when:
        mockEntries(existingFrom.archiveFile,
                entry(path: "f1"),
                entry(path: "d1/"),
                entry(path: "d1/f1", size: 10), // diff
                entry(path: "d1/f2"), // only in from
                entry(path: "d2/"), // only in from
                entry(path: "f2") // only in from
        )

        and:
        mockEntries(existingTo.archiveFile,
                entry(path: "f1"),
                entry(path: "d1/"),
                entry(path: "d1/f1", size: 20),
                entry(path: "d1/f3"), // only in to
                entry(path: "d3/"), // only in to
                entry(path: "f3") // only in to
        )

        then:
        def result = compare(existingFrom, existingTo)
        result.entryComparisons*.path == ["d1/", "d1/f1", "d1/f2", "d1/f3", "d2/", "d3/", "f1", "f2", "f3"]
        Map<String, ArchiveEntryComparison> indexed = result.entryComparisons.collectEntries { [it.path, it] }

        indexed["f1"].comparisonResultType == EQUAL
        indexed["f2"].comparisonResultType == SOURCE_ONLY
        indexed["f3"].comparisonResultType == TARGET_ONLY

        indexed["d1/"].comparisonResultType == EQUAL
        indexed["d1/f1"].comparisonResultType == UNEQUAL
        indexed["d1/f2"].comparisonResultType == SOURCE_ONLY
        indexed["d1/f3"].comparisonResultType == TARGET_ONLY

        indexed["d2/"].comparisonResultType == SOURCE_ONLY
        indexed["d3/"].comparisonResultType == TARGET_ONLY
    }


    def "comparison result types"() {
        given:
        def unequal = outcome("unequal")
        def notExistingFrom = outcome("no-from", null)
        def notExistingTo = outcome("no-to", null)

        mockEntries(existingFrom.archiveFile, entry(path: "f1"))
        mockEntries(existingTo.archiveFile, entry(path: "f1"))
        mockEntries(unequal.archiveFile, entry(path: "f1", size: 20))

        expect:
        compare(existingFrom, existingTo).comparisonResultType == EQUAL
        compare(notExistingFrom, existingTo).comparisonResultType == TARGET_ONLY
        compare(existingFrom, notExistingTo).comparisonResultType == SOURCE_ONLY
        compare(existingFrom, unequal).comparisonResultType == UNEQUAL
        compare(notExistingFrom, notExistingTo).comparisonResultType == NON_EXISTENT

        and:
        compare(existingFrom, existingTo).outcomesAreIdentical
        !compare(notExistingFrom, existingTo).outcomesAreIdentical
        !compare(existingFrom, notExistingTo).outcomesAreIdentical
        !compare(existingFrom, unequal).outcomesAreIdentical
        !compare(notExistingFrom, notExistingTo).outcomesAreIdentical
    }

    protected GeneratedArchiveBuildOutcomeComparisonResult compare(from, to) {
        comparator.compare(associate(from, to))
    }

    GeneratedArchiveBuildOutcome outcome(String name, File file = dir.createFile(name)) {
        def fileStoreEntry = new AbstractFileStoreEntry() {
            File getFile() { file }
        }
        new GeneratedArchiveBuildOutcome(name, name, fileStoreEntry, name)
    }
}
