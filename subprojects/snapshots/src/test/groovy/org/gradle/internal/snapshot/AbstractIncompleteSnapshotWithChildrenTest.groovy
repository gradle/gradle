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
import spock.lang.Specification
import spock.lang.Unroll

import javax.annotation.Nullable
import java.util.stream.Collectors

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

@Unroll
abstract class AbstractIncompleteSnapshotWithChildrenTest<T extends FileSystemNode> extends Specification {

    FileSystemNode initialRoot
    List<FileSystemNode> children
    String absolutePath
    int offset
    FileSystemNode selectedChild

    abstract protected T createInitialRootNode(String pathToParent, List<FileSystemNode> children);

    abstract protected boolean isSameNodeType(FileSystemNode node)

    def "invalidate child with no common pathToParent has no effect (#vfsSpec)"() {
        setupTest(vfsSpec)

        expect:
        initialRoot.invalidate(absolutePath, offset, CASE_SENSITIVE).get() is initialRoot

        where:
        vfsSpec << NO_COMMON_PREFIX + COMMON_PREFIX
    }

    def "store #fileType child with common prefix adds a new child with the shared prefix of type Directory (#vfsSpec)"() {
        setupTest(vfsSpec)
        def newPathToParent = pathFromCommonPrefix
        def snapshot = Mock(MetadataSnapshot)
        def newGrandChild = mockNode(newPathToParent)

        when:
        def resultRoot = initialRoot.store(absolutePath, offset, CASE_SENSITIVE, snapshot)
        AbstractIncompleteSnapshotWithChildren newChild = getNodeWithIndexOfSelectedChild(resultRoot.children) as AbstractIncompleteSnapshotWithChildren
        then:
        resultRoot.children == childrenWithSelectedChildReplacedBy(newChild)
        newChild.pathToParent == commonPrefix
        newChild.children == sortedChildren(newGrandChild, selectedChild)
        newChild.snapshot.get().type == FileType.Directory
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
        def newGrandChild = mockNode(newPathToParent)

        when:
        def resultRoot = initialRoot.store(absolutePath, offset, CASE_SENSITIVE, snapshot)
        AbstractIncompleteSnapshotWithChildren newChild = getNodeWithIndexOfSelectedChild(resultRoot.children) as AbstractIncompleteSnapshotWithChildren
        then:
        resultRoot.children == childrenWithSelectedChildReplacedBy(newChild)
        newChild.pathToParent == commonPrefix
        newChild.children == sortedChildren(newGrandChild, selectedChild)
        !newChild.snapshot.present
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
        def newPathToParent = absolutePath.substring(offset)
        def snapshot = Mock(MetadataSnapshot)
        def newChild = mockNode()

        when:
        def resultRoot = initialRoot.store(absolutePath, offset, CASE_SENSITIVE, snapshot)
        then:
        resultRoot.children == childrenWithAdditionalChild(newChild)
        isSameNodeType(resultRoot)
        1 * snapshot.asFileSystemNode(newPathToParent) >> newChild
        interaction { noMoreInteractions() }

        where:
        vfsSpec << NO_COMMON_PREFIX
    }

    def "store parent #vfsSpec.absolutePath replaces child #vfsSpec.selectedChildPath (#vfsSpec)"() {
        setupTest(vfsSpec)
        def newPathToParent = absolutePath.substring(offset)
        def snapshot = Mock(MetadataSnapshot)
        def parent = mockNode()

        when:
        def resultRoot = initialRoot.store(absolutePath, offset, CASE_SENSITIVE, snapshot)
        then:
        resultRoot.children == childrenWithSelectedChildReplacedBy(parent)
        isSameNodeType(resultRoot)
        1 * snapshot.asFileSystemNode(newPathToParent) >> parent
        interaction { noMoreInteractions() }

        where:
        vfsSpec << IS_PREFIX_OF_CHILD
    }

    def "storing a complete snapshot with same path #vfsSpec.absolutePath does replace child (#vfsSpec)"() {
        setupTest(vfsSpec)
        def newPathToParent = absolutePath.substring(offset)
        def snapshot = Mock(CompleteFileSystemLocationSnapshot)
        def snapshotWithParent = mockNode(newPathToParent)

        when:
        def resultRoot = initialRoot.store(absolutePath, offset, CASE_SENSITIVE, snapshot)
        then:
        resultRoot.children == childrenWithSelectedChildReplacedBy(snapshotWithParent)
        isSameNodeType(resultRoot)
        1 * snapshot.asFileSystemNode(newPathToParent) >> snapshotWithParent
        interaction { noMoreInteractions() }

        where:
        vfsSpec << SAME_PATH
    }

    def "storing a metadata snapshot with same path #vfsSpec.absolutePath does not replace a complete snapshot (#vfsSpec)"() {
        setupTest(vfsSpec)
        def snapshot = Mock(MetadataSnapshot)

        when:
        def resultRoot = initialRoot.store(absolutePath, offset, CASE_SENSITIVE, snapshot)
        then:
        resultRoot.children == children
        isSameNodeType(resultRoot)
        interaction {
            getSelectedChildSnapshot(Mock(CompleteFileSystemLocationSnapshot))
            noMoreInteractions()
        }

        where:
        vfsSpec << SAME_PATH
    }

    def "storing a metadata snapshot with same path #vfsSpec.absolutePath does replace a metadata snapshot (#vfsSpec)"() {
        setupTest(vfsSpec)
        def newPathToParent = absolutePath.substring(offset)
        def snapshot = Mock(MetadataSnapshot)
        def newNode = mockNode(newPathToParent)

        when:
        def resultRoot = initialRoot.store(absolutePath, offset, CASE_SENSITIVE, snapshot)
        then:
        isSameNodeType(resultRoot)
        resultRoot.children == childrenWithSelectedChildReplacedBy(newNode)
        interaction {
            getSelectedChildSnapshot(Mock(MetadataSnapshot))
            1 * snapshot.asFileSystemNode(newPathToParent) >> newNode
            noMoreInteractions()
        }

        where:
        vfsSpec << SAME_PATH
    }

    def "storing the child #vfsSpec.absolutePath of #vfsSpec.selectedChildPath updates the child (#vfsSpec)"() {
        setupTest(vfsSpec)
        def snapshotToStore = Mock(MetadataSnapshot)
        def updatedChildNode = mockNode(selectedChild.pathToParent)

        when:
        def resultRoot = initialRoot.store(absolutePath, offset, CASE_SENSITIVE, snapshotToStore)
        then:
        initialRoot.class == resultRoot.class
        resultRoot.children == childrenWithSelectedChildReplacedBy(updatedChildNode)
        interaction {
            storeDescendantOfSelectedChild(snapshotToStore, updatedChildNode)
            noMoreInteractions()
        }

        where:
        vfsSpec << CHILD_IS_PREFIX
    }

    def storeDescendantOfSelectedChild(MetadataSnapshot snapshot, FileSystemNode updatedChild) {
        def descendantOffset = offset + selectedChild.pathToParent.length() + 1
        1 * selectedChild.store(absolutePath, descendantOffset, CASE_SENSITIVE, snapshot) >> updatedChild
    }

    def "querying the snapshot for non-existing child #vfsSpec.absolutePath finds nothings (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.getSnapshot(absolutePath, offset, CASE_SENSITIVE)
        then:
        !resultRoot.present
        interaction { noMoreInteractions() }

        where:
        vfsSpec << NO_COMMON_PREFIX + COMMON_PREFIX + IS_PREFIX_OF_CHILD
    }

    def "querying the snapshot for existing child #vfsSpec.absolutePath returns the snapshot for the child (#vfsSpec)"() {
        setupTest(vfsSpec)
        def existingSnapshot = Mock(MetadataSnapshot)

        when:
        def resultRoot = initialRoot.getSnapshot(absolutePath, offset, CASE_SENSITIVE)
        then:
        resultRoot.get() == existingSnapshot

        interaction {
            getSelectedChildSnapshot(existingSnapshot)
            noMoreInteractions()
        }

        where:
        vfsSpec << SAME_PATH
    }

    def "querying the snapshot for existing child #vfsSpec.absolutePath without snapshot returns empty (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.getSnapshot(absolutePath, offset, CASE_SENSITIVE)
        then:
        !resultRoot.present
        interaction {
            getSelectedChildSnapshot(null)
            noMoreInteractions()
        }

        where:
        vfsSpec << SAME_PATH
    }

    def "querying the snapshot for descendant of child #vfsSpec.selectedChildPath queries the child (#vfsSpec)"() {
        setupTest(vfsSpec)
        def descendantSnapshot = Mock(MetadataSnapshot)

        when:
        def resultRoot = initialRoot.getSnapshot(absolutePath, offset, CASE_SENSITIVE)
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
        def resultRoot = initialRoot.getSnapshot(absolutePath, offset, CASE_SENSITIVE)
        then:
        !resultRoot.present
        interaction {
            getDescendantSnapshotOfSelectedChild(null)
            noMoreInteractions()
        }

        where:
        vfsSpec << CHILD_IS_PREFIX
    }

    /**
     * Different lists of relative paths of the initial children of the node under test.
     */
    static final INITIAL_CHILD_CONSTELLATIONS = [
        ['name'],
        ['name', 'name1'],
        ['name', 'name1', 'name12'],
        ['name', 'name1', 'name12', 'name2', 'name21'],
        ['name/some'],
        ['name/some/other'],
        ['name/some', 'name2'],
        ['name/some', 'name2/other'],
        ['name/some', 'name2/other'],
        ['name', 'name1/some', 'name2/other/third'],
        ['aa/b1', 'ab/a1', 'name', 'name1/some', 'name2/other/third']
    ]

    /**
     * The queried/updated path has no common prefix with any of the initial children of the node under test.
     *
     * E.g.
     *   path: 'name0/some
     *   children: ['name/some', 'other']
     * or
     *   path: 'completelyDifferent'
     *   children: ['name/some', 'other']
     */
    static final List<VirtualFileSystemTestSpec> NO_COMMON_PREFIX = INITIAL_CHILD_CONSTELLATIONS.collectMany { childPaths ->
        childPaths.collect { childPath ->
            def firstSlash = childPath.indexOf('/')
            String newChildPath = firstSlash > -1
                ? "${childPath.substring(0, firstSlash)}0${childPath.substring(firstSlash)}"
                : "${childPath}0"
            new VirtualFileSystemTestSpec(childPaths, newChildPath, 0, null)
        } + new VirtualFileSystemTestSpec(childPaths, 'completelyDifferent', 0, null)
    }

    /**
     * The queried/updated path has a true common prefix with one of the initial children of the node under test.
     *
     * E.g.
     *   path: 'name/different'
     *   children: ['name/some', 'other']
     */
    static final List<VirtualFileSystemTestSpec> COMMON_PREFIX = INITIAL_CHILD_CONSTELLATIONS.collectMany { childPaths ->
        childPaths.findAll { it.contains('/') } collectMany { childPath ->
            parentPaths(childPath).collect {
                new VirtualFileSystemTestSpec(childPaths, "${it}/different", 0, childPath)
            }
        }
    }

    /**
     * The queried/updated path is a prefix of one of the initial children of the node under test.
     *
     * E.g.
     *   path: 'name'
     *   children: ['name/some', 'other']
     */
    static final List<VirtualFileSystemTestSpec> IS_PREFIX_OF_CHILD = INITIAL_CHILD_CONSTELLATIONS.collectMany { childPaths ->
        childPaths.findAll { it.contains('/') } collectMany { childPath ->
            parentPaths(childPath).collect { parentPath ->
                new VirtualFileSystemTestSpec(childPaths, parentPath, 0, findPathWithParent(childPaths, parentPath))
            }
        }
    }

    /**
     * The queried/updated path is one of the initial children of the node under test.
     *
     * E.g.
     *   path: 'name/some'
     *   children: ['name/some', 'other']
     */
    static final List<VirtualFileSystemTestSpec> SAME_PATH = INITIAL_CHILD_CONSTELLATIONS.collectMany { childPaths ->
        childPaths.collect {
            new VirtualFileSystemTestSpec(childPaths, it, 0, it)
        }
    }

    /**
     * One of the initial children of the node under test is a prefix of the queried/updated path.
     *
     * E.g.
     *   path: 'name/some/descendant'
     *   children: ['name/some', 'other']
     */
    static final List<VirtualFileSystemTestSpec> CHILD_IS_PREFIX = INITIAL_CHILD_CONSTELLATIONS.collectMany { childPaths ->
        childPaths.collect {
            new VirtualFileSystemTestSpec(childPaths, "${it}/descendant", 0, it)
        }
    }

    def getSelectedChildSnapshot(@Nullable MetadataSnapshot foundSnapshot = null) {
        1 * selectedChild.getSnapshot() >> Optional.ofNullable(foundSnapshot)
    }

    def getDescendantSnapshotOfSelectedChild(@Nullable MetadataSnapshot foundSnapshot) {
        def descendantOffset = offset + selectedChild.pathToParent.length() + 1
        1 * selectedChild.getSnapshot(absolutePath, descendantOffset, CASE_SENSITIVE) >> Optional.ofNullable(foundSnapshot)
    }

    def invalidateDescendantOfSelectedChild(@Nullable FileSystemNode invalidatedChild) {
        def descendantOffset = offset + selectedChild.pathToParent.length() + 1
        1 * selectedChild.invalidate(absolutePath, descendantOffset, CASE_SENSITIVE) >> Optional.ofNullable(invalidatedChild)
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    void noMoreInteractions() {
        _ * _.pathToParent
        0 * _
    }

    private static String findPathWithParent(List<String> childPaths, String parentPath) {
        childPaths.find { PathUtil.hasPrefix(parentPath, it, 0, CASE_SENSITIVE) }
    }

    private static List<String> parentPaths(String childPath) {
        (childPath.split('/') as List).inits().tail().init().collect { it.join('/') }
    }


    static List<FileSystemNode> sortedChildren(FileSystemNode... children) {
        def childrenOfIntermediate = children as List
        childrenOfIntermediate.sort(Comparator.comparing({ FileSystemNode node -> node.pathToParent }, PathUtil.getPathComparator(CASE_SENSITIVE)))
        childrenOfIntermediate
    }

    FileSystemNode mockNode(String pathToParent) {
        Mock(FileSystemNode, defaultResponse: new RespondWithPathToParent(pathToParent), name: pathToParent)
    }

    void setupTest(VirtualFileSystemTestSpec spec) {
        def children = createChildren(spec.childPaths)
        def initialRoot = createInitialRootNode("path/to/parent", children)
        this.initialRoot = initialRoot
        this.children = children
        this.absolutePath = spec.absolutePath
        this.offset = spec.offset
        this.selectedChild = children.find { it.pathToParent == spec.selectedChildPath }
    }

    List<FileSystemNode> createChildren(List<String> pathsToParent) {
        return pathsToParent.stream()
            .sorted(PathUtil.getPathComparator(CASE_SENSITIVE))
            .map{ childPath -> mockNode(childPath) }
            .collect(Collectors.toList())
    }

    List<FileSystemNode> childrenWithSelectedChildReplacedBy(FileSystemNode replacement) {
        children.collect { it == selectedChild ? replacement : it }
    }

    List<FileSystemNode> childrenWithAdditionalChild(FileSystemNode newChild) {
        List<FileSystemNode> newChildren = new ArrayList<>(children)
        newChildren.add(insertionPoint, newChild)
        return newChildren
    }

    List<FileSystemNode> childrenWithSelectedChildRemoved() {
        children.findAll { it != selectedChild }
    }

    FileSystemNode getNodeWithIndexOfSelectedChild(List<FileSystemNode> newChildren) {
        int index = children.indexOf(selectedChild)
        return newChildren[index]
    }

    private int getInsertionPoint() {
        int childIndex = SearchUtil.binarySearch(children) {
            candidate -> PathUtil.compareFirstSegment(absolutePath, offset, candidate.pathToParent, CASE_SENSITIVE)
        }
        assert childIndex < 0
        return -childIndex - 1
    }

    String getCommonPrefix() {
        return selectedChild.pathToParent.substring(0, PathUtil.lengthOfCommonPrefix(selectedChild.pathToParent, absolutePath, offset, CASE_SENSITIVE))
    }

    String getPathFromCommonPrefix() {
        return absolutePath.substring(offset + commonPrefix.length() + 1)
    }

    String getSelectedChildPathFromCommonPrefix() {
        return selectedChild.pathToParent.substring(commonPrefix.length() + 1)
    }
}
