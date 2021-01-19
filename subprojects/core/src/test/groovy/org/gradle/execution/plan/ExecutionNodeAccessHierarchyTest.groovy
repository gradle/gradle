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

import org.gradle.api.file.FileTreeElement
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

    def locations = new ExecutionNodeAccessHierarchy(CaseSensitivity.CASE_SENSITIVE, Stub(Stat))

    def "can record nodes"() {
        def node1 = Mock(Node)
        def node2 = Mock(Node)
        def node3 = Mock(Node)
        locations.recordNodeAccessingLocations(node1, ["/some/location"])
        locations.recordNodeAccessingLocations(node2, ["/some/other/location"])
        locations.recordNodeAccessingLocations(node3, ["/some/other/third"])

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
        locations.recordNodeAccessingLocations(node1, [location])
        locations.recordNodeAccessingLocations(child, ["/some/location/second"])
        locations.recordNodeAccessingLocations(node2, [location])
        locations.recordNodeAccessingLocations(other, ["/some/other"])
        locations.recordNodeAccessingLocations(node3, [location])

        expect:
        assertNodesAccessing(location, node1, node2, node3, child)
    }

    def "can record nodes accessing ancestors"() {
        def child = Mock(Node)
        def grandChild = Mock(Node)
        def ancestor = Mock(Node)
        locations.recordNodeAccessingLocations(child, ["/some/location/child/within"])
        locations.recordNodeAccessingLocations(ancestor, ["/some/location"])
        locations.recordNodeAccessingLocations(grandChild, ["/some/location/child/within/some/grandchild"])

        expect:
        assertNodesAccessing("/some", ancestor, child, grandChild)
        assertNodesAccessing("/some/location", ancestor, child, grandChild)
        assertNodesAccessing("/some/location/child/within", ancestor, child, grandChild)
        assertNodesAccessing("/some/location/child/other", ancestor)
    }

    def "ancestor accesses location"() {
        def node1 = Mock(Node)
        def node2 = Mock(Node)
        locations.recordNodeAccessingLocations(node1, ["/some/location"])
        locations.recordNodeAccessingLocations(node2, ["/some/location/within/deep"])

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

        locations.recordNodeAccessingLocations(rootNode, [root.absolutePath])
        locations.recordNodeAccessingLocations(node1, [root.file("location").absolutePath])
        locations.recordNodeAccessingLocations(node2, [root.file("sub/location").absolutePath])
        locations.recordNodeAccessingLocations(node3, [root.file("third/within").absolutePath])
        locations.recordNodeAccessingLocations(node4, [root.file("included/within").absolutePath])
        locations.recordNodeAccessingLocations(node5, [root.file("excluded/within").absolutePath])
        locations.recordNodeAccessingLocations(node6, [temporaryFolder.createDir("other").file("third").absolutePath])

        expect:
        nodesRelatedTo(root, "*/within") == ([rootNode, node1, node3, node4, node5] as Set)
        nodesRelatedTo(root, "included/*") == ([rootNode, node4] as Set)
        nodesRelatedTo(root, "included/within") == ([rootNode, node4] as Set)
    }

    def "uses the correct file type for filtering"() {
        def root = temporaryFolder.file("root")

        def rootNode = Mock(Node)
        def missing = Mock(Node)
        def directory = Mock(Node)
        def file = Mock(Node)

        locations.recordNodeAccessingLocations(rootNode, [root.absolutePath])
        locations.recordNodeAccessingLocations(missing, [root.file("missing").absolutePath])
        locations.recordNodeAccessingLocations(directory, [root.createDir("directory").absolutePath])
        locations.recordNodeAccessingLocations(file, [root.createFile("file").absolutePath])

        expect:
        nodesRelatedTo(root, "**/within") == ([rootNode, missing, directory] as Set)
        nodesRelatedTo(root, "**/file") == ([rootNode, missing, directory, file] as Set)
        nodesRelatedTo(root, "directory/**") == ([rootNode, directory] as Set)
        nodesRelatedTo(root, "missing/**") == ([rootNode, missing] as Set)
        nodesRelatedTo(root, "file/**") == ([rootNode, file] as Set)
    }

    def "filters children for a true ancestor of an accessed location"() {
        def root = temporaryFolder.file("root")

        def childNode = Mock(Node)
        locations.recordNodeAccessingLocations(childNode, [root.file("some/sub/dir").absolutePath])
        expect:
        nodesRelatedTo(root, "some/sub/dir") == ([childNode] as Set)
        nodesRelatedTo(root, "some/sub/other") == ([] as Set)
    }

    def "can record filtered roots"() {
        def root = temporaryFolder.file("root")
        def node1 = Mock(Node)

        locations.recordNodeAccessingFileTree(node1, root.absolutePath, includes("sub/included/*"))

        expect:
        nodesRelatedTo(root.file("sub/included")) == ([node1] as Set)
    }

    def "can visit root path"() {
        def root = Mock(Node)
        def childNode = Mock(Node)

        locations.recordNodeAccessingLocations(root, ["/"])
        locations.recordNodeAccessingLocations(childNode, ["/some/location"])

        expect:
        assertNodesAccessing("/", root, childNode)
        assertNodesAccessing("/some/location", root, childNode)
        assertNodesAccessing("/other", root)
    }

    def nodesRelatedTo(TestFile location, String includes) {
        return locations.getNodesAccessing(location.absolutePath, new PatternSet().include(includes).asSpec)
    }

    def nodesRelatedTo(TestFile location) {
        return locations.getNodesAccessing(location.absolutePath)
    }

    Spec<FileTreeElement> includes(String include) {
        return new PatternSet().include(include).asSpec
    }

    void assertNodesAccessing(String location, Node... expectedNodeList) {
        def expectedNodes = expectedNodeList as Set
        assert locations.getNodesAccessing(location) == expectedNodes
        assert locations.getNodesAccessing(location) { element -> true } == expectedNodes
    }

}
