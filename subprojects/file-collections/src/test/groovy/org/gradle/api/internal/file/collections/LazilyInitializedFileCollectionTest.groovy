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
package org.gradle.api.internal.file.collections

import org.gradle.api.Task
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class LazilyInitializedFileCollectionTest extends Specification {
    def createCount = 0
    def taskDependenciesCount = 0
    def task = Stub(Task)
    def fileCollection = new LazilyInitializedFileCollection(TestFiles.taskDependencyFactory()) {
        @Override
        String getDisplayName() {
            return "test collection"
        }

        @Override
        void visitDependencies(TaskDependencyResolveContext context) {
            taskDependenciesCount++
            context.add(task)
        }

        @Override
        FileCollectionInternal createDelegate() {
            createCount++
            TestFiles.fixed(new File("foo"))
        }
    }

    def "creates delegate on first access"() {
        expect:
        createCount == 0

        when:
        def files = fileCollection.files

        then:
        createCount == 1
        files == [new File("foo")] as Set

        when:
        fileCollection.files

        then:
        createCount == 1
        files == [new File("foo")] as Set
    }

    def "does not create delegate when task dependencies are queried"() {
        expect:
        createCount == 0
        taskDependenciesCount == 0

        when:
        fileCollection.buildDependencies

        then:
        createCount == 0
        taskDependenciesCount == 0

        when:
        def deps = fileCollection.buildDependencies.getDependencies(Stub(Task))

        then:
        deps as List == [task]
        createCount == 0
        taskDependenciesCount == 1

        when:
        deps = fileCollection.buildDependencies.getDependencies(Stub(Task))

        then:
        deps as List == [task]
        createCount == 0
        taskDependenciesCount == 2
    }
}
