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

package org.gradle.api.internal.file.collections

import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class FilteredMinimalFileTreeTest extends Specification {
    def source = Mock(FilteredMinimalFileTree)
    def patterns = Stub(PatternSet)
    def tree = new FilteredMinimalFileTree(patterns, source)

    def "ignores directory that is not included"() {
        def spec = Mock(Spec)
        def included = Stub(FileVisitDetails)
        def excluded = Stub(FileVisitDetails)
        def visitor = Mock(FileVisitor)

        when:
        tree.visit(visitor)

        then:
        _ * patterns.asSpec >> spec
        1 * spec.isSatisfiedBy(included) >> true
        1 * spec.isSatisfiedBy(excluded) >> false
        1 * source.visit(_) >> { FileVisitor nestedVisitor ->
            nestedVisitor.visitDir(included)
            nestedVisitor.visitDir(excluded)
        }
        1 * visitor.visitDir(included)
        0 * _
    }

    def "ignores file that is not included"() {
        def spec = Mock(Spec)
        def included = Stub(FileVisitDetails)
        def excluded = Stub(FileVisitDetails)
        def visitor = Mock(FileVisitor)

        when:
        tree.visit(visitor)

        then:
        _ * patterns.asSpec >> spec
        1 * spec.isSatisfiedBy(included) >> true
        1 * spec.isSatisfiedBy(excluded) >> false
        1 * source.visit(_) >> { FileVisitor nestedVisitor ->
            nestedVisitor.visitFile(included)
            nestedVisitor.visitFile(excluded)
        }
        1 * visitor.visitFile(included)
        0 * _
    }

    def "visits structure when backed by a directory tree"() {
        def dir = new File("dir")
        def owner = Stub(FileTreeInternal)
        def sourcePatterns = Mock(PatternSet)
        def intersectPatterns = Mock(PatternSet)
        def visitor = Mock(MinimalFileTree.MinimalFileTreeStructureVisitor)

        when:
        tree.visitStructure(visitor, owner)

        then:
        1 * source.visitStructure(_, _) >> { MinimalFileTree.MinimalFileTreeStructureVisitor nestedVisitor, FileTreeInternal o ->
            nestedVisitor.visitFileTree(dir, sourcePatterns, o)
        }
        1 * sourcePatterns.intersect() >> intersectPatterns
        1 * intersectPatterns.copyFrom(patterns)
        1 * visitor.visitFileTree(dir, intersectPatterns, owner)
        0 * _
    }

    def "applies filters to mirror"() {
        def sourceMirror = Mock(DirectoryFileTree)
        def filteredMirror = Mock(DirectoryFileTree)

        when:
        def mirror = tree.mirror

        then:
        mirror == filteredMirror

        1 * source.mirror >> sourceMirror
        1 * sourceMirror.filter(patterns) >> filteredMirror
        0 * _
    }
}
