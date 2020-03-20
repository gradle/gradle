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

import org.gradle.internal.file.FileType
import org.gradle.internal.hash.HashCode
import spock.lang.Unroll

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

@Unroll
class CompleteDirectorySnapshotTest extends AbstractSnapshotWithChildrenTest<FileSystemNode, CompleteFileSystemLocationSnapshot> {
    @Override
    protected FileSystemNode createInitialRootNode(String pathToParent, List<CompleteFileSystemLocationSnapshot> children) {
        return new CompleteDirectorySnapshot("/root/${pathToParent}", PathUtil.getFileName(pathToParent), children, HashCode.fromInt(1234)).asFileSystemNode(pathToParent)
    }

    @Override
    protected CompleteFileSystemLocationSnapshot mockChild(String pathToParent) {
        Mock(CompleteFileSystemLocationSnapshot, defaultResponse: new RespondWithPathToParent(pathToParent), name: pathToParent)
    }

    def "invalidate child with no common pathToParent creates a partial directory snapshot (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.invalidate(searchedPath, CASE_SENSITIVE, changeListener).get()
        then:
        resultRoot instanceof PartialDirectorySnapshot
        resultRoot.children == children
        resultRoot.pathToParent == initialRoot.pathToParent
        removedSnapshots == [initialRoot.getSnapshot().get()]
        addedSnapshots == children
        interaction { noMoreInteractions() }

        where:
        vfsSpec << onlyDirectChildren(NO_COMMON_PREFIX)
    }

    def "invalidate a single child creates a partial directory snapshot without the child (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.invalidate(searchedPath, CASE_SENSITIVE, changeListener).get()
        then:
        resultRoot instanceof PartialDirectorySnapshot
        resultRoot.children == childrenWithSelectedChildRemoved()
        resultRoot.pathToParent == initialRoot.pathToParent
        removedSnapshots == [initialRoot.getSnapshot().get()]
        addedSnapshots == childrenWithSelectedChildRemoved()
        interaction { noMoreInteractions() }

        where:
        vfsSpec << onlyDirectChildren(SAME_PATH)
    }

    def "invalidate descendant #vfsSpec.absolutePath of child #vfsSpec.selectedChildPath creates a partial directory snapshot with the invalidated child (#vfsSpec)"() {
        setupTest(vfsSpec)
        def invalidatedChild = mockChild(selectedChild.pathToParent)

        when:
        def resultRoot = initialRoot.invalidate(searchedPath, CASE_SENSITIVE, changeListener).get()
        then:
        resultRoot instanceof PartialDirectorySnapshot
        resultRoot.children == childrenWithSelectedChildReplacedBy(invalidatedChild)
        resultRoot.pathToParent == initialRoot.pathToParent
        removedSnapshots == [initialRoot.getSnapshot().get()]
        addedSnapshots == childrenWithSelectedChildRemoved()

        interaction {
            invalidateDescendantOfSelectedChild(invalidatedChild)
            noMoreInteractions()
        }

        where:
        vfsSpec << onlyDirectChildren(CHILD_IS_PREFIX)
    }

    def "completely invalidating descendant #vfsSpec.absolutePath of child #vfsSpec.selectedChildPath creates a partial directory snapshot without the child (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.invalidate(searchedPath, CASE_SENSITIVE, changeListener).get()
        then:
        resultRoot instanceof PartialDirectorySnapshot
        resultRoot.children == childrenWithSelectedChildRemoved()
        resultRoot.pathToParent == initialRoot.pathToParent
        removedSnapshots == [initialRoot.getSnapshot().get()]
        addedSnapshots == childrenWithSelectedChildRemoved()

        interaction {
            invalidateDescendantOfSelectedChild(null)
            noMoreInteractions()
        }

        where:
        vfsSpec << onlyDirectChildren(CHILD_IS_PREFIX)
    }

    def "storing for path #vfsSpec.absolutePath adds no information (#vfsSpec)"() {
        setupTest(vfsSpec)

        expect:
        initialRoot.store(searchedPath, CASE_SENSITIVE, Mock(MetadataSnapshot), changeListener) is initialRoot
        addedSnapshots.empty
        removedSnapshots.empty

        where:
        vfsSpec << onlyDirectChildren(NO_COMMON_PREFIX + SAME_PATH + CHILD_IS_PREFIX)
    }

    def "querying the snapshot for non-existent child #vfsSpec.absolutePath yields a missing file (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        CompleteFileSystemLocationSnapshot foundSnapshot = initialRoot.getSnapshot(searchedPath, CASE_SENSITIVE).get() as CompleteFileSystemLocationSnapshot
        then:
        foundSnapshot.type == FileType.Missing
        foundSnapshot.absolutePath == searchedPath.absolutePath

        where:
        vfsSpec << onlyDirectChildren(NO_COMMON_PREFIX)
    }

    def "querying the snapshot for child #vfsSpec.absolutePath yields the child (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        CompleteFileSystemLocationSnapshot foundSnapshot = initialRoot.getSnapshot(searchedPath, CASE_SENSITIVE).get() as CompleteFileSystemLocationSnapshot
        then:
        foundSnapshot == selectedChild
        1 * selectedChild.snapshot >> Optional.of(selectedChild)
        interaction { noMoreInteractions() }

        where:
        vfsSpec << onlyDirectChildren(SAME_PATH)
    }

    def "querying a snapshot in child #vfsSpec.absolutePath yields the found snapshot (#vfsSpec)"() {
        setupTest(vfsSpec)
        def grandChild = Mock(CompleteFileSystemLocationSnapshot)

        when:
        CompleteFileSystemLocationSnapshot foundSnapshot = initialRoot.getSnapshot(searchedPath, CASE_SENSITIVE).get() as CompleteFileSystemLocationSnapshot
        then:
        foundSnapshot == grandChild
        interaction {
            getDescendantSnapshotOfSelectedChild(grandChild)
            noMoreInteractions()
        }

        where:
        vfsSpec << onlyDirectChildren(CHILD_IS_PREFIX)
    }

    def "querying a non-existent snapshot in child #vfsSpec.absolutePath yields a missing snapshot (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        CompleteFileSystemLocationSnapshot foundSnapshot = initialRoot.getSnapshot(searchedPath, CASE_SENSITIVE).get() as CompleteFileSystemLocationSnapshot
        then:
        foundSnapshot.type == FileType.Missing
        foundSnapshot.absolutePath == searchedPath.absolutePath
        interaction {
            getDescendantSnapshotOfSelectedChild(null)
            noMoreInteractions()
        }

        where:
        vfsSpec << onlyDirectChildren(CHILD_IS_PREFIX)
    }

    /**
     * Removes all specs which contain compressed paths, since this isn't allowed for the children of {@link CompleteDirectorySnapshot}s.
     */
    private static List<VirtualFileSystemTestSpec> onlyDirectChildren(List<VirtualFileSystemTestSpec> fullList) {
        return fullList.findAll { it.childPaths.every { !it.contains('/') } }
    }
}
