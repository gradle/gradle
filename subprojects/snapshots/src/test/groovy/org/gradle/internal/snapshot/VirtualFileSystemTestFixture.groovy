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

import javax.annotation.Nullable

class VirtualFileSystemTestFixture {
    final FileSystemNode initialRoot
    final List<FileSystemNode> children
    final String absolutePath
    final int offset
    final FileSystemNode selectedChild

    VirtualFileSystemTestFixture(FileSystemNode initialRoot, List<FileSystemNode> children, String absolutePath, int offset, @Nullable FileSystemNode selectedChild) {
        this.initialRoot = initialRoot
        this.children = children
        this.absolutePath = absolutePath
        this.offset = offset
        this.selectedChild = selectedChild
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
            candidate -> PathUtil.compareWithCommonPrefix(candidate.pathToParent, absolutePath, offset)
        }
        assert childIndex < 0
        return -childIndex - 1
    }

    String getCommonPrefix() {
        return selectedChild.pathToParent.substring(0, PathUtil.sizeOfCommonPrefix(selectedChild.pathToParent, absolutePath, offset))
    }
}
