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
import org.gradle.api.tasks.TaskDependency
import org.gradle.test.fixtures.file.WorkspaceTest
import org.gradle.util.UsesNativeServices

@UsesNativeServices
class DefaultCompositeFileTreeTest extends WorkspaceTest {

    def "can be empty"() {
        when:
        def ft = new DefaultCompositeFileTree(Collections.emptyList())

        then:
        ft.files.isEmpty()
    }

    def "contains all files"() {
        given:
        def a1 = file("a/1.txt") << "a/1"
        def b1 = file("b/1.txt") << "b/1"
        def fileResolver = TestFiles.resolver(testDirectory)

        when:
        def a = fileResolver.resolveFilesAsTree("a")
        def b = fileResolver.resolveFilesAsTree("b")
        def composite = new DefaultCompositeFileTree(Arrays.asList(a, b))

        then:
        composite.files == [a1, b1].toSet()
    }

    def "can visit all files"() {
        given:
        def a1 = file("a/1.txt") << "a/1"
        def b1 = file("b/1.txt") << "b/1"
        def fileResolver = TestFiles.resolver(testDirectory)

        when:
        def a = fileResolver.resolveFilesAsTree("a")
        def b = fileResolver.resolveFilesAsTree("b")
        def composite = new DefaultCompositeFileTree(Arrays.asList(a, b))

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
        tree1.buildDependencies >> Stub(TaskDependency) { getDependencies(_) >> [task1, task2] }
        tree2.buildDependencies >> Stub(TaskDependency) { getDependencies(_) >> [task2, task3] }

        expect:
        def composite = new DefaultCompositeFileTree([tree1, tree2])
        composite.buildDependencies.getDependencies(null) == [task1, task2, task3] as LinkedHashSet
    }

}
