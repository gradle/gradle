/*
 * Copyright 2025 the original author or authors.
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


import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.fileCollectionFactory

class SubtractingFileCollectionExecutionTimeValueTest extends Specification {
    def "SubtractingFileCollection supports execution time value"() {
        given:
        def left = fileCollectionFactory().fixed(new File("foo.txt"), new File("bar.txt"))
        def right = fileCollectionFactory().fixed(new File("bar.txt"))
        def collection = new SubtractingFileCollection(left, right)

        when:
        def executionTimeValue = collection.calculateExecutionTimeValue()

        then:
        executionTimeValue.present

        and:
        def newCollection = executionTimeValue.get().toFileCollection(fileCollectionFactory())

        then:
        newCollection.files == [new File("foo.txt")] as Set
    }

    def "SubtractingFileCollection supports absence of execution time value"() {
        given:
        def collection = new SubtractingFileCollection(left, right)

        when:
        def executionTimeValue = collection.calculateExecutionTimeValue()

        then:
        executionTimeValue.present == isPresent

        where:
        left                                               | right                                              | isPresent
        new EmptyExecutionTimeValueFileCollection()        | fileCollectionFactory().fixed(new File("foo.txt")) | false
        fileCollectionFactory().fixed(new File("foo.txt")) | new EmptyExecutionTimeValueFileCollection()        | false
        new EmptyExecutionTimeValueFileCollection()        | new EmptyExecutionTimeValueFileCollection()        | false
        fileCollectionFactory().fixed(new File("foo.txt")) | fileCollectionFactory().fixed(new File("bar.txt")) | true
    }
}

