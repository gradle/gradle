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

class EmptyFileCollectionExecutionTimeValueTest extends Specification {

    def "EmptyFileCollection supports execution time value"() {
        given:
        def collection = new EmptyFileCollection("foo")

        when:
        def executionTimeValue = collection.calculateExecutionTimeValue()

        then:
        executionTimeValue.present

        when:
        def newCollection = executionTimeValue.get().toFileCollection(fileCollectionFactory())

        then:
        newCollection.isEmpty()
        newCollection.displayName == "foo"
    }

    def "EmptyFileCollections with default display name produce the same execution time value"() {
        given:
        def collection1 = new EmptyFileCollection(FileCollectionInternal.DEFAULT_COLLECTION_DISPLAY_NAME)
        def collection2 = new EmptyFileCollection(FileCollectionInternal.DEFAULT_COLLECTION_DISPLAY_NAME)

        when:
        def executionTimeValue1 = collection1.calculateExecutionTimeValue()
        def executionTimeValue2 = collection2.calculateExecutionTimeValue()

        then:
        executionTimeValue1.get() == executionTimeValue2.get()
    }
}
