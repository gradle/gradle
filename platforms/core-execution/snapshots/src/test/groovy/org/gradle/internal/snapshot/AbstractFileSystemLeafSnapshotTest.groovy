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

import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.FileType

import static org.gradle.internal.file.FileMetadata.AccessType.DIRECT
import static org.gradle.internal.file.FileMetadata.AccessType.VIA_SYMLINK
import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

abstract class AbstractFileSystemLeafSnapshotTest<T extends FileSystemLeafSnapshot> extends AbstractFileSystemLocationSnapshotTest {

    abstract protected T createInitialRootNode(String absolutePath, FileMetadata.AccessType accessType = DIRECT);

    T initialRoot = createInitialRootNode("/some/absolute/path")

    List<FileSystemNode> removedSnapshots = []
    List<FileSystemNode> addedSnapshots = []

    SnapshotHierarchy.NodeDiffListener changeListener = new SnapshotHierarchy.NodeDiffListener() {
        @Override
        void nodeRemoved(FileSystemNode node) {
            removedSnapshots.add(node)
        }

        @Override
        void nodeAdded(FileSystemNode node) {
            addedSnapshots.add(node)
        }
    }

    def "store is ignored"() {
        def snapshot = Mock(MetadataSnapshot)

        expect:
        initialRoot.store(childAbsolutePath("some/child"), CASE_SENSITIVE, snapshot, changeListener) == initialRoot
        removedSnapshots.empty
        addedSnapshots.empty
    }

    def "invalidate removes the node"() {
        expect:
        initialRoot.invalidate(childAbsolutePath("some/child"), CASE_SENSITIVE, changeListener) == Optional.empty()
        removedSnapshots == [initialRoot]
        addedSnapshots.empty
    }

    def "getSnapshot returns itself"() {
        expect:
        initialRoot.getSnapshot() == Optional.of(initialRoot)
    }

    def "getSnapshot of child is missing"() {
        def childAbsolutePath = childAbsolutePath("some/child")

        when:
        FileSystemLocationSnapshot childSnapshot = initialRoot.getSnapshot(childAbsolutePath, CASE_SENSITIVE).get() as FileSystemLocationSnapshot
        then:
        childSnapshot.type == FileType.Missing
        childSnapshot.absolutePath == childAbsolutePath.absolutePath
    }

    def "getNode at child is missing"() {
        def childAbsolutePath = childAbsolutePath("some/child")

        when:
        FileSystemLocationSnapshot childSnapshot = initialRoot.getNode(childAbsolutePath, CASE_SENSITIVE).get() as FileSystemLocationSnapshot
        then:
        childSnapshot.type == FileType.Missing
        childSnapshot.absolutePath == childAbsolutePath.absolutePath
    }

    def "snapshot accessed directly is relocated"() {
        def sourceFile = temporaryFolder.file("source.txt")
        def targetFile = temporaryFolder.file("target.txt")
        def sourceSnapshot = createInitialRootNode(sourceFile.absolutePath, DIRECT)

        when:
        def targetSnapshot = sourceSnapshot.relocate(targetFile.absolutePath, stringInterner)

        then:
        targetSnapshot.present
        targetSnapshot.get().absolutePath == targetFile.absolutePath
        targetSnapshot.get().name == targetFile.name
        assertInterned(targetSnapshot.get())
    }

    def "snapshot accessed via symlink is not relocated"() {
        def sourceFile = temporaryFolder.file("source.txt")
        def targetFile = temporaryFolder.file("target.txt")
        def sourceSnapshot = createInitialRootNode(sourceFile.absolutePath, VIA_SYMLINK)

        when:
        def targetSnapshot = sourceSnapshot.relocate(targetFile.absolutePath, stringInterner)

        then:
        !targetSnapshot.present
    }

    private VfsRelativePath childAbsolutePath(String relativePath) {
        VfsRelativePath.of("${initialRoot.absolutePath}/${relativePath}", initialRoot.absolutePath.length() + 1)
    }
}
