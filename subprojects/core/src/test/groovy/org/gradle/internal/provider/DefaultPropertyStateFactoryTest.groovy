/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.provider

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.provider.DefaultPropertyStateFactory
import org.gradle.api.internal.tasks.TaskResolver
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

class DefaultPropertyStateFactoryTest extends Specification {

    static final PROJECT = ProjectBuilder.builder().build()
    static final File TEST_FILE = PROJECT.file('someDir')
    static final FileCollection TEST_FILECOLLECTION = PROJECT.files('some', 'other')
    static final FileTree TEST_FILETREE = PROJECT.fileTree('someDir')

    def taskResolver = Mock(TaskResolver)
    def providerFactory = new DefaultPropertyStateFactory(taskResolver)

    def "cannot create property state for null value"() {
        when:
        providerFactory.property(null)

        then:
        def t = thrown(InvalidUserDataException)
        t.message == 'Class cannot be null'
    }

    @Unroll
    def "can create property state for #type"() {
        when:
        def propertyState = providerFactory.property(type)
        propertyState.set(value)

        then:
        propertyState
        propertyState.buildDependencies
        propertyState.get() == value

        where:
        type           | value
        Boolean        | true
        Byte           | 1
        Short          | 2
        Integer        | 3
        Long           | 4L
        Float          | 5.5f
        Double         | 6.6d
        Character      | '\u1234'
        String         | 'hello'
        File           | TEST_FILE
        FileCollection | TEST_FILECOLLECTION
        FileTree       | TEST_FILETREE
    }
}
