/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.util.PatternSet
import spock.lang.Specification
import spock.lang.Subject


class VisitedTreesPreCheckHasherTest extends Specification {
    @Subject
    VisitedTreesPreCheckHasher visitedTreesPreCheckHasher = new VisitedTreesPreCheckHasher()

    def "hash is calculated for single tree"() {
        given:
        def visitedTree = Mock(VisitedTree)
        def patternSet = new PatternSet()
        def fileTreeElement = Mock(FileTreeElement)

        when:
        def hash = visitedTreesPreCheckHasher.calculatePreCheckHash([visitedTree])

        then:
        _ * visitedTree.getAbsolutePath() >> '/a/b/c'
        _ * visitedTree.getPatternSet() >> patternSet
        _ * visitedTree.getEntries() >> [fileTreeElement]
        1 * visitedTree.calculatePreCheckHash() >> 123
        hash == -483058259
        0 * _._
    }

    def "hash is calculated for multiple trees in stable order"() {
        given:
        def visitedTree1 = createVisitedTreeStub('/a/b/c1', 123)
        def visitedTree2 = createVisitedTreeStub('/a/b/c2', 234)
        def visitedTree3 = createVisitedTreeStub('/a/b/c3', 345)

        when:
        def hashes = [visitedTree1, visitedTree2, visitedTree3].permutations().collect { visitedTreesPreCheckHasher.calculatePreCheckHash(it) }

        then:
        hashes.every {
            it == 436976747
        }
    }

    VisitedTree createVisitedTreeStub(String path, int hash) {
        def visitedTree = Stub(VisitedTree)
        def patternSet = new PatternSet()
        def fileTreeElement = Stub(FileTreeElement)
        visitedTree.getAbsolutePath() >> path
        visitedTree.getPatternSet() >> patternSet
        visitedTree.getEntries() >> [fileTreeElement]
        visitedTree.calculatePreCheckHash() >> hash
        visitedTree
    }


}
