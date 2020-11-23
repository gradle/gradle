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


import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.TestSnapshotFixture
import spock.lang.Specification

import static org.gradle.internal.file.FileMetadata.AccessType.DIRECT

class OutputFileChangesTest extends Specification implements TestSnapshotFixture {

    def "empties"() {
        expect:
        changes(
            FileSystemSnapshot.EMPTY,
            FileSystemSnapshot.EMPTY
        ) == []
    }

    def "trivial equality"() {
        expect:
        changes(
            regularFile("one", DIRECT, 1234),
            regularFile("one", DIRECT, 1234)
        ) == []
    }

    def "trivial addition"() {
        expect:
        changes(
            FileSystemSnapshot.EMPTY,
            regularFile("two")
        ) == [added("two")]
    }

    def "trivial removal"() {
        expect:
        changes(
            regularFile("one"),
            FileSystemSnapshot.EMPTY
        ) == [removed("one")]
    }

    def "trivial absolute path change"() {
        expect:
        changes(
            regularFile("one"),
            regularFile("two")
        ) == [removed("one"), added("two")]
    }

    def "trivial content change"() {
        expect:
        changes(
            regularFile("one", DIRECT, 1234),
            regularFile("one", DIRECT, 2345)
        ) == [modified("one")]
    }

    def changes(FileSystemSnapshot previousSnapshot, FileSystemSnapshot currentSnapshot) {
        def visitor = new CollectingChangeVisitor()
        OutputFileChanges.COMPARE_STRATEGY.visitChangesSince(previousSnapshot, currentSnapshot, "test", visitor)
        visitor.getChanges().toList()
    }

    def added(String path) {
        new DescriptiveChange("Output property 'test' file $path has been added.")
    }

    def removed(String path) {
        new DescriptiveChange("Output property 'test' file $path has been removed.")
    }

    def modified(String path) {
        new DescriptiveChange("Output property 'test' file $path has changed.")
    }
}
