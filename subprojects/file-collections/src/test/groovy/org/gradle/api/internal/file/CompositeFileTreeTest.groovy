/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file

import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.gradle.api.Action
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.Actions
import org.gradle.internal.Factory
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

import java.util.function.Consumer

@UsesNativeServices
class CompositeFileTreeTest extends Specification {
    private final FileTreeInternal source1 = Mock()
    private final FileTreeInternal source2 = Mock()
    private final Factory<PatternSet> patternSetFactory = Mock()
    private final CompositeFileTree tree = new CompositeFileTree(TestFiles.taskDependencyFactory(), patternSetFactory) {
        @Override
        String getDisplayName() {
            return "<display-name>"
        }

        @Override
        protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
            visitor.accept(source1)
            visitor.accept(source2)
        }
    }

    def matchingWithClosureReturnsUnionOfFilteredSets() {
        final Closure closure = {}
        final FileTreeInternal filtered1 = Mock()
        final FileTreeInternal filtered2 = Mock()
        final PatternSet patterns = Mock()

        when:
        FileTree filtered = tree.matching(closure)
        def sourceCollections = (filtered as CompositeFileTree).sourceCollections

        then:
        sourceCollections == [filtered1, filtered2]

        and:
        1 * patternSetFactory.create() >> patterns
        1 * source1.matching(patterns) >> filtered1
        1 * source2.matching(patterns) >> filtered2
    }

    def matchingWithActionReturnsUnionOfFilteredSets() {
        final Action<PatternFilterable> action = Mock()
        final FileTreeInternal filtered1 = Mock()
        final FileTreeInternal filtered2 = Mock()
        final PatternSet patterns = Mock()

        when:
        FileTree filtered = tree.matching(action)

        then: // action is applied each time the contents are queried
        0 * _

        when:
        def sourceCollections = (filtered as CompositeFileTree).sourceCollections

        then:
        sourceCollections == [filtered1, filtered2]

        and:
        1 * patternSetFactory.create() >> patterns
        1 * action.execute(patterns)
        1 * source1.matching(patterns) >> filtered1
        1 * source2.matching(patterns) >> filtered2
    }

    def matchingWithPatternSetReturnsUnionOfFilteredSets() {
        final PatternSet patternSet = new PatternSet()
        final FileTreeInternal filtered1 = Mock()
        final FileTreeInternal filtered2 = Mock()

        when:
        FileTree filtered = tree.matching(patternSet)
        def sourceCollections = (filtered as CompositeFileTree).sourceCollections

        then:
        sourceCollections == [filtered1, filtered2]

        and:
        1 * source1.matching(patternSet) >> filtered1
        1 * source2.matching(patternSet) >> filtered2
    }

    def plusReturnsUnionOfThisTreeAndSourceTree() {
        FileTreeInternal other = Mock()

        when:
        FileTree sum = tree.plus(other)

        then:
        sum.sourceCollections == [tree, other]
    }

    def visitsEachTreeWithVisitor() {
        final FileVisitor visitor = Mock()

        when:
        tree.visit(visitor)

        then:
        1 * source1.visit(visitor)
        1 * source2.visit(visitor)
    }

    def visitsEachTreeWithClosure() {
        final Closure visitor = TestUtil.TEST_CLOSURE
        final FileVisitor closureAsVisitor = DefaultGroovyMethods.asType(visitor, FileVisitor.class)

        when:
        tree.visit(visitor)

        then:
        1 * source1.visit(closureAsVisitor)
        1 * source2.visit(closureAsVisitor)
    }

    def visitsEachTreeWithAction() {
        final Action<FileVisitDetails> visitor = Actions.doNothing()

        when:
        tree.visit(visitor)

        then:
        1 * source1.visit(visitor)
        1 * source2.visit(visitor)
    }
}
