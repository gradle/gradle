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

class AbstractIncompleteSnapshotWithChildrenTest extends Specification {

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
