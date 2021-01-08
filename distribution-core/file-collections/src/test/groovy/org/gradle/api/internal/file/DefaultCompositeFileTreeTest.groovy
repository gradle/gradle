/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Task
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.test.fixtures.file.WorkspaceTest

class DefaultCompositeFileTreeTest extends WorkspaceTest {

    def "can be empty"() {
        when:
        def ft = new DefaultCompositeFileTree(TestFiles.patternSetFactory, [])

        then:
        ft.files.isEmpty()
    }

    def "contains all files"() {
        given:
        def a1 = file("a/1.txt") << "a/1"
        def b1 = file("b/1.txt") << "b/1"
        def fileResolver = TestFiles.fileCollectionFactory(testDirectory)

        when:
        def a = fileResolver.resolving(["a"]).asFileTree
        def b = fileResolver.resolving(["b"]).asFileTree
        def composite = new DefaultCompositeFileTree(TestFiles.patternSetFactory, [a, b])

        then:
        composite.files == [a1, b1].toSet()
    }

    def "can visit all files"() {
        given:
        def a1 = file("a/1.txt") << "a/1"
        def b1 = file("b/1.txt") << "b/1"
        def fileResolver = TestFiles.fileCollectionFactory(testDirectory)

        when:
        def a = fileResolver.resolving(["a"]).asFileTree
        def b = fileResolver.resolving(["b"]).asFileTree
        def composite = new DefaultCompositeFileTree(TestFiles.patternSetFactory, [a, b])

        and:
        def visited = []
        composite.visit {
            visited << it.file
        }

        then:
        visited.toSet() == [a1, b1].toSet()
    }

    def "dependencies are union of dependencies of source trees"() {
        def task1 = Stub(Task)
        def task2 = Stub(Task)
        def task3 = Stub(Task)
        def tree1 = Stub(FileTreeInternal)
        def tree2 = Stub(FileTreeInternal)

        given:
        tree1.visitDependencies(_) >> { TaskDependencyResolveContext context ->
            context.add(task1)
            context.add(task2)
        }
        tree2.visitDependencies(_) >> { TaskDependencyResolveContext context ->
            context.add(task2)
            context.add(task3)
        }

        expect:
        def composite = new DefaultCompositeFileTree(TestFiles.patternSetFactory, [tree1, tree2])
        composite.buildDependencies.getDependencies(null) as List == [task1, task2, task3]
    }

}
