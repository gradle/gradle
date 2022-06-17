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

import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.internal.file.FileType
import org.gradle.internal.hash.TestHashCodes

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

class DirectoryNodeTest extends AbstractFileSystemNodeWithChildrenTest<FileSystemNode, FileSystemLocationSnapshot> {
    @Override
    protected FileSystemNode createInitialRootNode(ChildMap<FileSystemLocationSnapshot> children) {
        return new DirectorySnapshot("/root/some/path", PathUtil.getFileName("path"), AccessType.DIRECT, TestHashCodes.hashCodeFrom(1234), children).asFileSystemNode()
    }

    @Override
    protected FileSystemLocationSnapshot mockChild() {
        Mock(FileSystemLocationSnapshot)
    }

    def "invalidate child with no common pathToParent creates a partial directory node (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.invalidate(searchedPath, CASE_SENSITIVE, diffListener).get()
        then:
        resultRoot instanceof PartialDirectoryNode
        resultRoot.children == children
        removedNodes == [initialRoot.getSnapshot().get()]
        addedNodes == children.stream().map(ChildMap.Entry::getValue).toList()
        interaction { noMoreInteractions() }

        where:
        vfsSpec << onlyDirectChildren(NO_COMMON_PREFIX)
    }

    def "invalidate a single child creates a partial directory node without the child (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.invalidate(searchedPath, CASE_SENSITIVE, diffListener).get()
        then:
        resultRoot instanceof PartialDirectoryNode
        resultRoot.children == childrenWithSelectedChildRemoved()
        removedNodes == [initialRoot.getSnapshot().get()]
        addedNodes == childrenWithSelectedChildRemoved().stream().map(ChildMap.Entry::getValue).toList()
        interaction { noMoreInteractions() }

        where:
        vfsSpec << onlyDirectChildren(SAME_PATH)
    }

    def "invalidate descendant #vfsSpec.searchedPath of child #vfsSpec.selectedChildPath creates a partial directory node with the invalidated child (#vfsSpec)"() {
        setupTest(vfsSpec)
        def invalidatedChild = mockChild()

        when:
        def resultRoot = initialRoot.invalidate(searchedPath, CASE_SENSITIVE, diffListener).get()
        then:
        resultRoot instanceof PartialDirectoryNode
        resultRoot.children == childrenWithSelectedChildReplacedBy(invalidatedChild)
        removedNodes == [initialRoot.getSnapshot().get()]
        addedNodes == childrenWithSelectedChildRemoved().stream().map(ChildMap.Entry::getValue).toList()

        interaction {
            invalidateDescendantOfSelectedChild(invalidatedChild)
            noMoreInteractions()
        }

        where:
        vfsSpec << onlyDirectChildren(CHILD_IS_PREFIX)
    }

    def "completely invalidating descendant #vfsSpec.searchedPath of child #vfsSpec.selectedChildPath creates a partial directory node without the child (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.invalidate(searchedPath, CASE_SENSITIVE, diffListener).get()
        then:
        resultRoot instanceof PartialDirectoryNode
        resultRoot.children == childrenWithSelectedChildRemoved()
        removedNodes == [initialRoot.getSnapshot().get()]
        addedNodes == childrenWithSelectedChildRemoved().stream().map(ChildMap.Entry::getValue).toList()

        interaction {
            invalidateDescendantOfSelectedChild(null)
            noMoreInteractions()
        }

        where:
        vfsSpec << onlyDirectChildren(CHILD_IS_PREFIX)
    }

    def "storing for path #vfsSpec.searchedPath adds no information (#vfsSpec)"() {
        setupTest(vfsSpec)

        expect:
        initialRoot.store(searchedPath, CASE_SENSITIVE, Mock(MetadataSnapshot), diffListener) is initialRoot
        addedNodes.empty
        removedNodes.empty

        where:
        vfsSpec << onlyDirectChildren(NO_COMMON_PREFIX + SAME_PATH + CHILD_IS_PREFIX)
    }

    def "querying the snapshot for non-existent child #vfsSpec.searchedPath yields a missing file (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        FileSystemLocationSnapshot foundSnapshot = initialRoot.getSnapshot(searchedPath, CASE_SENSITIVE).get() as FileSystemLocationSnapshot
        then:
        foundSnapshot.type == FileType.Missing
        foundSnapshot.absolutePath == searchedPath.absolutePath

        where:
        vfsSpec << onlyDirectChildren(NO_COMMON_PREFIX)
    }

    def "querying the node for non-existent child #vfsSpec.searchedPath yields a missing file (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        FileSystemLocationSnapshot foundSnapshot = initialRoot.getNode(searchedPath, CASE_SENSITIVE).get() as FileSystemLocationSnapshot
        then:
        foundSnapshot.type == FileType.Missing
        foundSnapshot.absolutePath == searchedPath.absolutePath

        where:
        vfsSpec << onlyDirectChildren(NO_COMMON_PREFIX)
    }

    def "querying the snapshot for child #vfsSpec.searchedPath yields the child (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        FileSystemLocationSnapshot foundSnapshot = initialRoot.getSnapshot(searchedPath, CASE_SENSITIVE).get() as FileSystemLocationSnapshot
        then:
        foundSnapshot == selectedChild
        1 * selectedChild.snapshot >> Optional.of(selectedChild)
        interaction { noMoreInteractions() }

        where:
        vfsSpec << onlyDirectChildren(SAME_PATH)
    }

    def "querying the node for child #vfsSpec.searchedPath yields the child (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        FileSystemLocationSnapshot foundSnapshot = initialRoot.getNode(searchedPath, CASE_SENSITIVE).get() as FileSystemLocationSnapshot
        then:
        foundSnapshot == selectedChild
        interaction { noMoreInteractions() }

        where:
        vfsSpec << onlyDirectChildren(SAME_PATH)
    }

    def "querying a snapshot in child #vfsSpec.searchedPath yields the found snapshot (#vfsSpec)"() {
        setupTest(vfsSpec)
        def grandChild = Mock(FileSystemLocationSnapshot)

        when:
        FileSystemLocationSnapshot foundSnapshot = initialRoot.getSnapshot(searchedPath, CASE_SENSITIVE).get() as FileSystemLocationSnapshot
        then:
        foundSnapshot == grandChild
        interaction {
            getDescendantSnapshotOfSelectedChild(grandChild)
            noMoreInteractions()
        }

        where:
        vfsSpec << onlyDirectChildren(CHILD_IS_PREFIX)
    }

    def "querying a node in child #vfsSpec.searchedPath yields the found node (#vfsSpec)"() {
        setupTest(vfsSpec)
        def grandChild = Mock(FileSystemLocationSnapshot)

        when:
        FileSystemLocationSnapshot foundSnapshot = initialRoot.getNode(searchedPath, CASE_SENSITIVE).get() as FileSystemLocationSnapshot
        then:
        foundSnapshot == grandChild
        interaction {
            getDescendantNodeOfSelectedChild(grandChild)
            noMoreInteractions()
        }

        where:
        vfsSpec << onlyDirectChildren(CHILD_IS_PREFIX)
    }

    def "querying a non-existent snapshot in child #vfsSpec.searchedPath yields a missing snapshot (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        FileSystemLocationSnapshot foundSnapshot = initialRoot.getSnapshot(searchedPath, CASE_SENSITIVE).get() as FileSystemLocationSnapshot
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

    def "querying a non-existent node in child #vfsSpec.searchedPath yields a missing snapshot (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        FileSystemLocationSnapshot foundSnapshot = initialRoot.getNode(searchedPath, CASE_SENSITIVE).get() as FileSystemLocationSnapshot
        then:
        foundSnapshot.type == FileType.Missing
        foundSnapshot.absolutePath == searchedPath.absolutePath
        interaction {
            getDescendantNodeOfSelectedChild(null)
            noMoreInteractions()
        }

        where:
        vfsSpec << onlyDirectChildren(CHILD_IS_PREFIX)
    }

    /**
     * Removes all specs which contain compressed paths, since this isn't allowed for the children of {@link DirectorySnapshot}s.
     */
    private static List<VirtualFileSystemTestSpec> onlyDirectChildren(List<VirtualFileSystemTestSpec> fullList) {
        return fullList.findAll { it.childPaths.every { !it.contains('/') } }
    }
}
