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

import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.fileCollectionFactory

class FixedFileCollectionExecutionTimeValue extends Specification {

    def "FixedFileCollection supports execution time value"() {
        given:
        def collection = fileCollectionFactory().fixed("foo", new File("foo.txt"))

        when:
        def executionTimeValue = collection.calculateExecutionTimeValue()

        then:
        executionTimeValue.present

        and:
        def newCollection = executionTimeValue.get().toFileCollection(fileCollectionFactory())

        then:
        newCollection.files == [new File("foo.txt")] as Set
        newCollection.displayName == "foo"
    }
}
