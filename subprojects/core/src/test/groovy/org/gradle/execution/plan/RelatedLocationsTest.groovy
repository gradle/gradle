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

class RelatedLocationsTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def locations = new RelatedLocations(CaseSensitivity.CASE_SENSITIVE, Stub(Stat))

    def "can record related nodes"() {
        def node1 = Mock(Node)
        def node2 = Mock(Node)
        def node3 = Mock(Node)
        locations.recordRelatedToNode(["/some/location"], node1)
        locations.recordRelatedToNode(["/some/other/location"], node2)
        locations.recordRelatedToNode(["/some/other/third"], node3)

        expect:
        assertNodesRelated("/some", node1, node2, node3)
        assertNodesRelated("/some/other", node2, node3)
        assertNodesRelated("/some/other/third", node3)
        assertNodesRelated("/some/other/third/sub/dir", node3)
        assertNodesRelated("/some/different")
        assertNodesRelated("/", node1, node2, node3)
    }

    def "can record multiple nodes at the same location"() {
        def node1 = Mock(Node)
        def node2 = Mock(Node)
        def node3 = Mock(Node)
        def child = Mock(Node)
        def other = Mock(Node)
        def location = "/some/location"
        locations.recordRelatedToNode([location], node1)
        locations.recordRelatedToNode(["/some/location/second"], child)
        locations.recordRelatedToNode([location], node2)
        locations.recordRelatedToNode(["/some/other"], other)
        locations.recordRelatedToNode([location], node3)

        expect:
        assertNodesRelated(location, node1, node2, node3, child)
    }

    def "can record ancestors of related nodes"() {
        def child = Mock(Node)
        def grandChild = Mock(Node)
        def ancestor = Mock(Node)
        locations.recordRelatedToNode(["/some/location/child/within"], child)
        locations.recordRelatedToNode(["/some/location"], ancestor)
        locations.recordRelatedToNode(["/some/location/child/within/some/grandchild"], grandChild)

        expect:
        assertNodesRelated("/some", ancestor, child, grandChild)
        assertNodesRelated("/some/location", ancestor, child, grandChild)
        assertNodesRelated("/some/location/child/within", ancestor, child, grandChild)
        assertNodesRelated("/some/location/child/other", ancestor)
    }

    def "ancestor is related to location"() {
        def node1 = Mock(Node)
        def node2 = Mock(Node)
        locations.recordRelatedToNode(["/some/location"], node1)
        locations.recordRelatedToNode(["/some/location/within/deep"], node2)

        expect:
        assertNodesRelated("/some/location/within", node1, node2)
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

        locations.recordRelatedToNode([root.absolutePath], rootNode)
        locations.recordRelatedToNode([root.file("location").absolutePath], node1)
        locations.recordRelatedToNode([root.file("sub/location").absolutePath], node2)
        locations.recordRelatedToNode([root.file("third/within").absolutePath], node3)
        locations.recordRelatedToNode([root.file("included/within").absolutePath], node4)
        locations.recordRelatedToNode([root.file("excluded/within").absolutePath], node5)
        locations.recordRelatedToNode([temporaryFolder.createDir("other").file("third").absolutePath], node6)

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

        locations.recordRelatedToNode([root.absolutePath], rootNode)
        locations.recordRelatedToNode([root.file("missing").absolutePath], missing)
        locations.recordRelatedToNode([root.createDir("directory").absolutePath], directory)
        locations.recordRelatedToNode([root.createFile("file").absolutePath], file)

        expect:
        nodesRelatedTo(root, "**/within") == ([rootNode, missing, directory] as Set)
        nodesRelatedTo(root, "**/file") == ([rootNode, missing, directory, file] as Set)
        nodesRelatedTo(root, "directory/**") == ([rootNode, directory] as Set)
        nodesRelatedTo(root, "missing/**") == ([rootNode, missing] as Set)
        nodesRelatedTo(root, "file/**") == ([rootNode, file] as Set)
    }

    def "filters children for a true ancestor of related nodes"() {
        def root = temporaryFolder.file("root")

        def childNode = Mock(Node)
        locations.recordRelatedToNode([root.file("some/sub/dir").absolutePath], childNode)
        expect:
        nodesRelatedTo(root, "some/sub/dir") == ([childNode] as Set)
        nodesRelatedTo(root, "some/sub/other") == ([] as Set)
    }

    def "can record filtered roots"() {
        def root = temporaryFolder.file("root")
        def node1 = Mock(Node)

        locations.recordFileTreeRelatedToNode(root.absolutePath, node1, includes("sub/included/*"))

        expect:
        nodesRelatedTo(root.file("sub/included")) == ([node1] as Set)
    }

    def "can visit root path"() {
        def root = Mock(Node)
        def childNode = Mock(Node)

        locations.recordRelatedToNode(["/"], root)
        locations.recordRelatedToNode(["/some/location"], childNode)

        expect:
        assertNodesRelated("/", root, childNode)
        assertNodesRelated("/some/location", root, childNode)
        assertNodesRelated("/other", root)
    }

    def nodesRelatedTo(TestFile location, String includes) {
        return locations.getNodesRelatedTo(location.absolutePath, new PatternSet().include(includes).asSpec)
    }

    def nodesRelatedTo(TestFile location) {
        return locations.getNodesRelatedTo(location.absolutePath)
    }

    Spec<FileTreeElement> includes(String include) {
        return new PatternSet().include(include).asSpec
    }

    void assertNodesRelated(String location, Node... expectedNodeList) {
        def expectedNodes = expectedNodeList as Set
        assert locations.getNodesRelatedTo(location) == expectedNodes
        assert locations.getNodesRelatedTo(location) { element -> true } == expectedNodes
    }

}
