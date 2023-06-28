/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.execution.plan

import com.google.common.collect.ImmutableSet
import org.gradle.api.file.ReadOnlyFileTreeElement
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.file.Stat
import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class ExecutionNodeAccessHierarchyTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def hierarchy = new ExecutionNodeAccessHierarchy(CaseSensitivity.CASE_SENSITIVE, Stub(Stat))

    def "can record nodes"() {
        def node1 = Mock(Node)
        def node2 = Mock(Node)
        def node3 = Mock(Node)
        hierarchy.recordNodeAccessingLocations(node1, ["/some/location"])
        hierarchy.recordNodeAccessingLocations(node2, ["/some/other/location"])
        hierarchy.recordNodeAccessingLocations(node3, ["/some/other/third"])

        expect:
        assertNodesAccessing("/some", node1, node2, node3)
        assertNodesAccessing("/some/other", node2, node3)
        assertNodesAccessing("/some/other/third", node3)
        assertNodesAccessing("/some/other/third/sub/dir", node3)
        assertNodesAccessing("/some/different")
        assertNodesAccessing("/", node1, node2, node3)
    }

    def "can record multiple nodes accessing the same location"() {
        def node1 = Mock(Node)
        def node2 = Mock(Node)
        def node3 = Mock(Node)
        def child = Mock(Node)
        def other = Mock(Node)
        def location = "/some/location"
        hierarchy.recordNodeAccessingLocations(node1, [location])
        hierarchy.recordNodeAccessingLocations(child, ["/some/location/second"])
        hierarchy.recordNodeAccessingLocations(node2, [location])
        hierarchy.recordNodeAccessingLocations(other, ["/some/other"])
        hierarchy.recordNodeAccessingLocations(node3, [location])

        expect:
        assertNodesAccessing(location, node1, node2, node3, child)
    }

    def "can record nodes accessing ancestors"() {
        def child = Mock(Node)
        def grandChild = Mock(Node)
        def ancestor = Mock(Node)
        hierarchy.recordNodeAccessingLocations(child, ["/some/location/child/within"])
        hierarchy.recordNodeAccessingLocations(ancestor, ["/some/location"])
        hierarchy.recordNodeAccessingLocations(grandChild, ["/some/location/child/within/some/grandchild"])

        expect:
        assertNodesAccessing("/some", ancestor, child, grandChild)
        assertNodesAccessing("/some/location", ancestor, child, grandChild)
        assertNodesAccessing("/some/location/child/within", ancestor, child, grandChild)
        assertNodesAccessing("/some/location/child/other", ancestor)
    }

    def "ancestor accesses location"() {
        def node1 = Mock(Node)
        def node2 = Mock(Node)
        hierarchy.recordNodeAccessingLocations(node1, ["/some/location"])
        hierarchy.recordNodeAccessingLocations(node2, ["/some/location/within/deep"])

        expect:
        assertNodesAccessing("/some/location/within", node1, node2)
    }

    def "filters locations when searching for matching ones"() {
        def root = temporaryFolder.file("root")

        def rootNode = Mock(Node)
        def node1 = Mock(Node)
        def node2 = Mock(Node)
        def node3 = Mock(Node)
        def node4 = Mock(Node)
        def node5 = Mock(Node)
        def node6 = Mock(Node)
        def node7 = Mock(Node)

        hierarchy.recordNodeAccessingLocations(rootNode, [root.absolutePath])
        hierarchy.recordNodeAccessingLocations(node1, [root.file("location").absolutePath])
        hierarchy.recordNodeAccessingLocations(node2, [root.file("sub/location").absolutePath])
        hierarchy.recordNodeAccessingLocations(node3, [root.file("third/within").absolutePath])
        hierarchy.recordNodeAccessingLocations(node4, [root.file("included/without").absolutePath])
        hierarchy.recordNodeAccessingLocations(node7, [root.file("included/within").absolutePath])
        hierarchy.recordNodeAccessingLocations(node5, [root.file("excluded/within").absolutePath])
        hierarchy.recordNodeAccessingLocations(node6, [temporaryFolder.createDir("other").file("third").absolutePath])

        expect:
        nodesRelatedTo(root, "*/within") == ([rootNode, node3, node5, node7] as Set)
        nodesRelatedTo(root, "included/*") == ([rootNode, node4, node7] as Set)
        nodesRelatedTo(root, "included/within") == ([rootNode, node7] as Set)
    }

    def "uses the correct file type for filtering"() {
        def root = temporaryFolder.file("root")

        def rootNode = Mock(Node)
        def missing = Mock(Node)
        def directory = Mock(Node)
        def file = Mock(Node)

        hierarchy.recordNodeAccessingLocations(rootNode, [root.absolutePath])
        hierarchy.recordNodeAccessingLocations(missing, [root.file("missing").absolutePath])
        hierarchy.recordNodeAccessingLocations(directory, [root.createDir("directory").absolutePath])
        hierarchy.recordNodeAccessingLocations(file, [root.createFile("file").absolutePath])

        expect:
        nodesRelatedTo(root, "**/within") == ([rootNode, directory] as Set)
        nodesRelatedTo(root, "**/file") == ([rootNode, directory, file] as Set)
        nodesRelatedTo(root, "directory/**") == ([rootNode, directory] as Set)
        nodesRelatedTo(root, "missing/**") == ([rootNode, missing] as Set)
        nodesRelatedTo(root, "file/**") == ([rootNode, file] as Set)
    }

    def "filters children for a true ancestor of an accessed location"() {
        def root = temporaryFolder.file("root")

        def childNode = Mock(Node)
        hierarchy.recordNodeAccessingLocations(childNode, [root.file("some/sub/dir").absolutePath])
        expect:
        nodesRelatedTo(root, "some/sub/dir") == ([childNode] as Set)
        nodesRelatedTo(root, "some/sub/other") == ([] as Set)
    }

    def "can record filtered roots"() {
        def root = temporaryFolder.file("root")
        def node1 = Mock(Node)
        root.file("sub/included").createDir()

        hierarchy.recordNodeAccessingFileTree(node1, root.absolutePath, includes("sub/included/*"))

        expect:
        nodesRelatedTo(root.file("sub/included")) == ([node1] as Set)
        nodesRelatedTo(root.file("sub/included/within")) == ([node1] as Set)
        nodesRelatedTo(root.file("sub/included/within/notMatched")) == Collections.emptySet()
    }

    def "can visit root path"() {
        def root = Mock(Node)
        def childNode = Mock(Node)

        hierarchy.recordNodeAccessingLocations(root, ["/"])
        hierarchy.recordNodeAccessingLocations(childNode, ["/some/location"])

        expect:
        assertNodesAccessing("/", root, childNode)
        assertNodesAccessing("/some/location", root, childNode)
        assertNodesAccessing("/other", root)
    }

    def "can return nodes accessing some path taking includes and excludes into consideration"() {
        def childNode = Mock(Node)

        hierarchy.recordNodeAccessingLocations(childNode, ["/some/root/child/relative/path"])
        expect:
        hierarchy.getNodesAccessing("/some/root", excludes("child")).empty
        hierarchy.getNodesAccessing("/some/root", includes("child")).empty
        hierarchy.getNodesAccessing("/some/root", includes("child/**")) == ImmutableSet.of(childNode)
    }

    def "can record file trees with excludes for parts of paths"() {
        def excluding = Mock(Node)
        def including = Mock(Node)
        def includingWithChildren = Mock(Node)
        def root = "/some/root/"

        hierarchy.recordNodeAccessingFileTree(excluding, root, excludes("child"))
        hierarchy.recordNodeAccessingFileTree(including, root, includes("child"))
        hierarchy.recordNodeAccessingFileTree(includingWithChildren, root, includes("child/**"))
        expect:
        assertNodesAccessing(root, excluding, including, includingWithChildren)
        assertNodesAccessing("${root}/child", including, includingWithChildren)
        assertNodesAccessing("${root}/child/some/subdir", includingWithChildren)
    }

    def nodesRelatedTo(TestFile location, String includePattern) {
        return hierarchy.getNodesAccessing(location.absolutePath, includes(includePattern))
    }

    def nodesRelatedTo(TestFile location) {
        return hierarchy.getNodesAccessing(location.absolutePath)
    }

    static Spec<ReadOnlyFileTreeElement> includes(String include) {
        return new PatternSet().include(include).asSpec
    }

    static Spec<ReadOnlyFileTreeElement> excludes(String exclude) {
        return new PatternSet().exclude(exclude).asSpec
    }

    void assertNodesAccessing(String location, Node... expectedNodeList) {
        def expectedNodes = expectedNodeList as Set
        assert hierarchy.getNodesAccessing(location) == expectedNodes
        assert hierarchy.getNodesAccessing(location) { element -> true } == expectedNodes
    }

}
