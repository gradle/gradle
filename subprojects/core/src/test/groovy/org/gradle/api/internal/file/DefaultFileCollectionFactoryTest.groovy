/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableSet
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.internal.tasks.TaskResolver
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskDependency
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.ClassRule
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.Callable

class DefaultFileCollectionFactoryTest extends Specification {
    @ClassRule
    @Shared
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def factory = new DefaultFileCollectionFactory(TestFiles.pathToFileResolver(tmpDir.testDirectory), Stub(TaskResolver))

    def "lazily queries contents of collection created from MinimalFileSet"() {
        def contents = Mock(MinimalFileSet)
        def file = new File("a")

        when:
        def collection = factory.create(contents)

        then:
        0 * contents._

        when:
        def files = collection.files

        then:
        files == [file] as Set
        1 * contents.files >> [file]
        0 * contents._
    }

    def "lazily queries dependencies of collection created from MinimalFileSet"() {
        def contents = Mock(MinimalFileSet)
        def builtBy = Mock(TaskDependency)
        def task = Stub(Task)

        when:
        def collection = factory.create(builtBy, contents)

        then:
        0 * builtBy._

        when:
        def tasks = collection.buildDependencies.getDependencies(null)

        then:
        tasks == [task] as Set
        1 * builtBy.getDependencies(_) >> [task]
        0 * _
    }

    def "constructs a configurable collection"() {
        expect:
        def collection = factory.configurableFiles("some collection")
        collection.files.empty
        collection.buildDependencies.getDependencies(null).empty
        collection.toString() == "some collection"
    }

    def "constructs an empty collection"() {
        expect:
        def collection = factory.empty("some collection")
        collection.files.empty
        collection.buildDependencies.getDependencies(null).empty
        collection.toString() == "some collection"
    }

    def "constructs a collection with fixed contents"() {
        def file1 = new File("a")
        def file2 = new File("b")

        expect:
        def collection1 = factory.fixed("some collection", file1, file2)
        collection1.files == [file1, file2] as Set
        collection1.buildDependencies.getDependencies(null).empty
        collection1.toString() == "some collection"

        def collection2 = factory.fixed("some collection", [file1, file2])
        collection2.files == [file1, file2] as Set
        collection2.buildDependencies.getDependencies(null).empty
        collection2.toString() == "some collection"
    }

    def "returns empty collection when constructed with a list containing nothing"() {
        expect:
        def collection = factory.resolving("some collection", [])
        collection.files.empty
        collection.buildDependencies.getDependencies(null).empty
        collection.toString() == "some collection"
    }

    def "returns empty collection when constructed with an array containing nothing"() {
        expect:
        def collection = factory.resolving("some collection")
        collection.files.empty
        collection.buildDependencies.getDependencies(null).empty
        collection.toString() == "some collection"
    }

    def 'resolves specified files using FileResolver'() {
        def collection = factory.resolving('test files', 'abc', 'def')

        when:
        Set<File> files = collection.getFiles()

        then:
        collection.toString() == 'test files'
        files == [tmpDir.file('abc'), tmpDir.file('def')] as Set
    }

    def 'can use a Closure to specify a single file'() {
        def collection = factory.resolving('test files', [{ 'abc' }] as Object[])

        when:
        Set<File> files = collection.getFiles()

        then:
        collection.toString() == 'test files'
        files == [tmpDir.file('abc')] as Set
    }

    @Unroll
    def '#description can return null'() {
        def collection = factory.resolving('test files', input)

        when:
        Set<File> files = collection.getFiles()

        then:
        files.isEmpty()

        where:
        description | input
        'Closure'   | ({ null } as Object[])
        'Callable'  | (({ null } as Callable<Object>) as Object[])
    }

    def 'Provider can throw IllegalStateException'() {
        Provider provider = Mock()
        def exception = new IllegalStateException()
        def collection = factory.resolving('test files', provider)

        when:
        collection.getFiles()

        then:
        1 * provider.get() >> { throw exception }
        def thrown = thrown(IllegalStateException)
        exception == thrown
    }

    def 'lazily queries contents of a FileCollection'() {
        FileCollectionInternal fileCollection = Mock()

        when:
        def collection = factory.resolving('test files', fileCollection)

        then:
        0 * fileCollection._
        collection.toString() == 'test files'

        when:
        def files = collection.files

        then:
        1 * fileCollection.files >> { [tmpDir.file('abc')] as Set }
        files == [tmpDir.file('abc')] as Set
    }

    @Unroll
    def 'can use a #description to specify the contents of the collection'() {
        def collection = factory.resolving('test files', input)

        when:
        Set<File> files = collection.getFiles()

        then:
        files == [tmpDir.file('abc'), tmpDir.file('def')] as Set

        where:
        description        | input
        'closure'          | ({ ['abc', 'def'] } as Object[])
        'collection(list)' | ['abc', 'def']
        'array'            | (['abc', 'def'] as Object[])
        'FileCollection'   | fileCollectionOf(tmpDir.file('abc'), tmpDir.file('def'))
        'Callable'         | (({ ['abc', 'def'] } as Callable<Object>) as Object[])
        'Provider'         | providerReturning(['abc', 'def'])
        'nested objects'   | ({ [{ ['abc', { ['def'] as String[] }] }] } as Object[])
    }

    private FileCollection fileCollectionOf(final File... files) {
        return new AbstractFileCollection() {
            @Override
            String getDisplayName() {
                return 'test file collection'
            }

            @Override
            Set<File> getFiles() {
                return ImmutableSet.copyOf(files)
            }

            @Override
            TaskDependency getBuildDependencies() {
                return new DefaultTaskDependency()
            }
        }
    }

    private Provider<Object> providerReturning(Object result) {
        return Providers.of(result)
    }

    @Unroll
    def 'can use a #description to specify the single content of the collection'() {
        def collection = factory.resolving('test files', input)

        when:
        Set<File> files = collection.getFiles()

        then:
        files == [tmpDir.file('abc')] as Set

        where:
        description | input
        'String'    | 'abc'
        'Path'      | tmpDir.file('abc').toPath()
        'URI'       | tmpDir.file('abc').toURI()
        'URL'       | tmpDir.file('abc').toURI().toURL()
    }
}
