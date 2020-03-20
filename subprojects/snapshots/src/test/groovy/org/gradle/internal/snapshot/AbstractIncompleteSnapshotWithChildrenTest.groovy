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
import spock.lang.Unroll

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

@Unroll
abstract class AbstractIncompleteSnapshotWithChildrenTest<T extends FileSystemNode> extends AbstractSnapshotWithChildrenTest<T, FileSystemNode> {

    abstract protected boolean isSameNodeType(FileSystemNode node)

    abstract boolean isAllowEmptyChildren()

    def "invalidate child with no common pathToParent has no effect (#vfsSpec)"() {
        setupTest(vfsSpec)

        expect:
        initialRoot.invalidate(searchedPath, CASE_SENSITIVE, changeListener).get() is initialRoot
        removedSnapshots.empty
        addedSnapshots.empty

        where:
        vfsSpec << (NO_COMMON_PREFIX + COMMON_PREFIX).findAll { allowEmptyChildren || !it.childPaths.empty }
    }

    def "store #fileType child with common prefix adds a new child with the shared prefix of type Directory (#vfsSpec)"() {
        setupTest(vfsSpec)
        def newPathToParent = pathFromCommonPrefix
        def snapshot = Mock(MetadataSnapshot)
        def newGrandChild = mockChild(newPathToParent)

        when:
        def resultRoot = initialRoot.store(searchedPath, CASE_SENSITIVE, snapshot, changeListener)
        AbstractIncompleteSnapshotWithChildren newChild = getNodeWithIndexOfSelectedChild(resultRoot.children) as AbstractIncompleteSnapshotWithChildren
        then:
        resultRoot.children == childrenWithSelectedChildReplacedBy(newChild)
        newChild.pathToParent == commonPrefix
        newChild.children == sortedChildren(newGrandChild, selectedChild)
        newChild.snapshot.get().type == FileType.Directory

        addedSnapshots == [newGrandChild]
        removedSnapshots.empty

        1 * snapshot.asFileSystemNode(newPathToParent) >> newGrandChild
        1 * selectedChild.snapshot >> Optional.empty()
        1 * selectedChild.withPathToParent(selectedChildPathFromCommonPrefix) >> selectedChild
        1 * snapshot.type >> fileType
        interaction { noMoreInteractions() }

        where:
        vfsSpecWithFileType << [COMMON_PREFIX, [FileType.RegularFile, FileType.Directory]].combinations()
        vfsSpec = vfsSpecWithFileType[0] as VirtualFileSystemTestSpec
        fileType = vfsSpecWithFileType[1] as FileType
    }

    def "store Missing child with common prefix adds a new child with the shared prefix with unknown metadata (#vfsSpec)"() {
        setupTest(vfsSpec)
        def newPathToParent = pathFromCommonPrefix
        def snapshot = Mock(MetadataSnapshot)
        def newGrandChild = mockChild(newPathToParent)

        when:
        def resultRoot = initialRoot.store(searchedPath, CASE_SENSITIVE, snapshot, changeListener)
        AbstractIncompleteSnapshotWithChildren newChild = getNodeWithIndexOfSelectedChild(resultRoot.children) as AbstractIncompleteSnapshotWithChildren
        then:
        resultRoot.children == childrenWithSelectedChildReplacedBy(newChild)
        newChild.pathToParent == commonPrefix
        newChild.children == sortedChildren(newGrandChild, selectedChild)
        !newChild.snapshot.present

        addedSnapshots == [newGrandChild]
        removedSnapshots.empty

        1 * snapshot.asFileSystemNode(newPathToParent) >> newGrandChild
        1 * selectedChild.snapshot >> Optional.empty()
        1 * selectedChild.withPathToParent(selectedChildPathFromCommonPrefix) >> selectedChild
        1 * snapshot.type >> FileType.Missing
        interaction { noMoreInteractions() }

        where:
        vfsSpec << COMMON_PREFIX
    }

    def "store child with no common prefix adds it (#vfsSpec)"() {
        setupTest(vfsSpec)
        def newPathToParent = searchedPath.asString
        def snapshot = Mock(MetadataSnapshot)
        def newChild = mockChild(newPathToParent)

        when:
        def resultRoot = initialRoot.store(searchedPath, CASE_SENSITIVE, snapshot, changeListener)
        then:
        resultRoot.children == childrenWithAdditionalChild(newChild)
        isSameNodeType(resultRoot)

        addedSnapshots == [newChild]
        removedSnapshots.empty

        1 * snapshot.asFileSystemNode(newPathToParent) >> newChild
        interaction { noMoreInteractions() }

        where:
        vfsSpec << NO_COMMON_PREFIX.findAll { allowEmptyChildren || !it.childPaths.empty }
    }

    def "store parent #vfsSpec.searchedPath replaces child #vfsSpec.selectedChildPath (#vfsSpec)"() {
        setupTest(vfsSpec)
        def newPathToParent = searchedPath.asString
        def snapshot = Mock(MetadataSnapshot)
        def parent = mockChild(newPathToParent)

        when:
        def resultRoot = initialRoot.store(searchedPath, CASE_SENSITIVE, snapshot, changeListener)
        then:
        resultRoot.children == childrenWithSelectedChildReplacedBy(parent)
        isSameNodeType(resultRoot)

        addedSnapshots == [parent]
        removedSnapshots == [selectedChild]

        1 * snapshot.asFileSystemNode(newPathToParent) >> parent
        interaction { noMoreInteractions() }

        where:
        vfsSpec << IS_PREFIX_OF_CHILD
    }

    def "storing a complete snapshot with same path #vfsSpec.searchedPath does replace child (#vfsSpec)"() {
        setupTest(vfsSpec)
        def newPathToParent = searchedPath.asString
        def snapshot = Mock(CompleteFileSystemLocationSnapshot)
        def snapshotWithParent = mockChild(newPathToParent)

        when:
        def resultRoot = initialRoot.store(searchedPath, CASE_SENSITIVE, snapshot, changeListener)
        then:
        resultRoot.children == childrenWithSelectedChildReplacedBy(snapshotWithParent)
        isSameNodeType(resultRoot)

        addedSnapshots == [snapshotWithParent]
        removedSnapshots == [selectedChild]

        1 * snapshot.asFileSystemNode(newPathToParent) >> snapshotWithParent
        interaction { noMoreInteractions() }

        where:
        vfsSpec << SAME_PATH
    }

    def "storing a metadata snapshot with same path #vfsSpec.searchedPath does not replace a complete snapshot (#vfsSpec)"() {
        setupTest(vfsSpec)
        def snapshot = Mock(MetadataSnapshot)

        when:
        def resultRoot = initialRoot.store(searchedPath, CASE_SENSITIVE, snapshot, changeListener)
        then:
        resultRoot.children == children
        isSameNodeType(resultRoot)

        addedSnapshots.empty
        removedSnapshots.empty

        1 * selectedChild.getSnapshot() >> Optional.of(Mock(CompleteFileSystemLocationSnapshot))
        interaction {
            noMoreInteractions()
        }

        where:
        vfsSpec << SAME_PATH
    }

    def "storing a metadata snapshot with same path #vfsSpec.searchedPath does replace a metadata snapshot (#vfsSpec)"() {
        setupTest(vfsSpec)
        def newPathToParent = searchedPath.asString
        def snapshot = Mock(MetadataSnapshot)
        def newNode = mockChild(newPathToParent)

        when:
        def resultRoot = initialRoot.store(searchedPath, CASE_SENSITIVE, snapshot, changeListener)
        then:
        isSameNodeType(resultRoot)
        resultRoot.children == childrenWithSelectedChildReplacedBy(newNode)

        addedSnapshots == [newNode]
        removedSnapshots == [selectedChild]

        1 * selectedChild.getSnapshot() >> Optional.of(Mock(MetadataSnapshot))
        interaction {
            1 * snapshot.asFileSystemNode(newPathToParent) >> newNode
            noMoreInteractions()
        }

        where:
        vfsSpec << SAME_PATH
    }

    def "storing the child #vfsSpec.searchedPath of #vfsSpec.selectedChildPath updates the child (#vfsSpec)"() {
        setupTest(vfsSpec)
        def snapshotToStore = Mock(MetadataSnapshot)
        def updatedChildNode = mockChild(selectedChild.pathToParent)

        when:
        def resultRoot = initialRoot.store(searchedPath, CASE_SENSITIVE, snapshotToStore, changeListener)
        then:
        initialRoot.class == resultRoot.class
        resultRoot.children == childrenWithSelectedChildReplacedBy(updatedChildNode)

        addedSnapshots.empty
        removedSnapshots.empty

        interaction {
            storeDescendantOfSelectedChild(snapshotToStore, updatedChildNode)
            noMoreInteractions()
        }

        where:
        vfsSpec << CHILD_IS_PREFIX
    }

    def storeDescendantOfSelectedChild(MetadataSnapshot snapshot, FileSystemNode updatedChild) {
        def descendantOffset = selectedChild.pathToParent.length() + 1
        1 * selectedChild.store(searchedPath.suffixStartingFrom(descendantOffset), CASE_SENSITIVE, snapshot, changeListener) >> updatedChild
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

    static List<FileSystemNode> sortedChildren(FileSystemNode... children) {
        def childrenOfIntermediate = children as List
        childrenOfIntermediate.sort(Comparator.comparing({ FileSystemNode node -> node.pathToParent }, PathUtil.getPathComparator(CASE_SENSITIVE)))
        childrenOfIntermediate
    }

    @Override
    FileSystemNode mockChild(String pathToParent) {
        Mock(FileSystemNode, defaultResponse: new RespondWithPathToParent(pathToParent), name: pathToParent)
    }
}
