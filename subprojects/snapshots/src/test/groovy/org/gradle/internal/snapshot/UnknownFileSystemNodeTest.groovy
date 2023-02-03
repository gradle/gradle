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


import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

class UnknownFileSystemNodeTest extends AbstractIncompleteFileSystemNodeTest<UnknownFileSystemNode> {

    @Override
    protected UnknownFileSystemNode createInitialRootNode(ChildMap<FileSystemNode> children) {
        return new UnknownFileSystemNode(children)
    }

    @Override
    protected boolean isSameNodeType(FileSystemNode node) {
        return node instanceof UnknownFileSystemNode
    }

    @Override
    boolean isAllowEmptyChildren() {
        return false
    }

    def "invalidate #vfsSpec.searchedPath removes child #vfsSpec.selectedChildPath (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.invalidate(searchedPath, CASE_SENSITIVE, diffListener).get()
        then:
        resultRoot.children == childrenWithSelectedChildRemoved()
        isSameNodeType(resultRoot)
        removedNodes == [selectedChild]
        addedNodes.empty
        interaction { noMoreInteractions() }

        where:
        vfsSpec << (IS_PREFIX_OF_CHILD + SAME_PATH).findAll { it.childPaths.size() > 1 }
    }

    def "invalidating the only child by #vfsSpec.searchedPath removes the node (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.invalidate(searchedPath, CASE_SENSITIVE, diffListener)
        then:
        !resultRoot.present
        removedNodes == [selectedChild]
        addedNodes.empty
        interaction { noMoreInteractions() }

        where:
        vfsSpec << (IS_PREFIX_OF_CHILD + SAME_PATH).findAll { it.childPaths.size() == 1 }
    }

    def "invalidate #vfsSpec.searchedPath invalidates children of #vfsSpec.selectedChildPath (#vfsSpec)"() {
        setupTest(vfsSpec)
        def invalidatedChild = mockChild()

        when:
        def resultRoot = initialRoot.invalidate(searchedPath, CASE_SENSITIVE, diffListener).get()
        then:
        resultRoot.children == childrenWithSelectedChildReplacedBy(invalidatedChild)
        isSameNodeType(resultRoot)
        removedNodes.empty
        addedNodes.empty
        interaction {
            invalidateDescendantOfSelectedChild(invalidatedChild)
            noMoreInteractions()
        }

        where:
        vfsSpec << CHILD_IS_PREFIX
    }

    def "invalidate #vfsSpec.searchedPath removes empty invalidated child #vfsSpec.selectedChildPath (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.invalidate(searchedPath, CASE_SENSITIVE, diffListener).get()
        then:
        resultRoot.children == childrenWithSelectedChildRemoved()
        isSameNodeType(resultRoot)
        removedNodes.empty
        addedNodes.empty
        interaction {
            invalidateDescendantOfSelectedChild(null)
            noMoreInteractions()
        }

        where:
        vfsSpec << CHILD_IS_PREFIX.findAll { it.childPaths.size() > 1 }
    }

    def "invalidate #vfsSpec.searchedPath removes the child #vfsSpec.selectedChildPath and the node with it (#vfsSpec)"() {
        setupTest(vfsSpec)

        when:
        def resultRoot = initialRoot.invalidate(searchedPath, CASE_SENSITIVE, diffListener)
        then:
        !resultRoot.present
        removedNodes.empty
        addedNodes.empty
        interaction {
            invalidateDescendantOfSelectedChild(null)
            noMoreInteractions()
        }

        where:
        vfsSpec << CHILD_IS_PREFIX.findAll { it.childPaths.size() == 1 }
    }

    def "returns empty for snapshot"() {
        def node = new UnknownFileSystemNode(createChildren(["myFile.txt"]))

        expect:
        !node.getSnapshot().present
    }
}
