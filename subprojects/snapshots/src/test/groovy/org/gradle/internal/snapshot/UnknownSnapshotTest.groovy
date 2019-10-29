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

import org.spockframework.mock.EmptyOrDummyResponse
import org.spockframework.mock.IDefaultResponse
import org.spockframework.mock.IMockInvocation
import spock.lang.Specification

class UnknownSnapshotTest extends Specification {

    def "returns empty when queried at root"() {
        def node = new UnknownSnapshot("some/prefix", createChildren("myFile.txt"))

        when:
        def snapshot = node.getSnapshot("/absolute/some/prefix", "/absolute/some/prefix".length() + 1)
        then:
        !snapshot.present
        0 * _
    }

    def "queries child when queried for path in child"() {
        given:
        def children = createChildren(childNames)
        def first = children.get(0)
        def node = new UnknownSnapshot("some/prefix", children)
        def relativePath = "${first.pathToParent}/someString"
        def result = Mock(MetadataSnapshot)

        when:
        def snapshot = node.getSnapshot(relativePath, 0)
        then:
        snapshot.get() == result
        _ * first.getSnapshot(relativePath, first.pathToParent.length() + 1) >> Optional.of(result)

        where:
        childNames << [["first"], ["first", "second"], ["first", "second", "third"]]
    }

    def "finds no snapshot when no child has a similar prefix"() {
        given:
        def children = createChildren(childNames)
        def first = children.get(0)
        def node = new UnknownSnapshot("some/prefix", children)
        def relativePath = "${first.pathToParent}1/someString"

        when:
        def snapshot = node.getSnapshot(relativePath, 0)
        then:
        !snapshot.present
        0 * _.getSnapshot(_)

        where:
        childNames << [["first"], ["first", "second"], ["first", "second", "third"]]
    }

    def "invalidating unknown child does nothing"() {
        given:
        def children = createChildren(childNames)
        def node = new UnknownSnapshot("some/prefix", children)
        def relativePath = "first/outside"

        when:
        def result = node.invalidate(relativePath, 0)
        then:
        0 * _.invalidate(_)
        result.get() == node

        where:
        childNames << [["first/within"], ["first/within", "second"], ["first/within", "second", "third"]]
    }

    def "invalidating only child returns empty"() {
        given:
        def children = createChildren("first")
        def node = new UnknownSnapshot("some/prefix", children)
        def childToInvalidate = children.get(0)
        def relativePath = childToInvalidate.pathToParent

        when:
        def result = node.invalidate(relativePath, 0)
        then:
        0 * _.invalidate(_)
        !result.present
    }

    def "invalidating known child removes it"() {
        given:
        def children = createChildren(childNames)
        def node = new UnknownSnapshot("some/prefix", children)
        def childToInvalidate = children.get(0)
        def relativePath = childToInvalidate.pathToParent
        def snapshot = Mock(MetadataSnapshot)
        def remainingChildren = children.findAll { it != childToInvalidate }

        when:
        def result = node.invalidate(relativePath, 0).get()
        remainingChildren.each {
            assert result.getSnapshot(it.pathToParent, 0).get() == snapshot
        }
        then:
        0 * _.invalidate(_)
        interaction {
            remainingChildren.each {
                _ * it.getSnapshot(it.pathToParent, it.pathToParent.length() + 1) >> Optional.of(snapshot)
            }
        }

        when:
        def removedSnapshot = result.getSnapshot(childToInvalidate.pathToParent, 0)
        then:
        0 * _.getSnapshot(_)
        !removedSnapshot.present

        where:
        childNames << [["first", "fourth"], ["first", "second"], ["first", "second", "third"], ["first", "second", "third", "fourth"]]
    }

    def "invalidating location within child works"() {
        given:
        def children = createChildren(childNames)
        def node = new UnknownSnapshot("some/prefix", children)
        def childWithChildToInvalidate = children.get(0)
        def invalidatedChild = Mock(FileSystemNode, defaultResponse: new RespondWithPrefix(childWithChildToInvalidate.pathToParent))

        when:
        def result = node.invalidate("${childWithChildToInvalidate.pathToParent}/deeper", 0).get()

        then:
        1 * childWithChildToInvalidate.invalidate("${childWithChildToInvalidate.pathToParent}/deeper", childWithChildToInvalidate.pathToParent.length() + 1) >> Optional.of(invalidatedChild)
        0 * _.invalidate(_)
        !result.getSnapshot(invalidatedChild.prefix, 0).present


        where:
        childNames << [["first/more"], ["first/some", "second/other"], ["first/even/deeper", "second", "third/whatever"], ["first/more/stuff", "second", "third", "fourth"]]
    }

    private List<FileSystemNode> createChildren(String... prefixes) {
        createChildren(prefixes as List)
    }

    private List<FileSystemNode> createChildren(Iterable<String> prefixes) {
        prefixes.sort()
        List<FileSystemNode> result = []
        prefixes.each {
            result.add(Mock(FileSystemNode, defaultResponse: new RespondWithPrefix(it)))
        }
        return result
    }

    private static class RespondWithPrefix implements IDefaultResponse {
        private final String prefix

        RespondWithPrefix(String prefix) {
            this.prefix = prefix
        }

        @Override
        Object respond(IMockInvocation invocation) {
            if (invocation.getMethod().name == "getPrefix") {
                return prefix
            }
            return EmptyOrDummyResponse.INSTANCE.respond(invocation)
        }
    }
}
