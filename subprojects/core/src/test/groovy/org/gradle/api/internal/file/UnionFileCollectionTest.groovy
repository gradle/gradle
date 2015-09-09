/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class UnionFileCollectionTest extends Specification {
    def containsUnionOfAllSourceCollections() {
        def file1 = new File("1")
        def file2 = new File("2")
        def file3 = new File("3")
        def source1 = Stub(FileCollectionInternal)
        def source2 = Stub(FileCollectionInternal)

        given:
        source1.files >> [file1, file2]
        source2.files >> [file2, file3]

        expect:
        def collection = new UnionFileCollection(source1, source2)
        collection.files == [file1, file2, file3] as LinkedHashSet
    }

    def contentsTrackContentsOfSourceCollections() {
        def file1 = new File("1")
        def file2 = new File("2")
        def file3 = new File("3")
        def source1 = Stub(FileCollectionInternal)
        def source2 = Stub(FileCollectionInternal)

        given:
        source1.files >> [file1]
        source2.files >>> [[file2, file3], [file3]]

        expect:
        def collection = new UnionFileCollection(source1, source2)
        collection.files == [file1, file2, file3] as LinkedHashSet
        collection.files == [file1, file3] as LinkedHashSet
    }

    def canAddCollection() {
        def file1 = new File("1")
        def file2 = new File("2")
        def source1 = Stub(FileCollectionInternal)
        def source2 = Stub(FileCollectionInternal)

        given:
        source1.files >> [file1]
        source2.files >> [file2]

        expect:
        def collection = new UnionFileCollection([source1])
        collection.add(source2)
        collection.files == [file1, file2] as LinkedHashSet
    }

    def dependsOnUnionOfDependenciesOfSourceCollections() {
        def task1 = Stub(Task)
        def task2 = Stub(Task)
        def task3 = Stub(Task)
        def source1 = Stub(FileCollectionInternal)
        def source2 = Stub(FileCollectionInternal)

        given:
        source1.buildDependencies >> Stub(TaskDependency) { getDependencies(_) >> [task1, task2] }
        source2.buildDependencies >> Stub(TaskDependency) { getDependencies(_) >> [task2, task3] }

        expect:
        def collection = new UnionFileCollection(source1, source2)
        collection.buildDependencies.getDependencies(null) == [task1, task2, task3] as LinkedHashSet
    }
}
