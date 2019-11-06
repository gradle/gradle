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

import com.google.common.collect.ImmutableList
import org.gradle.internal.file.FileType
import spock.lang.Unroll

@Unroll
class PartialDirectorySnapshotTest extends AbstractIncompleteSnapshotWithChildrenTest {

    def "invalidate child with no common pathToParent has no effect (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def metadataSnapshot = partialDirectorySnapshot(vfsFixture)
        expect:
        metadataSnapshot.invalidate(vfsFixture.absolutePath, vfsFixture.offset).get() == metadataSnapshot

        where:
        vfsSpec << NO_COMMON_PREFIX + COMMON_PREFIX
    }

    def "invalidate #vfsSpec.absolutePath removes child #vfsSpec.selectedChildPath (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def metadataSnapshot = partialDirectorySnapshot(vfsFixture)
        when:
        def invalidated = metadataSnapshot.invalidate(vfsFixture.absolutePath, vfsFixture.offset).get()
        then:
        invalidated.children == vfsFixture.withSelectedChildRemoved
        invalidated.type == FileType.Directory
        interaction { noMoreInteractions() }

        where:
        vfsSpec << PARENT_PATH + SAME_PATH
    }

    def "invalidate #vfsSpec.absolutePath invalidates children of #vfsSpec.selectedChildPath (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def metadataSnapshot = partialDirectorySnapshot(vfsFixture)
        def invalidatedChild = mockChild(vfsFixture.selectedChild.pathToParent)
        when:
        def invalidated = metadataSnapshot.invalidate(vfsFixture.absolutePath, vfsFixture.offset).get()
        then:
        invalidated.children == vfsFixture.withSelectedChildReplacedBy(invalidatedChild)
        invalidated.type == FileType.Directory
        interaction {
            invalidateDescendantOfSelectedChild(vfsFixture, invalidatedChild)
            noMoreInteractions()
        }

        where:
        vfsSpec << DESCENDANT_PATH
    }

    def "invalidate #vfsSpec.absolutePath removes empty invalidated child #vfsSpec.selectedChildPath (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def metadataSnapshot = partialDirectorySnapshot(vfsFixture)
        when:
        def invalidated = metadataSnapshot.invalidate(vfsFixture.absolutePath, vfsFixture.offset).get()
        then:
        invalidated.children == vfsFixture.withSelectedChildRemoved
        invalidated.type == FileType.Directory
        interaction {
            invalidateDescendantOfSelectedChild(vfsFixture, null)
            noMoreInteractions()
        }

        where:
        vfsSpec << DESCENDANT_PATH
    }

    def "store child with no common prefix adds it (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def partialDirectorySnapshot = new PartialDirectorySnapshot("some/pathToParent", vfsFixture.children)
        def newPathToParent = vfsFixture.absolutePath.substring(vfsFixture.offset)
        def snapshot = mockSnapshot(newPathToParent)
        def newChild = mockSnapshot(newPathToParent)

        when:
        def stored = partialDirectorySnapshot.store(vfsFixture.absolutePath, vfsFixture.offset, snapshot)
        then:
        stored.children == vfsFixture.withNewChild(newChild)
        1 * snapshot.withPathToParent(newPathToParent) >> newChild
        interaction { noMoreInteractions() }

        where:
        vfsSpec << NO_COMMON_PREFIX
    }

    def "store #fileType child with common prefix adds a new child with the shared prefix of type Directory (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def partialDirectorySnapshot = new PartialDirectorySnapshot("some/pathToParent", vfsFixture.children)
        def newPathToParent = vfsFixture.absolutePath.substring(vfsFixture.offset)
        def snapshot = mockSnapshot(newPathToParent)

        when:
        def stored = partialDirectorySnapshot.store(vfsFixture.absolutePath, vfsFixture.offset, snapshot)
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
        def partialDirectorySnapshot = new PartialDirectorySnapshot("some/pathToParent", vfsFixture.children)
        def newPathToParent = vfsFixture.absolutePath.substring(vfsFixture.offset)
        def snapshot = mockSnapshot(newPathToParent)

        when:
        def stored = partialDirectorySnapshot.store(vfsFixture.absolutePath, vfsFixture.offset, snapshot)
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

    def "store parent #vfsSpec.absolutePath replaces child #vfsSpec.selectedChildPath (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def partialDirectorySnapshot = new PartialDirectorySnapshot("some/pathToParent", vfsFixture.children)
        def newPathToParent = vfsFixture.absolutePath.substring(vfsFixture.offset)
        def snapshot = mockSnapshot(newPathToParent)
        def parent = mockSnapshot(newPathToParent)

        when:
        def stored = partialDirectorySnapshot.store(vfsFixture.absolutePath, vfsFixture.offset, snapshot)
        then:
        stored.children == vfsFixture.withSelectedChildReplacedBy(parent)
        stored.type == FileType.Directory
        1 * snapshot.withPathToParent(newPathToParent) >> parent
        interaction { noMoreInteractions() }

        where:
        vfsSpec << PARENT_PATH
    }

    def "storing a complete snapshot with same path #vfsSpec.absolutePath does replace child (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec)
        def partialDirectorySnapshot = new PartialDirectorySnapshot("some/pathToParent", vfsFixture.children)
        def newPathToParent = vfsFixture.absolutePath.substring(vfsFixture.offset)
        def snapshot = mockSnapshot(CompleteFileSystemLocationSnapshot, newPathToParent)
        def snapshotWithParent = mockSnapshot(CompleteFileSystemLocationSnapshot, newPathToParent)

        when:
        def stored = partialDirectorySnapshot.store(vfsFixture.absolutePath, vfsFixture.offset, snapshot)
        then:
        stored.children == vfsFixture.withSelectedChildReplacedBy(snapshotWithParent)
        stored.type == FileType.Directory
        1 * snapshot.withPathToParent(newPathToParent) >> snapshotWithParent
        interaction { noMoreInteractions() }

        where:
        vfsSpec << SAME_PATH
    }

    def "storing a metadata snapshot with same path #vfsSpec.absolutePath does not replace a complete snapshot (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec).withSelectedChildAs(CompleteFileSystemLocationSnapshot)
        def partialDirectorySnapshot = partialDirectorySnapshot(vfsFixture)
        def newPathToParent = vfsFixture.absolutePath.substring(vfsFixture.offset)
        def snapshot = mockSnapshot(newPathToParent)

        when:
        def stored = partialDirectorySnapshot.store(vfsFixture.absolutePath, vfsFixture.offset, snapshot)
        then:
        stored.children == vfsFixture.children
        stored.type == FileType.Directory
        interaction {
            getSelectedChildSnapshot(vfsFixture)
            noMoreInteractions()
        }

        where:
        vfsSpec << SAME_PATH
    }

    def "storing a metadata snapshot with same path #vfsSpec.absolutePath does replace a metadata snapshot (#vfsSpec)"() {
        def vfsFixture = fixture(vfsSpec).withSelectedChildAs(MetadataSnapshot)
        def partialDirectorySnapshot = partialDirectorySnapshot(vfsFixture)
        def newPathToParent = vfsFixture.absolutePath.substring(vfsFixture.offset)
        def newSnapshot = mockSnapshot(newPathToParent)

        when:
        def stored = partialDirectorySnapshot.store(vfsFixture.absolutePath, vfsFixture.offset, newSnapshot)
        then:
        stored.type == FileType.Directory
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
        def partialDirectorySnapshot = partialDirectorySnapshot(vfsFixture)
        def snapshot = mockSnapshot(vfsFixture.selectedChild.pathToParent)
        def updatedChild = mockChild(vfsFixture.selectedChild.pathToParent)

        when:
        def stored = partialDirectorySnapshot.store(vfsFixture.absolutePath, vfsFixture.offset, snapshot)
        then:
        stored.type == FileType.Directory
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
        def partialDirectorySnapshot = partialDirectorySnapshot(vfsFixture)

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
        def partialDirectorySnapshot = partialDirectorySnapshot(vfsFixture)

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
        def partialDirectorySnapshot = partialDirectorySnapshot(vfsFixture)

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
        def partialDirectorySnapshot = partialDirectorySnapshot(vfsFixture)
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
        def partialDirectorySnapshot = partialDirectorySnapshot(vfsFixture)

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

    def "can obtain metadata from Directory"() {
        def metadataSnapshot = new PartialDirectorySnapshot("some/prefix", ImmutableList.of())
        expect:
        metadataSnapshot.getSnapshot("/absolute/path", "/absolute/path".length() + 1).get() == metadataSnapshot
        !metadataSnapshot.getSnapshot("another/path", 0).present
    }

    private static PartialDirectorySnapshot partialDirectorySnapshot(VirtualFileSystemTestFixture vfsFixture) {
        new PartialDirectorySnapshot("some/pathToParent", vfsFixture.children)
    }
}
