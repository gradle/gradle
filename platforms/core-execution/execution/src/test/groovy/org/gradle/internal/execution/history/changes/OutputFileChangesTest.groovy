/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.execution.history.changes

import com.google.common.collect.ImmutableSortedMap
import org.apache.commons.io.FilenameUtils
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.TestSnapshotFixture
import spock.lang.Specification

class OutputFileChangesTest extends Specification implements TestSnapshotFixture {

    def "empties"() {
        expect:
        changes(
            FileSystemSnapshot.EMPTY,
            FileSystemSnapshot.EMPTY
        ) == []
    }

    def "equal hash for #current.type"() {
        expect:
        changes(previous, current) == []

        where:
        previous                        | current
        regularFile("one", 0x1234)      | regularFile("one", 0x1234)
        missing("one")                  | missing("one")
        directoryWithTwoChildren("one") | directoryWithTwoChildren("one")
    }

    def "equal hash for #current.type with different path"() {
        expect:
        changes(previous, current) == []

        where:
        previous                        | current
        regularFile("one", 0x1234)      | regularFile("two", 0x1234)
        missing("one")                  | missing("two")
        directoryWithTwoChildren("one") | directoryWithTwoChildren("two")
    }

    def "trivial addition of #current.type"() {
        expect:
        changes(
            FileSystemSnapshot.EMPTY,
            current
        ) == expectedChanges

        where:
        current                          | expectedChanges
        regularFile("two", 0x1234)       | [added("two")]
        missing("two")                   | [added("two")]
        directoryWithTwoChildren("root") | [added("root"), added("root/one"), added("root/two")]
    }

    def "trivial removal of #previous.type"() {
        expect:
        changes(
            previous,
            FileSystemSnapshot.EMPTY
        ) == expectedChanges

        where:
        previous                         | expectedChanges
        regularFile("two", 0x1234)       | [removed("two")]
        missing("two")                   | [removed("two")]
        directoryWithTwoChildren("root") | [removed("root"), removed("root/one"), removed("root/two")]
    }

    def "trivial content change"() {
        expect:
        changes(
            regularFile("one", 0x1234),
            regularFile("one", 0xffff)
        ) == [modified("one")]
    }

    def "deep equality"() {
        expect:
        changes(
            directory("root", [
                regularFile("root/one", 0x1234),
                regularFile("root/two", 0x2345)
            ]),
            directory("root", [
                regularFile("root/one", 0x1234),
                regularFile("root/two", 0x2345)
            ])
        ) == []
    }

    def "type change directory -> regular file"() {
        expect:
        changes(
            directory("root", [
                regularFile("root/one", 0x1234),
                regularFile("root/two", 0x2345)
            ]),
            regularFile("root", 0x1234)
        ) == [modified("root"), removed("root/one"), removed("root/two")]
    }

    def "type change directory -> missing file"() {
        expect:
        changes(
            directory("root", [
                regularFile("root/one", 0x1234),
                regularFile("root/two", 0x2345)
            ]),
            missing("root")
        ) == [removed("root"), removed("root/one"), removed("root/two")]
    }

    def "type change missing -> directory"() {
        expect:
        changes(
            missing("root"),
            directory("root", [
                regularFile("root/one", 0x1234),
                regularFile("root/two", 0x2345)
            ])
        ) == [added("root"), added("root/one"), added("root/two")]
    }

    def "type change missing -> regular file"() {
        expect:
        changes(
            missing("root"),
            regularFile("root", 0x1234)
        ) == [added("root")]
    }

    def "type change regular file -> directory"() {
        expect:
        changes(
            regularFile("root", 0x1234),
            directory("root", [
                regularFile("root/one", 0x1234),
                regularFile("root/two", 0x2345)
            ])
        ) == [modified("root"), added("root/one"), added("root/two")]
    }

    def "type change regular file -> missing"() {
        expect:
        changes(
            regularFile("root", 0x1234),
            missing("root")
        ) == [removed("root")]
    }

    def "deep addition"() {
        expect:
        changes(
            directory("root1", [
                regularFile("root1/one", 0x1234)
            ]),
            directory("root2", [
                regularFile("root2/one", 0x1234),
                regularFile("root2/two", 0x2345)
            ])
        ) == [added("root2/two")]
    }

    def "deep removal"() {
        expect:
        changes(
            directory("root", [
                regularFile("root/one", 0x1234),
                regularFile("root/two", 0x2345)
            ]),
            directory("root", [
                regularFile("root/one", 0x1234)
            ])
        ) == [removed("root/two")]
    }

    def "deep content change"() {
        expect:
        changes(
            directory("root", [
                regularFile("root/one", 0x1234),
                regularFile("root/two", 0x2345)
            ]),
            directory("root", [
                regularFile("root/one", 0x1234),
                regularFile("root/two", 0xffff)
            ])
        ) == [modified("root/two")]
    }

    FileSystemLocationSnapshot directoryWithTwoChildren(String absolutePath) {
        directory(absolutePath, [
            regularFile("${absolutePath}/one", 0x1234),
            regularFile("${absolutePath}/two", 0x2345)
        ])
    }

    def changes(FileSystemSnapshot previousSnapshot, FileSystemSnapshot currentSnapshot) {
        def visitor = new CollectingChangeVisitor()
        def outputFileChanges = new OutputFileChanges(ImmutableSortedMap.of("test", previousSnapshot), ImmutableSortedMap.of("test", currentSnapshot))
        outputFileChanges.accept(visitor)
        visitor.getChanges().collect {new DescriptiveChange(it.message) }.toList()
    }

    def added(String path) {
        new DescriptiveChange("Output property 'test' file ${FilenameUtils.separatorsToSystem(path)} has been added.")
    }

    def removed(String path) {
        new DescriptiveChange("Output property 'test' file ${FilenameUtils.separatorsToSystem(path)} has been removed.")
    }

    def modified(String path) {
        new DescriptiveChange("Output property 'test' file ${FilenameUtils.separatorsToSystem(path)} has changed.")
    }
}
