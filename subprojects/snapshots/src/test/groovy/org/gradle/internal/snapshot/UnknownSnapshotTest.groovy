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

import spock.lang.Unroll

@Unroll
class UnknownSnapshotTest extends AbstractIncompleteSnapshotWithChildrenTest<UnknownSnapshot> {

    @Override
    protected UnknownSnapshot createInitialRootNode(String pathToParent, List<FileSystemNode> children) {
        return new UnknownSnapshot(pathToParent, children)
    }

    @Override
    protected boolean isSameNodeType(FileSystemNode node) {
        return node instanceof UnknownSnapshot
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
        vfsSpec << (PARENT_PATH + SAME_PATH).findAll { it.childPaths.size() > 1 }
    }

    def "invalidating the only child by #vfsSpec.absolutePath removes the node (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def invalidated = initialRoot.invalidate(absolutePath, offset)
        then:
        !invalidated.present
        interaction { noMoreInteractions() }

        where:
        vfsSpec << (PARENT_PATH + SAME_PATH).findAll { it.childPaths.size() == 1 }
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
        vfsSpec << DESCENDANT_PATH.findAll { it.childPaths.size() > 1 }
    }

    def "invalidate #vfsSpec.absolutePath removes the child #vfsSpec.selectedChildPath and the node with it (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def invalidated = initialRoot.invalidate(absolutePath, offset)
        then:
        !invalidated.present
        interaction {
            invalidateDescendantOfSelectedChild(vfsFixture, null)
            noMoreInteractions()
        }

        where:
        vfsSpec << DESCENDANT_PATH.findAll { it.childPaths.size() == 1 }
    }

    def "returns empty when queried at root"() {
        def node = new UnknownSnapshot("some/prefix", createChildren(["myFile.txt"]))

        when:
        def snapshot = node.getSnapshot("/absolute/some/prefix", "/absolute/some/prefix".length() + 1)
        then:
        !snapshot.present
        0 * _
    }
}
