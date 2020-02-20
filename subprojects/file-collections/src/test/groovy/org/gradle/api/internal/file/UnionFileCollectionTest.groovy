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

package org.gradle.api.internal.file

import org.gradle.api.Task
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

import static org.gradle.api.internal.file.AbstractFileCollectionTest.TestFileCollection

@UsesNativeServices
class UnionFileCollectionTest extends Specification {
    def file1 = new File("1")
    def file2 = new File("2")
    def file3 = new File("3")

    def containsUnionOfAllSourceCollections() {
        def source1 = new TestFileCollection(file1, file2)
        def source2 = new TestFileCollection(file2, file3)

        expect:
        def collection = new UnionFileCollection(source1, source2)
        collection.files == [file1, file2, file3] as LinkedHashSet
    }

    def contentsTrackContentsOfSourceCollections() {
        def source1 = new TestFileCollection(file1)
        def source2 = new TestFileCollection(file2, file3)

        expect:
        def collection = new UnionFileCollection(source1, source2)
        collection.files == [file1, file2, file3] as LinkedHashSet
    }

    def canAddCollection() {
        def source1 = new TestFileCollection(file1)
        def source2 = new TestFileCollection(file2)

        expect:
        def collection = new UnionFileCollection([source1])
        collection.addToUnion(source2)
        collection.files == [file1, file2] as LinkedHashSet
    }

    def dependsOnUnionOfDependenciesOfSourceCollections() {
        def task1 = Stub(Task)
        def task2 = Stub(Task)
        def task3 = Stub(Task)
        def source1 = Stub(FileCollectionInternal)
        def source2 = Stub(FileCollectionInternal)

        given:
        source1.visitDependencies(_) >> { TaskDependencyResolveContext context ->
            context.add(task1)
            context.add(task2)
        }
        source2.visitDependencies(_) >> { TaskDependencyResolveContext context ->
            context.add(task3)
            context.add(task2)
        }

        expect:
        def collection = new UnionFileCollection(source1, source2)
        collection.buildDependencies.getDependencies(null) as List == [task1, task2, task3]
    }
}
