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

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

abstract class AbstractIncompleteFileSystemNodeTest<T extends FileSystemNode> extends AbstractFileSystemNodeWithChildrenTest<T, FileSystemNode> {

    abstract protected boolean isSameNodeType(FileSystemNode node)

    abstract boolean isAllowEmptyChildren()

    def "invalidate child with no common pathToParent has no effect (#vfsSpec)"() {
        setupTest(vfsSpec)

        expect:
        initialRoot.invalidate(searchedPath, CASE_SENSITIVE, diffListener).get() is initialRoot
        removedNodes.empty
        addedNodes.empty

        where:
        vfsSpec << (NO_COMMON_PREFIX + COMMON_PREFIX).findAll { allowEmptyChildren || !it.childPaths.empty }
    }

    def "store #fileType child with common prefix adds a new child with the shared prefix of type Directory (#vfsSpec)"() {
        setupTest(vfsSpec)
        def snapshot = Mock(MetadataSnapshot)
        def newGrandChildPath = pathFromCommonPrefix
        def newGrandChild = Mock(FileSystemNode)

        when:
        def resultRoot = initialRoot.store(searchedPath, CASE_SENSITIVE, snapshot, diffListener)
        AbstractIncompleteFileSystemNode newChild = getNodeWithIndexOfSelectedChild(resultRoot.children) as AbstractIncompleteFileSystemNode
        then:
        resultRoot.children == childrenWithSelectedChildReplacedBy(commonPrefix, newChild)
        newChild.children == sortedChildren(
            newGrandChildPath, newGrandChild,
            selectedChildPathFromCommonPrefix, selectedChild
        )
        newChild.snapshot.get().type == FileType.Directory

        addedNodes == [newGrandChild]
        removedNodes.empty

        1 * snapshot.asFileSystemNode() >> newGrandChild
        _ * newGrandChild.snapshot >> Optional.of(snapshot)
        _ * selectedChild.snapshot >> Optional.empty()
        1 * snapshot.type >> fileType
        interaction { noMoreInteractions() }

        where:
        vfsSpecWithFileType << [COMMON_PREFIX, [FileType.RegularFile, FileType.Directory]].combinations()
        vfsSpec = vfsSpecWithFileType[0] as VirtualFileSystemTestSpec
        fileType = vfsSpecWithFileType[1] as FileType
    }

    def "store Missing child with common prefix adds a new child with the shared prefix with unknown metadata (#vfsSpec)"() {
        setupTest(vfsSpec)
        def snapshot = Mock(MetadataSnapshot)
        def newGrandChildPath = pathFromCommonPrefix
        def newGrandChild = mockChild()

        when:
        def resultRoot = initialRoot.store(searchedPath, CASE_SENSITIVE, snapshot, diffListener)
        AbstractIncompleteFileSystemNode newChild = getNodeWithIndexOfSelectedChild(resultRoot.children) as AbstractIncompleteFileSystemNode
        then:
        resultRoot.children == childrenWithSelectedChildReplacedBy(commonPrefix, newChild)
        newChild.children == sortedChildren(
            newGrandChildPath, newGrandChild,
            selectedChildPathFromCommonPrefix, selectedChild
        )
        !newChild.snapshot.present

        addedNodes == [newGrandChild]
        removedNodes.empty

        1 * snapshot.asFileSystemNode() >> newGrandChild
        _ * newGrandChild.snapshot >> Optional.of(snapshot)
        _ * selectedChild.snapshot >> Optional.empty()
        1 * snapshot.type >> FileType.Missing
        interaction { noMoreInteractions() }

        where:
        vfsSpec << COMMON_PREFIX
    }

    def "store child with no common prefix adds it (#vfsSpec)"() {
        setupTest(vfsSpec)
        def snapshot = Mock(MetadataSnapshot)
        def newChildPath = searchedPath.asString
        def newChild = mockChild()

        when:
        def resultRoot = initialRoot.store(searchedPath, CASE_SENSITIVE, snapshot, diffListener)
        then:
        resultRoot.children == childrenWithAdditionalChild(newChildPath, newChild)
        isSameNodeType(resultRoot)

        addedNodes == [newChild]
        removedNodes.empty

        1 * snapshot.asFileSystemNode() >> newChild
        interaction { noMoreInteractions() }

        where:
        vfsSpec << NO_COMMON_PREFIX.findAll { allowEmptyChildren || !it.childPaths.empty }
    }

    def "store parent #vfsSpec.searchedPath replaces child #vfsSpec.selectedChildPath (#vfsSpec)"() {
        setupTest(vfsSpec)
        def newChildPath = searchedPath.asString
        def snapshot = Mock(MetadataSnapshot)
        def parent = mockChild()

        when:
        def resultRoot = initialRoot.store(searchedPath, CASE_SENSITIVE, snapshot, diffListener)
        then:
        resultRoot.children == childrenWithSelectedChildReplacedBy(newChildPath, parent)
        isSameNodeType(resultRoot)

        addedNodes == [parent]
        removedNodes == [selectedChild]

        1 * snapshot.asFileSystemNode() >> parent
        interaction { noMoreInteractions() }

        where:
        vfsSpec << IS_PREFIX_OF_CHILD
    }

    def "storing a file system snapshot with same path #vfsSpec.searchedPath does replace child (#vfsSpec)"() {
        setupTest(vfsSpec)
        def snapshot = Mock(FileSystemLocationSnapshot)
        def newChild = mockChild()

        when:
        def resultRoot = initialRoot.store(searchedPath, CASE_SENSITIVE, snapshot, diffListener)
        then:
        resultRoot.children == childrenWithSelectedChildReplacedBy(newChild)
        isSameNodeType(resultRoot)

        addedNodes == [newChild]
        removedNodes == [selectedChild]

        1 * snapshot.asFileSystemNode() >> newChild
        _ * newChild.snapshot >> snapshot
        interaction { noMoreInteractions() }

        where:
        vfsSpec << SAME_PATH
    }

    def "storing a metadata snapshot with same path #vfsSpec.searchedPath does not replace a complete snapshot (#vfsSpec)"() {
        setupTest(vfsSpec)
        def snapshot = Mock(MetadataSnapshot)

        when:
        def resultRoot = initialRoot.store(searchedPath, CASE_SENSITIVE, snapshot, diffListener)
        then:
        resultRoot.children == children
        isSameNodeType(resultRoot)

        addedNodes.empty
        removedNodes.empty

        1 * selectedChild.getSnapshot() >> Optional.of(Mock(FileSystemLocationSnapshot))
        interaction {
            noMoreInteractions()
        }

        where:
        vfsSpec << SAME_PATH
    }

    def "storing a metadata snapshot with same path #vfsSpec.searchedPath does replace a metadata snapshot (#vfsSpec)"() {
        setupTest(vfsSpec)
        def snapshot = Mock(MetadataSnapshot)
        def newNode = mockChild()

        when:
        def resultRoot = initialRoot.store(searchedPath, CASE_SENSITIVE, snapshot, diffListener)
        then:
        isSameNodeType(resultRoot)
        resultRoot.children == childrenWithSelectedChildReplacedBy(newNode)

        addedNodes == [newNode]
        removedNodes == [selectedChild]

        1 * selectedChild.getSnapshot() >> Optional.of(Mock(MetadataSnapshot))
        interaction {
            1 * snapshot.asFileSystemNode() >> newNode
            noMoreInteractions()
        }

        where:
        vfsSpec << SAME_PATH
    }

    def "storing the child #vfsSpec.searchedPath of #vfsSpec.selectedChildPath updates the child (#vfsSpec)"() {
        setupTest(vfsSpec)
        def snapshotToStore = Mock(MetadataSnapshot)
        def updatedChildNode = mockChild()

        when:
        def resultRoot = initialRoot.store(searchedPath, CASE_SENSITIVE, snapshotToStore, diffListener)
        then:
        initialRoot.class == resultRoot.class
        resultRoot.children == childrenWithSelectedChildReplacedBy(updatedChildNode)

        addedNodes.empty
        removedNodes.empty

        interaction {
            storeDescendantOfSelectedChild(snapshotToStore, updatedChildNode)
            noMoreInteractions()
        }

        where:
        vfsSpec << CHILD_IS_PREFIX
    }

    def storeDescendantOfSelectedChild(MetadataSnapshot snapshot, FileSystemNode updatedChild) {
        1 * selectedChild.store(searchedPath.pathFromChild(selectedChildPath), CASE_SENSITIVE, snapshot, diffListener) >> updatedChild
    }

    def "querying the snapshot for non-existing child #vfsSpec.searchedPath finds nothings (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.getSnapshot(searchedPath, CASE_SENSITIVE)
        then:
        !resultRoot.present
        interaction { noMoreInteractions() }

        where:
        vfsSpec << (NO_COMMON_PREFIX + COMMON_PREFIX + IS_PREFIX_OF_CHILD).findAll { allowEmptyChildren || !it.childPaths.empty}
    }

    def "querying for non-existing child #vfsSpec.searchedPath finds nothings (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.getNode(searchedPath, CASE_SENSITIVE)
        then:
        !resultRoot.present
        interaction { noMoreInteractions() }

        where:
        vfsSpec << (NO_COMMON_PREFIX + COMMON_PREFIX).findAll { allowEmptyChildren || !it.childPaths.empty}
    }

    def "querying for parent #vfsSpec.searchedPath of child #vfsSpec.selectedChildPath finds adapted child (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.getNode(searchedPath, CASE_SENSITIVE)

        then:
        resultRoot.get() == selectedChild
        interaction {
            noMoreInteractions()
        }

        where:
        vfsSpec << IS_PREFIX_OF_CHILD
    }

    def "querying the snapshot for existing child #vfsSpec.searchedPath returns the snapshot for the child (#vfsSpec)"() {
        setupTest(vfsSpec)
        def existingSnapshot = Mock(MetadataSnapshot)

        when:
        def resultRoot = initialRoot.getSnapshot(searchedPath, CASE_SENSITIVE)
        then:
        resultRoot.get() == existingSnapshot

        1 * selectedChild.getSnapshot() >> Optional.of(existingSnapshot)
        interaction {
            noMoreInteractions()
        }

        where:
        vfsSpec << SAME_PATH
    }

    def "querying the snapshot for existing child #vfsSpec.searchedPath without snapshot returns empty (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.getSnapshot(searchedPath, CASE_SENSITIVE)
        then:
        !resultRoot.present
        1 * selectedChild.getSnapshot() >> Optional.empty()
        interaction {
            noMoreInteractions()
        }

        where:
        vfsSpec << SAME_PATH
    }

    def "querying for existing child #vfsSpec.searchedPath returns the child (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.getNode(searchedPath, CASE_SENSITIVE)
        then:
        resultRoot.get() == selectedChild

        interaction {
            noMoreInteractions()
        }

        where:
        vfsSpec << SAME_PATH
    }

    def "querying the snapshot for descendant of child #vfsSpec.selectedChildPath queries the child (#vfsSpec)"() {
        setupTest(vfsSpec)
        def descendantSnapshot = Mock(MetadataSnapshot)

        when:
        def resultRoot = initialRoot.getSnapshot(searchedPath, CASE_SENSITIVE)
        then:
        resultRoot.get() == descendantSnapshot
        interaction {
            getDescendantSnapshotOfSelectedChild(descendantSnapshot)
            noMoreInteractions()
        }

        where:
        vfsSpec << CHILD_IS_PREFIX
    }

    def "querying the snapshot for non-existing descendant of child #vfsSpec.selectedChildPath returns empty (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.getSnapshot(searchedPath, CASE_SENSITIVE)
        then:
        !resultRoot.present
        interaction {
            getDescendantSnapshotOfSelectedChild(null)
            noMoreInteractions()
        }

        where:
        vfsSpec << CHILD_IS_PREFIX
    }

    def "querying for descendant of child #vfsSpec.selectedChildPath queries the child (#vfsSpec)"() {
        setupTest(vfsSpec)
        def descendantNode = Mock(FileSystemNode)

        when:
        def resultRoot = initialRoot.getNode(searchedPath, CASE_SENSITIVE)
        then:
        resultRoot.get() == descendantNode
        interaction {
            getDescendantNodeOfSelectedChild(descendantNode)
            noMoreInteractions()
        }

        where:
        vfsSpec << CHILD_IS_PREFIX
    }

    static ChildMap<FileSystemNode> sortedChildren(String path1, FileSystemNode child1, String path2, FileSystemNode child2) {
        def compared = PathUtil.getPathComparator(CASE_SENSITIVE).compare(path1, path2)
        def entry1 = new ChildMap.Entry<FileSystemNode>(path1, child1)
        def entry2 = new ChildMap.Entry<FileSystemNode>(path2, child2)
        return compared < 0 ? ChildMapFactory.childMapFromSorted([entry1, entry2]) : ChildMapFactory.childMapFromSorted([entry2, entry1])
    }

    @Override
    FileSystemNode mockChild() {
        Mock(FileSystemNode)
    }
}
