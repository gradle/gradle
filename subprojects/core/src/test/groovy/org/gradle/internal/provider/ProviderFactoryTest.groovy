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
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.provider.ProviderFactory
import org.gradle.api.internal.tasks.TaskResolver
import org.gradle.internal.UncheckedException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

class ProviderFactoryTest extends Specification {

    static final PROJECT = ProjectBuilder.builder().build()
    static final File TEST_FILE = PROJECT.file('someDir')
    static final FileCollection TEST_FILECOLLECTION = PROJECT.files('some', 'other')
    static final FileTree TEST_FILETREE = PROJECT.fileTree('someDir')

    def fileOperations = Mock(FileOperations)
    def taskResolver = Mock(TaskResolver)
    def providerFactory = new ProviderFactory(fileOperations, taskResolver)
    def project = ProjectBuilder.builder().build()

    def "cannot create provider for null value"() {
        when:
        providerFactory.defaultProvider(null)

        then:
        def t = thrown(InvalidUserDataException)
        t.message == 'Class cannot be null'

        when:
        providerFactory.lazilyEvaluatedProvider(null)

        then:
        t = thrown(InvalidUserDataException)
        t.message == 'Value cannot be null'

        when:
        providerFactory.eagerlyEvaluatedProvider(null)

        then:
        t = thrown(InvalidUserDataException)
        t.message == 'Value cannot be null'
    }

    @Unroll
    def "can create default value provider for #type"() {
        when:
        def provider = providerFactory.defaultProvider(type)

        then:
        provider
        provider.buildDependencies
        provider.get() == defaultValue

        where:
        type           | defaultValue
        Boolean        | false
        Byte           | 0
        Short          | 0
        Integer        | 0
        Long           | 0L
        Float          | 0.0f
        Double         | 0.0d
        Character      | '\u0000'
        String         | null
        File           | null
    }

    def "can create default value provider for Gradle file types"() {
        when:
        def provider = providerFactory.defaultProvider(FileCollection)

        then:
        provider

        when:
        def evaluatedValue = provider.get()
        evaluatedValue instanceof FileCollection

        then:
        1 * fileOperations.files() >> project.files()

        when:
        provider = providerFactory.defaultProvider(FileTree)

        then:
        provider

        when:
        evaluatedValue = provider.get()
        evaluatedValue instanceof FileTree

        then:
        1 * fileOperations.fileTree([:]) >> project.fileTree([:])
    }

    @Unroll
    def "can create value provider for type #type"() {
        when:
        def provider = providerFactory.eagerlyEvaluatedProvider(rawValue)

        then:
        provider
        provider.buildDependencies
        provider.get() == rawValue

        when:
        provider = providerFactory.lazilyEvaluatedProvider({ rawValue })

        then:
        provider
        provider.buildDependencies
        provider.get() == rawValue

        where:
        rawValue                    | type
        Boolean.TRUE                | Boolean.class.name
        Byte.valueOf((byte) 0)      | Byte.class.name
        Short.valueOf((short) 0)    | Short.class.name
        Integer.valueOf(0)          | Integer.class.name
        Long.valueOf(0)             | Long.class.name
        Float.valueOf(0)            | Float.class.name
        Double.valueOf(0)           | Double.class.name
        new Character('\0' as char) | Character.class.name
        ''                          | String.class.name
        TEST_FILE                   | File.class.name
        TEST_FILECOLLECTION         | FileCollection.class.name
        TEST_FILETREE               | FileTree.class.name
    }

    def "rethrows checked exception as unchecked for lazily evaluated value"() {
        when:
        def provider = providerFactory.lazilyEvaluatedProvider({ throw new Exception("something went wrong") })
        provider.get()

        then:
        def t = thrown(UncheckedException)
        t.cause.message == 'something went wrong'
    }
}
