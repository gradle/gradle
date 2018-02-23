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
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
public class AbstractFileTreeTest extends Specification {
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
        FileVisitor visitor = Mock()
        def tree = new TestFileTree([file1, file2])

        given:
        _ * file1.relativePath >> new RelativePath(true, 'a.txt')
        _ * file2.relativePath >> new RelativePath(true, 'b.html')

        when:
        def filtered = tree.matching { include '*.txt' }
        filtered.visit(visitor)

        then:
        1 * visitor.visitFile(file1)
        0 * visitor._
    }

    def canFilterTreeUsingAction() {
        FileVisitDetails file1 = Mock()
        FileVisitDetails file2 = Mock()
        FileVisitor visitor = Mock()
        def tree = new TestFileTree([file1, file2])

        given:
        _ * file1.relativePath >> new RelativePath(true, 'a.txt')
        _ * file2.relativePath >> new RelativePath(true, 'b.html')

        when:
        def filtered = tree.matching(new Action<PatternFilterable>() {
            @Override
            void execute(PatternFilterable patternFilterable) {
                patternFilterable.include '*.txt'
            }
        })
        filtered.visit(visitor)

        then:
        1 * visitor.visitFile(file1)
        0 * visitor._
    }

    def filteredTreeHasSameDependenciesAsThis() {
        TaskDependency buildDependencies = Mock()
        def tree = new TestFileTree([], buildDependencies)

        when:
        def filtered = tree.matching { include '*.txt' }

        then:
        filtered.buildDependencies == buildDependencies
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

    public void "can visit root elements"() {
        def tree = new TestFileTree([])
        def visitor = Mock(FileCollectionVisitor)

        when:
        tree.visitRootElements(visitor)

        then:
        1 * visitor.visitTree(tree)
        0 * visitor._
    }

    FileVisitDetails fileVisitDetails(File file) {
        return Stub(FileVisitDetails) {
            getFile() >> { file }
        }
    }

    class TestFileTree extends AbstractFileTree {
        List contents
        TaskDependency buildDependencies

        def TestFileTree(List files, TaskDependency dependencies = null) {
            this.contents = files
            this.buildDependencies = dependencies
        }

        String getDisplayName() {
            throw new UnsupportedOperationException();
        }

        FileTree visit(FileVisitor visitor) {
            contents.each { FileVisitDetails details ->
                visitor.visitFile(details)
            }
            this
        }
    }
}
