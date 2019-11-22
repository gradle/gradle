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

import spock.lang.Specification

import javax.annotation.Nullable
import java.util.stream.Collectors

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

abstract class AbstractSnapshotWithChildrenTest<NODE extends FileSystemNode, CHILD extends FileSystemNode> extends Specification {
    NODE initialRoot
    List<CHILD> children
    PathSuffix relativePath

    /**
     * The child, if any, which has a common prefix with the selected path, i.e. (absolutePath/offset).
     */
    FileSystemNode selectedChild

    abstract protected NODE createInitialRootNode(String pathToParent, List<CHILD> children);

    abstract protected CHILD mockChild(String pathToParent)

    void setupTest(VirtualFileSystemTestSpec spec) {
        this.children = createChildren(spec.childPaths)
        this.initialRoot = createInitialRootNode("path/to/parent", children)
        this.relativePath = PathSuffix.of(spec.absolutePath, spec.offset)
        this.selectedChild = children.find { it.pathToParent == spec.selectedChildPath }
    }

    List<CHILD> createChildren(List<String> pathsToParent) {
        return pathsToParent.stream()
            .sorted(PathUtil.getPathComparator(CASE_SENSITIVE))
            .map { childPath -> mockChild(childPath) }
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

    List<CHILD> childrenWithSelectedChildRemoved() {
        children.findAll { it != selectedChild }
    }

    CHILD getNodeWithIndexOfSelectedChild(List<CHILD> newChildren) {
        int index = children.indexOf(selectedChild)
        return newChildren[index]
    }

    private int getInsertionPoint() {
        int childIndex = SearchUtil.binarySearch(children) {
            candidate -> relativePath.compareToFirstSegment(candidate.pathToParent, CASE_SENSITIVE)
        }
        assert childIndex < 0
        return -childIndex - 1
    }

    String getCommonPrefix() {
        return selectedChild.pathToParent.substring(0, relativePath.lengthOfCommonPrefix(selectedChild.pathToParent, CASE_SENSITIVE))
    }

    String getPathFromCommonPrefix() {
        return relativePath.suffixStartingFrom(commonPrefix.length() + 1).asString
    }

    String getSelectedChildPathFromCommonPrefix() {
        return selectedChild.pathToParent.substring(commonPrefix.length() + 1)
    }

    def getDescendantSnapshotOfSelectedChild(@Nullable MetadataSnapshot foundSnapshot) {
        def descendantOffset = selectedChild.pathToParent.length() + 1
        1 * selectedChild.getSnapshot(relativePath.suffixStartingFrom(descendantOffset), CASE_SENSITIVE) >> Optional.ofNullable(foundSnapshot)
    }

    def invalidateDescendantOfSelectedChild(@Nullable FileSystemNode invalidatedChild) {
        def descendantOffset = selectedChild.pathToParent.length() + 1
        1 * selectedChild.invalidate(relativePath.suffixStartingFrom(descendantOffset), CASE_SENSITIVE) >> Optional.ofNullable(invalidatedChild)
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    void noMoreInteractions() {
        _ * _.pathToParent
        0 * _
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
        ['aa/b1', 'ab/a1', 'name', 'name1/some', 'name2/other/third'],
        ("a".."z").toList(),
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
    } + new VirtualFileSystemTestSpec([], 'different', 0, null)

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

    private static String findPathWithParent(List<String> childPaths, String parentPath) {
        childPaths.find { PathSuffix.of(it, 0).hasPrefix(parentPath, CASE_SENSITIVE) }
    }

    private static List<String> parentPaths(String childPath) {
        (childPath.split('/') as List).inits().tail().init().collect { it.join('/') }
    }
}
