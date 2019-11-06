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

@Unroll
abstract class AbstractIncompleteSnapshotWithChildrenTest<T extends FileSystemNode> extends Specification {

    abstract T createNodeFromFixture(VirtualFileSystemTestFixture fixture);

    abstract boolean isSameNodeType(FileSystemNode node)

    def "invalidate child with no common pathToParent has no effect (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def metadataSnapshot = createNodeFromFixture(vfsFixture)
        expect:
        metadataSnapshot.invalidate(vfsFixture.absolutePath, vfsFixture.offset).get() == metadataSnapshot

        where:
        vfsSpec << NO_COMMON_PREFIX + COMMON_PREFIX
    }


    def "store #fileType child with common prefix adds a new child with the shared prefix of type Directory (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def currentNode = createNodeFromFixture(vfsFixture)
        def newPathToParent = vfsFixture.absolutePath.substring(vfsFixture.offset)
        def snapshot = mockSnapshot(newPathToParent)

        when:
        def stored = currentNode.store(vfsFixture.absolutePath, vfsFixture.offset, snapshot)
        AbstractIncompleteSnapshotWithChildren newChild = vfsFixture.getNodeWithIndexOfSelectedChild(stored.children)
        then:
        stored.children == vfsFixture.withSelectedChildReplacedBy(newChild)
        newChild.pathToParent == vfsFixture.commonPrefix
        newChild.children == sortedChildren(snapshot, vfsFixture.selectedChild)
        newChild.type == FileType.Directory
        1 * snapshot.withPathToParent(snapshot.pathToParent.substring(vfsFixture.commonPrefix.length() + 1)) >> snapshot
        1 * vfsFixture.selectedChild.withPathToParent(vfsFixture.selectedChild.pathToParent.substring(vfsFixture.commonPrefix.length() + 1)) >> vfsFixture.selectedChild
        1 * snapshot.type >> fileType
        interaction { noMoreInteractions() }

        where:
        vfsSpecWithFileType << [COMMON_PREFIX, [FileType.RegularFile, FileType.Directory]].combinations()
        vfsSpec = vfsSpecWithFileType[0]
        fileType = vfsSpecWithFileType[1]
    }

    def "store Missing child with common prefix adds a new child with the shared prefix with unknown metadata (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def currentNode = createNodeFromFixture(vfsFixture)
        def newPathToParent = vfsFixture.absolutePath.substring(vfsFixture.offset)
        def snapshot = mockSnapshot(newPathToParent)

        when:
        def stored = currentNode.store(vfsFixture.absolutePath, vfsFixture.offset, snapshot)
        AbstractIncompleteSnapshotWithChildren newChild = vfsFixture.getNodeWithIndexOfSelectedChild(stored.children)
        then:
        stored.children == vfsFixture.withSelectedChildReplacedBy(newChild)
        newChild.pathToParent == vfsFixture.commonPrefix
        newChild.children == sortedChildren(snapshot, vfsFixture.selectedChild)
        !(newChild instanceof MetadataSnapshot)
        1 * snapshot.withPathToParent(snapshot.pathToParent.substring(vfsFixture.commonPrefix.length() + 1)) >> snapshot
        1 * vfsFixture.selectedChild.withPathToParent(vfsFixture.selectedChild.pathToParent.substring(vfsFixture.commonPrefix.length() + 1)) >> vfsFixture.selectedChild
        1 * snapshot.type >> FileType.Missing
        interaction { noMoreInteractions() }

        where:
        vfsSpec << COMMON_PREFIX
    }

    def "store child with no common prefix adds it (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def metadataSnapshot = createNodeFromFixture(vfsFixture)
        def newPathToParent = vfsFixture.absolutePath.substring(vfsFixture.offset)
        def snapshot = mockSnapshot(newPathToParent)
        def newChild = mockSnapshot(newPathToParent)

        when:
        def stored = metadataSnapshot.store(vfsFixture.absolutePath, vfsFixture.offset, snapshot)
        then:
        stored.children == vfsFixture.withNewChild(newChild)
        isSameNodeType(stored)
        1 * snapshot.withPathToParent(newPathToParent) >> newChild
        interaction { noMoreInteractions() }

        where:
        vfsSpec << NO_COMMON_PREFIX
    }

    def "store parent #vfsSpec.absolutePath replaces child #vfsSpec.selectedChildPath (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def currentNode = createNodeFromFixture(vfsFixture)
        def newPathToParent = vfsFixture.absolutePath.substring(vfsFixture.offset)
        def snapshot = mockSnapshot(newPathToParent)
        def parent = mockSnapshot(newPathToParent)

        when:
        def stored = currentNode.store(vfsFixture.absolutePath, vfsFixture.offset, snapshot)
        then:
        stored.children == vfsFixture.withSelectedChildReplacedBy(parent)
        isSameNodeType(stored)
        1 * snapshot.withPathToParent(newPathToParent) >> parent
        interaction { noMoreInteractions() }

        where:
        vfsSpec << PARENT_PATH
    }

    def "storing a complete snapshot with same path #vfsSpec.absolutePath does replace child (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def partialDirectorySnapshot = createNodeFromFixture(vfsFixture)
        def newPathToParent = vfsFixture.absolutePath.substring(vfsFixture.offset)
        def snapshot = mockSnapshot(CompleteFileSystemLocationSnapshot, newPathToParent)
        def snapshotWithParent = mockSnapshot(CompleteFileSystemLocationSnapshot, newPathToParent)

        when:
        def stored = partialDirectorySnapshot.store(vfsFixture.absolutePath, vfsFixture.offset, snapshot)
        then:
        stored.children == vfsFixture.withSelectedChildReplacedBy(snapshotWithParent)
        isSameNodeType(stored)
        1 * snapshot.withPathToParent(newPathToParent) >> snapshotWithParent
        interaction { noMoreInteractions() }

        where:
        vfsSpec << SAME_PATH
    }

    def "storing a metadata snapshot with same path #vfsSpec.absolutePath does not replace a complete snapshot (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec).withSelectedChildAs(CompleteFileSystemLocationSnapshot)
        def partialDirectorySnapshot = createNodeFromFixture(vfsFixture)
        def newPathToParent = vfsFixture.absolutePath.substring(vfsFixture.offset)
        def snapshot = mockSnapshot(newPathToParent)

        when:
        def stored = partialDirectorySnapshot.store(vfsFixture.absolutePath, vfsFixture.offset, snapshot)
        then:
        stored.children == vfsFixture.children
        isSameNodeType(stored)
        interaction {
            getSelectedChildSnapshot(vfsFixture)
            noMoreInteractions()
        }

        where:
        vfsSpec << SAME_PATH
    }

    def "storing a metadata snapshot with same path #vfsSpec.absolutePath does replace a metadata snapshot (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec).withSelectedChildAs(MetadataSnapshot)
        def partialDirectorySnapshot = createNodeFromFixture(vfsFixture)
        def newPathToParent = vfsFixture.absolutePath.substring(vfsFixture.offset)
        def newSnapshot = mockSnapshot(newPathToParent)

        when:
        def stored = partialDirectorySnapshot.store(vfsFixture.absolutePath, vfsFixture.offset, newSnapshot)
        then:
        isSameNodeType(stored)
        stored.children == vfsFixture.withSelectedChildReplacedBy(newSnapshot)
        interaction {
            getSelectedChildSnapshot(vfsFixture)
            1 * newSnapshot.withPathToParent(newPathToParent) >> newSnapshot
            noMoreInteractions()
        }

        where:
        vfsSpec << SAME_PATH
    }

    def "storing the child #vfsSpec.absolutePath of #vfsSpec.selectedChildPath updates the child (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def partialDirectorySnapshot = createNodeFromFixture(vfsFixture)
        def snapshot = mockSnapshot(vfsFixture.selectedChild.pathToParent)
        def updatedChild = mockChild(vfsFixture.selectedChild.pathToParent)

        when:
        def stored = partialDirectorySnapshot.store(vfsFixture.absolutePath, vfsFixture.offset, snapshot)
        then:
        isSameNodeType(stored)
        stored.children == vfsFixture.withSelectedChildReplacedBy(updatedChild)
        interaction {
            storeDescendantOfSelectedChild(vfsFixture, snapshot, updatedChild)
            noMoreInteractions()
        }

        where:
        vfsSpec << DESCENDANT_PATH
    }

    def "querying the snapshot for non-existing child #vfsSpec.absolutePath finds nothings (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def partialDirectorySnapshot = createNodeFromFixture(vfsFixture)

        when:
        def result = partialDirectorySnapshot.getSnapshot(vfsFixture.absolutePath, vfsFixture.offset)
        then:
        !result.present
        interaction { noMoreInteractions() }

        where:
        vfsSpec << NO_COMMON_PREFIX + COMMON_PREFIX + PARENT_PATH
    }

    def "querying the snapshot for existing child #vfsSpec.absolutePath returns the snapshot for the child (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec).withSelectedChildAs(MetadataSnapshot)
        def partialDirectorySnapshot = createNodeFromFixture(vfsFixture)

        when:
        def result = partialDirectorySnapshot.getSnapshot(vfsFixture.absolutePath, vfsFixture.offset)
        then:
        result.get() == vfsFixture.selectedChild
        interaction {
            getSelectedChildSnapshot(vfsFixture)
            noMoreInteractions()
        }

        where:
        vfsSpec << SAME_PATH
    }

    def "querying the snapshot for existing child #vfsSpec.absolutePath without snapshot returns empty (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec).withSelectedChildAs(MetadataSnapshot)
        def partialDirectorySnapshot = createNodeFromFixture(vfsFixture)

        when:
        def result = partialDirectorySnapshot.getSnapshot(vfsFixture.absolutePath, vfsFixture.offset)
        then:
        !result.present
        interaction {
            getSelectedChildSnapshot(vfsFixture, null)
            noMoreInteractions()
        }

        where:
        vfsSpec << SAME_PATH
    }

    def "querying the snapshot for descendant of child #vfsSpec.selectedChildPath queries the child (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def partialDirectorySnapshot = createNodeFromFixture(vfsFixture)
        def descendantSnapshot = mockSnapshot(vfsFixture.absolutePath.substring(vfsFixture.selectedChild.pathToParent.length() + 1))

        when:
        def result = partialDirectorySnapshot.getSnapshot(vfsFixture.absolutePath, vfsFixture.offset)
        then:
        result.get() == descendantSnapshot
        interaction {
            getDescendantSnapshotOfSelectedChild(vfsFixture, descendantSnapshot)
            noMoreInteractions()
        }

        where:
        vfsSpec << DESCENDANT_PATH
    }

    def "querying the snapshot for non-existing descendant of child #vfsSpec.selectedChildPath returns empty (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def partialDirectorySnapshot = createNodeFromFixture(vfsFixture)

        when:
        def result = partialDirectorySnapshot.getSnapshot(vfsFixture.absolutePath, vfsFixture.offset)
        then:
        !result.present
        interaction {
            getDescendantSnapshotOfSelectedChild(vfsFixture, null)
            noMoreInteractions()
        }

        where:
        vfsSpec << DESCENDANT_PATH
    }

    static final CHILD_CONSTELLATIONS = [
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
    static final List<VirtualFileSystemTestSpec> NO_COMMON_PREFIX = CHILD_CONSTELLATIONS.collectMany { childPaths ->
        childPaths.collect { childPath ->
            def firstSlash = childPath.indexOf('/')
            String newChildPath = firstSlash > -1
                ? "${childPath.substring(0, firstSlash)}0${childPath.substring(firstSlash)}"
                : "${childPath}0"
            new VirtualFileSystemTestSpec(childPaths, newChildPath, 0, null)
        } + new VirtualFileSystemTestSpec(childPaths, 'completelyDifferent', 0, null)
    }
    static final List<VirtualFileSystemTestSpec> COMMON_PREFIX = CHILD_CONSTELLATIONS.collectMany { childPaths ->
        childPaths.findAll { it.contains('/') } collectMany { childPath ->
            parentPaths(childPath).collect {
                new VirtualFileSystemTestSpec(childPaths, "${it}/different", 0, childPath)
            }
        }
    }
    static final List<VirtualFileSystemTestSpec> PARENT_PATH = CHILD_CONSTELLATIONS.collectMany { childPaths ->
        childPaths.findAll { it.contains('/') } collectMany { childPath ->
            parentPaths(childPath).collect { parentPath ->
                new VirtualFileSystemTestSpec(childPaths, parentPath, 0, findPathWithParent(childPaths, parentPath))
            }
        }
    }
    static final List<VirtualFileSystemTestSpec> SAME_PATH = CHILD_CONSTELLATIONS.collectMany { childPaths ->
        childPaths.collect {
            new VirtualFileSystemTestSpec(childPaths, it, 0, it)
        }
    }
    static final List<VirtualFileSystemTestSpec> DESCENDANT_PATH = CHILD_CONSTELLATIONS.collectMany { childPaths ->
        childPaths.collect {
            new VirtualFileSystemTestSpec(childPaths, "${it}/descendant", 0, it)
        }
    }

    def getSelectedChildSnapshot(VirtualFileSystemTestFixture fixture, @Nullable foundSnapshot = fixture.selectedChild) {
        1 * fixture.selectedChild.getSnapshot(fixture.absolutePath, fixture.absolutePath.length() + 1) >> Optional.ofNullable(foundSnapshot)
    }

    def getDescendantSnapshotOfSelectedChild(VirtualFileSystemTestFixture fixture, @Nullable MetadataSnapshot foundSnapshot) {
        def descendantOffset = fixture.offset + fixture.selectedChild.pathToParent.length() + 1
        1 * fixture.selectedChild.getSnapshot(fixture.absolutePath, descendantOffset) >> Optional.ofNullable(foundSnapshot)
    }

    def storeDescendantOfSelectedChild(VirtualFileSystemTestFixture fixture, MetadataSnapshot snapshot, FileSystemNode updatedChild) {
        def descendantOffset = fixture.offset + fixture.selectedChild.pathToParent.length() + 1
        1 * fixture.selectedChild.store(fixture.absolutePath, descendantOffset, snapshot) >> updatedChild
    }

    def invalidateDescendantOfSelectedChild(VirtualFileSystemTestFixture fixture, @Nullable FileSystemNode invalidatedChild) {
        def descendantOffset = fixture.offset + fixture.selectedChild.pathToParent.length() + 1
        1 * fixture.selectedChild.invalidate(fixture.absolutePath, descendantOffset) >> Optional.ofNullable(invalidatedChild)
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    void noMoreInteractions() {
        _ * _.pathToParent
        0 * _
    }

    private static String findPathWithParent(List<String> childPaths, String parentPath) {
        childPaths.find { PathUtil.isChildOfOrThis(it, 0, parentPath) }
    }

    private static List<String> parentPaths(String childPath) {
        (childPath.split('/') as List).inits().tail().init().collect { it.join('/') }
    }


    static List<FileSystemNode> sortedChildren(FileSystemNode... children) {
        def childrenOfIntermediate = children as List
        childrenOfIntermediate.sort(Comparator.comparing({ FileSystemNode node -> node.pathToParent }, PathUtil.pathComparator()))
        childrenOfIntermediate
    }

    FileSystemNode mockChild(String pathToParent) {
        Mock(FileSystemNode, defaultResponse: new RespondWithPathToParent(pathToParent), name: pathToParent)
    }

    def <T extends FileSystemNode> T mockSnapshot(
        Class<T> type,
        String pathToParent
    ) {
        Mock(type, defaultResponse: new RespondWithPathToParent(pathToParent), name: pathToParent)
    }

    MetadataSnapshot mockSnapshot(String pathToParent) {
        mockSnapshot(MetadataSnapshot, pathToParent)
    }

    VirtualFileSystemTestFixture fixture(VirtualFileSystemTestSpec spec) {
        def children = createChildren(spec.childPaths)
        return new VirtualFileSystemTestFixture(
            children,
            spec.absolutePath,
            spec.offset,
            children.find { it.pathToParent == spec.selectedChildPath },
            new VirtualFileSystemTestFixture.MockCreator() {
                @Override
                <T extends FileSystemNode> T create(Class<T> type, String pathToParent) {
                    mockSnapshot(type, pathToParent) as T
                }
            }
        )
    }

    List<FileSystemNode> createChildren(List<String> pathsToParent) {
        return pathsToParent.stream()
            .sorted(PathUtil.pathComparator())
            .map{ it -> mockChild(it) }
            .collect(Collectors.toList())
    }
}
