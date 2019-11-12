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

@Unroll
class PartialDirectorySnapshotTest extends AbstractIncompleteSnapshotWithChildrenTest<PartialDirectorySnapshot> {

    @Override
    protected PartialDirectorySnapshot createInitialRootNode(String pathToParent, List<FileSystemNode> children) {
        return new PartialDirectorySnapshot(pathToParent, children)
    }

    @Override
    protected boolean isSameNodeType(FileSystemNode node) {
        node instanceof MetadataSnapshot && node.type == FileType.Directory
    }

    def "invalidate #vfsSpec.absolutePath removes child #vfsSpec.selectedChildPath (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def invalidated = initialRoot.invalidate(absolutePath, offset).get()
        then:
        invalidated.children == childrenWithSelectedChildRemoved()
        isSameNodeType(invalidated)
        interaction { noMoreInteractions() }

        where:
        vfsSpec << PARENT_PATH + SAME_PATH
    }

    def "invalidate #vfsSpec.absolutePath invalidates children of #vfsSpec.selectedChildPath (#vfsSpec)"() {
        setupTest(vfsSpec)
        def invalidatedChild = mockNode(selectedChild.pathToParent)

        when:
        def invalidated = initialRoot.invalidate(absolutePath, offset).get()
        then:
        invalidated.children == childrenWithSelectedChildReplacedBy(invalidatedChild)
        isSameNodeType(invalidated)
        interaction {
            invalidateDescendantOfSelectedChild(vfsFixture, invalidatedChild)
            noMoreInteractions()
        }

        where:
        vfsSpec << DESCENDANT_PATH
    }

    def "invalidate #vfsSpec.absolutePath removes empty invalidated child #vfsSpec.selectedChildPath (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def invalidated = initialRoot.invalidate(absolutePath, offset).get()
        then:
        invalidated.children == childrenWithSelectedChildRemoved()
        isSameNodeType(invalidated)
        interaction {
            invalidateDescendantOfSelectedChild(vfsFixture, null)
            noMoreInteractions()
        }

        where:
        vfsSpec << DESCENDANT_PATH
    }

    def "returns Directory when queried at root"() {
        def node = new PartialDirectorySnapshot("some/prefix", [])

        when:
        def snapshot = node.getSnapshot("/absolute/some/prefix", "/absolute/some/prefix".length() + 1).get()
        then:
        snapshot.type == FileType.Directory
        0 * _
    }
}
