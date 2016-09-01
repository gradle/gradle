/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.api.internal.changedetection.rules.ChangeType
import org.gradle.api.internal.changedetection.rules.FileChange
import spock.lang.Unroll

import static FileCollectionSnapshotCompareUtil.compareTrivialSnapshots

class FileCollectionSnapshotCompareUtilTest extends AbstractTaskFilePropertyCompareStrategyTest {
    @Unroll("empty snapshots have no changes (include added: #includeAdded)")
    def "empty snapshots have no changes"() {
        expect:
        compareTrivialSnapshots(
            [:],
            [:],
            "test",
            includeAdded
        ) as List == []
        where:
        includeAdded << [true, false]
    }

    @Unroll("added snapshot is recognized (include added: #includeAdded)")
    def "added snapshot is recognized"() {
        expect:
        compareTrivialSnapshots(
            ["one-new": snapshot("one")],
            [:],
            "test",
            includeAdded
        ) as List == changes
        where:
        includeAdded | changes
        true         | [new FileChange("one-new", ChangeType.ADDED, "test")]
        false        | []
    }

    @Unroll("removed snapshot is recognized (include added: #includeAdded)")
    def "removed snapshot is recognized"() {
        expect:
        compareTrivialSnapshots(
            [:],
            ["one-old": snapshot("one")],
            "test",
            includeAdded
        ) as List == [new FileChange("one-old", ChangeType.REMOVED, "test")]
        where:
        includeAdded << [true, false]
    }

    @Unroll("replaced snapshot is recognized (include added: #includeAdded)")
    def "replaced snapshot is recognized"() {
        expect:
        compareTrivialSnapshots(
            ["two-new": snapshot("two")],
            ["one-old": snapshot("one")],
            "test",
            includeAdded
        ) as List == [new FileChange("two-new", ChangeType.REPLACED, "test")]
        where:
        includeAdded | changes
        true         | [new FileChange("two-new", ChangeType.REPLACED, "test")]
        false        | [new FileChange("one-old", ChangeType.REMOVED, "test")]
    }

    @Unroll("too many elements are ignored (#current.size() current vs #previous.size() previous, include added: #includeAdded)")
    def "too many elements are ignored"() {
        expect:
        compareTrivialSnapshots(current, previous, "test", includeAdded) == null
        where:
        includeAdded | current                                                  | previous
        true         | [:]                                                      | ["one-old": snapshot("one"), "two-old": snapshot("two")]
        false        | [:]                                                      | ["one-old": snapshot("one"), "two-old": snapshot("two")]
        true         | ["one-new": snapshot("one"), "two-new": snapshot("two")] | [:]
        false        | ["one-new": snapshot("one"), "two-new": snapshot("two")] | [:]
    }
}
