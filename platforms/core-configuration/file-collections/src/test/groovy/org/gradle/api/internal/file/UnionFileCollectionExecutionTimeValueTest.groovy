/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.fileCollectionFactory

class UnionFileCollectionExecutionTimeValueTest extends Specification {

    def "UnionFileCollection supports execution time value"() {
        given:
        def source = [
            fileCollectionFactory().fixed("foo", new File("foo.txt")),
            fileCollectionFactory().fixed("bar", new File("bar.txt"))
        ]
        def collection = new UnionFileCollection(DefaultTaskDependencyFactory.withNoAssociatedProject(), source)

        when:
        def executionTimeValue = collection.calculateExecutionTimeValue()

        then:
        executionTimeValue.present

        when:
        def newCollection = executionTimeValue.get().toFileCollection(fileCollectionFactory())

        then:
        newCollection.files ==  [new File("foo.txt"), new File("bar.txt")] as Set
        newCollection.displayName == "file collection"
    }

    def "UnionFileCollection supports absence of execution time value for source"() {
        given:
        def emptySource = new EmptyExecutionTimeValueFileCollection()
        def fixedSource = fileCollectionFactory().fixed("foo", new File("foo.txt"))
        def collection = new UnionFileCollection(DefaultTaskDependencyFactory.withNoAssociatedProject(), [emptySource, fixedSource])

        when:
        def executionTimeValue = collection.calculateExecutionTimeValue()

        then:
        !executionTimeValue.present
    }
}
