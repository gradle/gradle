/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.snapshot

import org.gradle.internal.vfs.SnapshotHierarchy
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

@Unroll
class SnapshotUtilTest extends Specification {

    SnapshotHierarchy.ChangeListener changeListener = new SnapshotHierarchy.ChangeListener() {
        @Override
        void nodeRemoved(FileSystemNode node) {
        }

        @Override
        void nodeAdded(FileSystemNode node) {
        }
    }

    def "getSnapshotFromChild returns child when queried at the same path #absolutePath"() {
        def child = mockChild(pathToParent)

        when:
        def foundSnapshot = SnapshotUtil.getSnapshotFromChild(child, VfsRelativePath.of(absolutePath).suffixStartingFrom(suffixStart), CASE_SENSITIVE)
        then:
        foundSnapshot.present
        1 * child.snapshot >> Optional.of(Mock(MetadataSnapshot))

        where:
        absolutePath          | suffixStart               | pathToParent
        "/some/absolute/path" | "some/absolute/".length() | "path"
        "C:"                  | 0                         | "C:"
        "C:\\"                | 0                         | "C:"
        "/"                   | 0                         | ""
    }

    def "getSnapshotFromChild queries child when queried at the path in child"() {
        def child = mockChild(pathToParent)
        def relativePath = VfsRelativePath.of(absolutePath).suffixStartingFrom(suffixStart)

        when:
        def foundSnapshot = SnapshotUtil.getSnapshotFromChild(child, relativePath, CASE_SENSITIVE)
        then:
        foundSnapshot.present
        1 * child.getSnapshot(relativePath.suffixStartingFrom(pathToParent.length() + childOffset), CASE_SENSITIVE) >> Optional.of(Mock(MetadataSnapshot))

        where:
        absolutePath                    | suffixStart               | pathToParent | childOffset
        "/some/absolute/path/something" | "some/absolute/".length() | "path"       | 1
        "C:"                            | 0                         | ""           | 0
        "C:\\some"                      | 0                         | "C:"         | 1
        "/something"                    | 0                         | ""           | 0
    }

    def "storeSingleChild uses offset #childOffset for path #absolutePath in child #pathToParent"() {
        def child = mockChild(pathToParent)
        def snapshot = Mock(MetadataSnapshot)
        def updatedChild = mockChild(pathToParent)
        def relativePath = VfsRelativePath.of(absolutePath).suffixStartingFrom(suffixStart)

        when:
        def resultRoot = SnapshotUtil.storeSingleChild(child, relativePath, CASE_SENSITIVE, snapshot, changeListener)
        then:
        resultRoot.is updatedChild
        1 * child.store(relativePath.suffixStartingFrom(pathToParent.length() + childOffset), CASE_SENSITIVE, snapshot, changeListener) >> updatedChild

        where:
        absolutePath                    | suffixStart               | pathToParent | childOffset
        "/some/absolute/path/something" | "some/absolute/".length() | "path"       | 1
        "C:"                            | 0                         | ""           | 0
        "C:\\some"                      | 0                         | "C:"         | 1
        "/something"                    | 0                         | ""           | 0
    }

    def "invalidateSingleChild uses offset #childOffset for path #absolutePath in child #pathToParent"() {
        def child = mockChild(pathToParent)
        def invalidatedChild = mockChild(pathToParent)
        def relativePath = VfsRelativePath.of(absolutePath).suffixStartingFrom(suffixStart)

        when:
        def resultRoot = SnapshotUtil.invalidateSingleChild(child, relativePath, CASE_SENSITIVE, changeListener).get()
        then:
        resultRoot.is invalidatedChild
        1 * child.invalidate(relativePath.suffixStartingFrom(pathToParent.length() + childOffset), CASE_SENSITIVE, changeListener) >> Optional.of(invalidatedChild)

        where:
        absolutePath                    | suffixStart               | pathToParent | childOffset
        "/some/absolute/path/something" | "some/absolute/".length() | "path"       | 1
        "C:"                            | 0                         | ""           | 0
        "C:\\some"                      | 0                         | "C:"         | 1
        "/something"                    | 1                         | ""           | 0
    }

    protected FileSystemNode mockChild(String pathToParent) {
        Mock(FileSystemNode, defaultResponse: new RespondWithPathToParent(pathToParent), name: pathToParent)
    }
}
