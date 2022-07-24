/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.util.PatternFilterable
import spock.lang.Specification

import java.util.function.Consumer

class AbstractFileTreeTest extends Specification {
    def isEmptyWhenVisitsNoFiles() {
        def tree = new TestFileTree([])

        expect:
        tree.empty
    }

    def isNotEmptyWhenVisitsFirstFile() {
        FileVisitDetails file = Mock()
        def tree = new TestFileTree([file])

        when:
        def empty = tree.empty

        then:
        !empty
        1 * file.stopVisiting()
    }

    def canFilterTreeUsingClosure() {
        FileVisitDetails file1 = Mock()
        FileVisitDetails file2 = Mock()
        def tree = new TestFileTree([file1, file2])

        when:
        def filtered = tree.matching { include '*.txt' }

        then:
        filtered instanceof FilteredTestFileTree
        filtered.patterns.includes == ['*.txt'] as Set
    }

    def canFilterTreeUsingAction() {
        FileVisitDetails file1 = Mock()
        FileVisitDetails file2 = Mock()
        def tree = new TestFileTree([file1, file2])

        when:
        def filtered = tree.matching(new Action<PatternFilterable>() {
            @Override
            void execute(PatternFilterable patternFilterable) {
                patternFilterable.include '*.txt'
            }
        })

        then:
        filtered instanceof FilteredTestFileTree
        filtered.patterns.includes == ['*.txt'] as Set
    }

    def "can add file trees together"() {
        File file1 = new File("f1")
        File file2 = new File("f2")
        FileVisitDetails fileVisitDetails1 = fileVisitDetails(file1)
        FileVisitDetails fileVisitDetails2 = fileVisitDetails(file2)
        def tree1 = new TestFileTree([fileVisitDetails1])
        def tree2 = new TestFileTree([fileVisitDetails2])

        when:
        FileTree sum = tree1.plus(tree2)

        then:
        sum.files.sort() == [file1, file2]
    }

    def "can add file trees together using + operator"() {
        File file1 = new File("f1")
        File file2 = new File("f2")
        FileVisitDetails fileVisitDetails1 = fileVisitDetails(file1)
        FileVisitDetails fileVisitDetails2 = fileVisitDetails(file2)
        def tree1 = new TestFileTree([fileVisitDetails1])
        def tree2 = new TestFileTree([fileVisitDetails2])

        when:
        FileTree sum = tree1 + tree2

        then:
        sum.files.sort() == [file1, file2]
    }

    FileVisitDetails fileVisitDetails(File file) {
        return Stub(FileVisitDetails) {
            getFile() >> { file }
        }
    }

    static class TestFileTree extends AbstractFileTree {
        List contents
        TaskDependency builtBy

        def TestFileTree(List files, TaskDependency dependencies = null) {
            this.contents = files
            this.builtBy = dependencies
        }

        String getDisplayName() {
            throw new UnsupportedOperationException();
        }

        @Override
        void visitDependencies(TaskDependencyResolveContext context) {
            context.add(builtBy)
        }

        @Override
        FileTreeInternal matching(PatternFilterable patterns) {
            return new FilteredTestFileTree(patterns: patterns)
        }

        @Override
        void visitContentsAsFileTrees(Consumer<FileTreeInternal> visitor) {
            visitor.accept(this)
        }

        FileTree visit(FileVisitor visitor) {
            contents.each { FileVisitDetails details ->
                visitor.visitFile(details)
            }
            this
        }
    }

    static class FilteredTestFileTree extends TestFileTree {
        PatternFilterable patterns

        FilteredTestFileTree() {
            super([])
        }
    }
}
