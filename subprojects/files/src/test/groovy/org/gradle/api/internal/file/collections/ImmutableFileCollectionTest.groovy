/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.ImmutableSet
import org.gradle.api.Transformer
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.file.AbstractFileCollection
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskDependency
import org.gradle.util.UsesNativeServices
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Paths
import java.util.concurrent.Callable

@UsesNativeServices
class ImmutableFileCollectionTest extends Specification {
    def 'can create empty collection'() {
        ImmutableFileCollection collection1 = ImmutableFileCollection.of()
        ImmutableFileCollection collection2 = ImmutableFileCollection.of(new File[0])

        expect:
        collection1.files.size() == 0
        collection2.files.size() == 0
    }

    def 'empty collections are fixed instance'() {
        ImmutableFileCollection collection1 = ImmutableFileCollection.of()
        ImmutableFileCollection collection2 = ImmutableFileCollection.of()
        ImmutableFileCollection collection3 = ImmutableFileCollection.of(new File[0])

        expect:
        collection1.is(collection2)
        collection1.is(collection3)
    }

    def 'resolves specified files using FileResolver'() {
        File file1 = new File('1')
        File file2 = new File('2')
        FileResolver fileResolver = Mock()
        ImmutableFileCollection collection = ImmutableFileCollection.usingResolver(fileResolver, 'abc', 'def')

        when:
        Set<File> files = collection.getFiles()

        then:
        1 * fileResolver.resolve('abc') >> file1
        1 * fileResolver.resolve('def') >> file2
        files == [ file1, file2 ] as LinkedHashSet
    }

    def 'can use a Closure to specify a single file'() {
        File file = new File('1')
        FileResolver fileResolver = Mock()
        ImmutableFileCollection collection = ImmutableFileCollection.usingResolver(fileResolver, [{ 'abc' }] as Object[])

        when:
        Set<File> files = collection.getFiles()

        then:
        1 * fileResolver.resolve('abc') >> file
        files == [ file ] as LinkedHashSet
    }

    @Unroll
    def '#description can return null'() {
        FileResolver fileResolver = Mock()
        ImmutableFileCollection collection = ImmutableFileCollection.usingResolver(fileResolver, input)

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
        FileResolver fileResolver = Mock()
        Provider provider = Mock()
        def exception = new IllegalStateException()
        ImmutableFileCollection collection = ImmutableFileCollection.usingResolver(fileResolver, provider)

        when:
        collection.getFiles()

        then:
        1 * provider.get() >> { throw exception }
        def thrown = thrown(IllegalStateException)
        exception == thrown
    }

    def 'can create with a FileCollection without reading its contents'() {
        FileResolver fileResolver = Mock()
        FileCollection fileCollection = Mock()

        when:
        ImmutableFileCollection.usingResolver(fileResolver, fileCollection)

        then:
        0 * fileCollection.iterator()
    }

    @Unroll
    def 'can use a #description to specify the contents of the collection'() {
        File file1 = new File('1')
        File file2 = new File('2')
        FileResolver fileResolver = Mock()
        ImmutableFileCollection collection = ImmutableFileCollection.usingResolver(fileResolver, input)

        when:
        Set<File> files = collection.getFiles()

        then:
        _ * fileResolver.resolve('abc') >> file1
        _ * fileResolver.resolve('def') >> file2
        files == [ file1, file2 ] as LinkedHashSet

        where:
        description        | input
        'closure'          | ({ [ 'abc', 'def' ] } as Object[])
        'collection(list)' | [ 'abc', 'def' ]
        'array'            | ([ 'abc', 'def' ] as Object[])
        'FileCollection'   | fileCollectionOf(new File('1'), new File('2'))
        'Callable'         | (({ [ 'abc', 'def' ] } as Callable<Object>) as Object[])
        'Provider'         | providerReturning(['abc', 'def'])
        'nested objects'   | ({[{['abc', { ['def'] as String[] }]}]} as Object[])
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
        return new Provider<Object>() {
            @Override
            Object get() {
                return result
            }

            @Override
            Object getOrNull() {
                return result
            }

            @Override
            Object getOrElse(Object defaultValue) {
                return result
            }

            @Override
            def <S> Provider<S> map(Transformer<? extends S, ? super Object> transformer) {
                return result
            }

            @Override
            boolean isPresent() {
                return true
            }
        }
    }

    @Unroll
    def 'can use a #description to specify the single content of the collection'() {
        File file = new File('1')
        FileResolver fileResolver = Mock()
        ImmutableFileCollection collection = ImmutableFileCollection.usingResolver(fileResolver, input)

        when:
        Set<File> files = collection.getFiles()

        then:
        1 * fileResolver.resolve(toResolve ?: input) >> file
        files == [ file ] as LinkedHashSet

        where:
        description   | input                | toResolve
        'String'      | 'abc'                | null
        'Path'        | Paths.get('abc')     | new File('abc')
        'URI'         | new URI('file:/abc') | null
        'URL'         | new URL('file:/abc') | null
        'Directory'   | Mock(Directory)      | null
        'RegularFile' | Mock(RegularFile)    | null
    }
}
