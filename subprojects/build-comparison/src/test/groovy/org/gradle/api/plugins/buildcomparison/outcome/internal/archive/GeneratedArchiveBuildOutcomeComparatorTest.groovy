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

import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import groovy.transform.CompileStatic
import org.gradle.api.Transformer
import org.gradle.api.plugins.buildcomparison.outcome.internal.DefaultBuildOutcomeAssociation
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.entry.ArchiveEntry
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.entry.ArchiveEntryComparison
import org.gradle.internal.resource.local.DefaultLocallyAvailableResource
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.plugins.buildcomparison.compare.internal.ComparisonResultType.*

class GeneratedArchiveBuildOutcomeComparatorTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider dir = new TestNameTestDirectoryProvider()

    def transformer = Mock(Transformer)
    def comparator = new GeneratedArchiveBuildOutcomeComparator(transformer)

    def existingFrom = outcome("from")
    def existingTo = outcome("to")

    protected DefaultBuildOutcomeAssociation associate(from, to) {
        new DefaultBuildOutcomeAssociation(from, to, GeneratedArchiveBuildOutcome)
    }

    ArchiveEntry entry(Map attrs) {
        ArchiveEntry.of(attrs)
    }

    Set<ArchiveEntry> flatten(Set<ArchiveEntry> archiveEntries) {
        archiveEntries.collect {
            [it] + flatten(it.subEntries)
        }.flatten()
    }

    void mockEntries(File archive, ArchiveEntry... entries) {
        def flattened = flatten(Sets.newHashSet(entries))
        interaction {
            _ * transformer.transform(archive) >> flattened
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
                entry(path: "f2"), // only in from
                entry(path: "sourceSub.zip", subEntries: set( // only in from
                        entry(parentPaths: ["sourceSub.zip"], path: "a.txt"), // only in from
                        entry(parentPaths: ["sourceSub.zip"], path: "b/"), // only in from
                        entry(parentPaths: ["sourceSub.zip"], path: "b/c.txt") // only in from
                )),
                entry(path: "sameSub.zip", subEntries: set(
                        entry(parentPaths: ["sameSub.zip"], path: "a.txt"),
                        entry(parentPaths: ["sameSub.zip"], path: "b/"),
                        entry(parentPaths: ["sameSub.zip"], path: "b/c.txt")
                )),
                entry(path: "differentSub.zip", subEntries: set(
                        entry(parentPaths: ["differentSub.zip"], path: "a.txt", size: 0),
                        entry(parentPaths: ["differentSub.zip"], path: "b/"),
                        entry(parentPaths: ["differentSub.zip"], path: "b/c.txt")
                ))
        )

        and:
        mockEntries(existingTo.archiveFile,
                entry(path: "f1"),
                entry(path: "d1/"),
                entry(path: "d1/f1", size: 20),
                entry(path: "d1/f3"), // only in to
                entry(path: "d3/"), // only in to
                entry(path: "f3"), // only in to
                entry(path: "targetSub.zip", subEntries: set( // only in from
                        entry(parentPaths: ["targetSub.zip"], path: "a.txt"), // only in from
                        entry(parentPaths: ["targetSub.zip"], path: "b/"), // only in from
                        entry(parentPaths: ["targetSub.zip"], path: "b/c.txt") // only in from
                )),
                entry(path: "sameSub.zip", subEntries: set(
                        entry(parentPaths: ["sameSub.zip"], path: "a.txt"),
                        entry(parentPaths: ["sameSub.zip"], path: "b/"),
                        entry(parentPaths: ["sameSub.zip"], path: "b/c.txt")
                )),
                entry(path: "differentSub.zip", subEntries: set(
                        entry(parentPaths: ["differentSub.zip"], path: "a.txt", size: 1),
                        entry(parentPaths: ["differentSub.zip"], path: "b/"),
                        entry(parentPaths: ["differentSub.zip"], path: "b/c.txt")
                ))
        )

        then:
        def result = compare(existingFrom, existingTo)
        result.entryComparisons*.path*.toString() == [
                "d1/",
                "d1/f1",
                "d1/f2",
                "d1/f3",
                "d2/",
                "d3/",
                "differentSub.zip",
                "differentSub.zip!/a.txt",
                "differentSub.zip!/b/",
                "differentSub.zip!/b/c.txt",
                "f1",
                "f2",
                "f3",
                "sameSub.zip",
                "sameSub.zip!/a.txt",
                "sameSub.zip!/b/",
                "sameSub.zip!/b/c.txt",
                "sourceSub.zip",
                "sourceSub.zip!/a.txt",
                "sourceSub.zip!/b/",
                "sourceSub.zip!/b/c.txt",
                "targetSub.zip",
                "targetSub.zip!/a.txt",
                "targetSub.zip!/b/",
                "targetSub.zip!/b/c.txt"
        ]

        Map<String, ArchiveEntryComparison> indexed = result.entryComparisons.collectEntries { [it.path.toString(), it] }

        indexed["f1"].comparisonResultType == EQUAL
        indexed["f2"].comparisonResultType == SOURCE_ONLY
        indexed["f3"].comparisonResultType == TARGET_ONLY

        indexed["d1/"].comparisonResultType == EQUAL
        indexed["d1/f1"].comparisonResultType == UNEQUAL
        indexed["d1/f2"].comparisonResultType == SOURCE_ONLY
        indexed["d1/f3"].comparisonResultType == TARGET_ONLY

        indexed["d2/"].comparisonResultType == SOURCE_ONLY
        indexed["d3/"].comparisonResultType == TARGET_ONLY

        indexed["sourceSub.zip"].comparisonResultType == SOURCE_ONLY
        indexed["sourceSub.zip!/a.txt"].comparisonResultType == SOURCE_ONLY
        indexed["sourceSub.zip!/b/"].comparisonResultType == SOURCE_ONLY
        indexed["sourceSub.zip!/b/c.txt"].comparisonResultType == SOURCE_ONLY

        indexed["targetSub.zip"].comparisonResultType == TARGET_ONLY
        indexed["targetSub.zip!/a.txt"].comparisonResultType == TARGET_ONLY
        indexed["targetSub.zip!/b/"].comparisonResultType == TARGET_ONLY
        indexed["targetSub.zip!/b/c.txt"].comparisonResultType == TARGET_ONLY

        indexed["sameSub.zip"].comparisonResultType == EQUAL
        indexed["sameSub.zip!/a.txt"].comparisonResultType == EQUAL
        indexed["sameSub.zip!/b/"].comparisonResultType == EQUAL
        indexed["sameSub.zip!/b/c.txt"].comparisonResultType == EQUAL

        indexed["differentSub.zip"].comparisonResultType == UNEQUAL
        indexed["differentSub.zip!/a.txt"].comparisonResultType == UNEQUAL
        indexed["differentSub.zip!/b/"].comparisonResultType == EQUAL
        indexed["differentSub.zip!/b/c.txt"].comparisonResultType == EQUAL
    }

    @CompileStatic // Workaround for https://issues.apache.org/jira/browse/GROOVY-7879 on Java 9
    static <T> ImmutableSet<T> set(T element1, T element2, T element3) {
        return ImmutableSet.of(element1, element2, element3)
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
        compare(notExistingFrom, notExistingTo).outcomesAreIdentical
    }

    protected GeneratedArchiveBuildOutcomeComparisonResult compare(from, to) {
        comparator.compare(associate(from, to))
    }

    GeneratedArchiveBuildOutcome outcome(String name, File file = dir.createFile(name)) {
        def resource = new DefaultLocallyAvailableResource(file)
        new GeneratedArchiveBuildOutcome(name, name, resource, name)
    }
}
