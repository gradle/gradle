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
import spock.lang.Specification

import static org.gradle.api.internal.file.AbstractFileCollectionTest.TestFileCollection

class UnionFileCollectionTest extends Specification {
    def file1 = new File("1")
    def file2 = new File("2")
    def file3 = new File("3")

    private UnionFileCollection newUnionFileCollection(FileCollectionInternal... source) {
        new UnionFileCollection(TestFiles.taskDependencyFactory(), source)
    }

    def containsUnionOfAllSourceCollections() {
        def source1 = new TestFileCollection(file1, file2)
        def source2 = new TestFileCollection(file2, file3)

        expect:
        def collection = newUnionFileCollection(source1, source2)
        collection.files.toList() == [file1, file2, file3]
        collection.sourceCollections == [source1, source2]
    }

    def contentsTrackContentsOfSourceCollections() {
        def source1 = Mock(FileCollectionInternal)
        def source2 = Mock(FileCollectionInternal)

        def collection = newUnionFileCollection(source1, source2)

        when:
        def result = collection.files

        then:
        result.toList() == [file1, file2, file3]

        1 * source1.visitStructure(_) >> { FileCollectionStructureVisitor visitor -> visitor.visitCollection(null, [file1, file2]) }
        1 * source2.visitStructure(_) >> { FileCollectionStructureVisitor visitor -> visitor.visitCollection(null, [file2, file3]) }
        0 * _

        when:
        def result2 = collection.files

        then:
        result2 != result
        result2.toList() == [file1, file2]

        1 * source1.visitStructure(_) >> { FileCollectionStructureVisitor visitor -> visitor.visitCollection(null, [file1]) }
        1 * source2.visitStructure(_) >> { FileCollectionStructureVisitor visitor -> visitor.visitCollection(null, [file2]) }
        0 * _
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
        def collection = newUnionFileCollection(source1, source2)
        collection.buildDependencies.getDependencies(null) as List == [task1, task2, task3]
    }

    def "can replace one of the source collections"() {
        def source1 = Mock(FileCollectionInternal)
        def source2 = Mock(FileCollectionInternal)
        def source3 = Mock(FileCollectionInternal)
        def source4 = Mock(FileCollectionInternal)

        def collection = newUnionFileCollection(source1, source2)

        when:
        def replaced = collection.replace(source3, {})

        then:
        1 * source1.replace(source3, _) >> source1
        1 * source2.replace(source3, _) >> source2

        replaced.is(collection)

        when:
        def replaced2 = collection.replace(source3, {})

        then:
        1 * source1.replace(source3, _) >> source1
        1 * source2.replace(source3, _) >> source4

        replaced2 != collection
        replaced2.sourceCollections == [source1, source4]
    }
}
