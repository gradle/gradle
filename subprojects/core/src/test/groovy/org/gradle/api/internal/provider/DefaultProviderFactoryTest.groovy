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

package org.gradle.api.internal.provider

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.internal.provider.DefaultProvider.NON_NULL_VALUE_EXCEPTION_MESSAGE

class DefaultProviderFactoryTest extends Specification {

    static final PROJECT = ProjectBuilder.builder().build()
    static final File TEST_FILE = PROJECT.file('someDir')

    def providerFactory = new DefaultProviderFactory()

    def "cannot create provider for null value"() {
        when:
        providerFactory.provider(null)

        then:
        def t = thrown(InvalidUserDataException)
        t.message == 'Value cannot be null'
    }

    @Unroll
    def "can create provider for #type"() {
        when:
        def provider = providerFactory.provider({ value })

        then:
        provider
        provider.get() == value

        where:
        type           | value
        Boolean        | true
        Byte           | Byte.valueOf((byte) 0)
        Short          | Short.valueOf((short) 0)
        Integer        | Integer.valueOf(0)
        Long           | 4L
        Float          | 5.5f
        Double         | 6.6d
        Character      | '\u1234'
        String         | 'hello'
        File           | TEST_FILE
    }

    def "cannot create property state for null value"() {
        when:
        providerFactory.property(null)

        then:
        def t = thrown(InvalidUserDataException)
        t.message == 'Class cannot be null'
    }

    @Unroll
    def "property state representing boolean and numbers provide default value for #type"() {
        given:
        def propertyState = providerFactory.property(type)

        expect:
        propertyState.get() == defaultValue

        where:
        type      | defaultValue
        Boolean   | false
        Byte      | 0
        Short     | 0
        Integer   | 0
        Long      | 0L
        Float     | 0.0f
        Double    | 0.0d
        Character | '\u0000'
    }

    @Unroll
    def "can create property state for #type"() {
        when:
        def propertyState = providerFactory.property(type)
        propertyState.set(value)

        then:
        propertyState
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
    }

    def "creating property type for Gradle file type #type throws exception upon retrieval of value"() {
        when:
        def provider = providerFactory.property(type)
        provider.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == NON_NULL_VALUE_EXCEPTION_MESSAGE

        where:
        type << [FileCollection, ConfigurableFileTree, FileTree, ConfigurableFileTree]
    }
}
