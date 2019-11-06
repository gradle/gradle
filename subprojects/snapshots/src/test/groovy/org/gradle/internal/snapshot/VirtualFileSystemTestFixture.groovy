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
    List<FileSystemNode> children
    String absolutePath
    int offset
    FileSystemNode selectedChild
    private final MockCreator mockCreator

    VirtualFileSystemTestFixture(List<FileSystemNode> children, String absolutePath, int offset, @Nullable FileSystemNode selectedChild, MockCreator mockCreator) {
        this.mockCreator = mockCreator
        this.children = children
        this.absolutePath = absolutePath
        this.offset = offset
        this.selectedChild = selectedChild
    }

    List<FileSystemNode> getWithSelectedChildRemoved() {
        children.findAll { it != selectedChild }
    }

    def <T extends FileSystemNode> VirtualFileSystemTestFixture withSelectedChildAs(Class<T> nodeType) {
        def metadataChild = mockCreator.create(nodeType, selectedChild.pathToParent)
        children[children.indexOf(selectedChild)] = metadataChild
        selectedChild = metadataChild
        return this
    }

    List<FileSystemNode> withSelectedChildReplacedBy(FileSystemNode replacement) {
        children.collect { it == selectedChild ? replacement : it }
    }

    List<FileSystemNode> withNewChild(FileSystemNode newChild) {
        List<FileSystemNode> newChildren = new ArrayList<>(children)
        newChildren.add(insertionPoint, newChild)
        return newChildren
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

    interface MockCreator {
        def <T extends FileSystemNode> T create(Class<T> type, String pathToParent)
    }
}
