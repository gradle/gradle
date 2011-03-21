/*
 * Copyright 2011 the original author or authors.
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

import spock.lang.Specification
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.file.FileCollection
import java.util.concurrent.Callable
import org.gradle.api.internal.file.SingletonFileCollection
import org.gradle.api.tasks.TaskDependency

class DefaultFileCollectionResolveContextTest extends Specification {
    final FileResolver resolver = Mock()
    final TaskDependency builtBy = Mock()
    final DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext(resolver, builtBy)

    def resolvesWhenNothingAdded() {
        expect:
        context.resolve() == []
    }

    def resolvesAMinimalFileCollection() {
        MinimalFileCollection fileCollection = Mock()

        when:
        context.add(fileCollection)
        def result = context.resolve()

        then:
        result.size() == 1
        result[0] instanceof FileCollectionAdapter
        result[0].fileCollection == fileCollection
    }

    def resolvesAMinimalFileTree() {
        MinimalFileTree fileTree = Mock()

        when:
        context.add(fileTree)
        def result = context.resolve()

        then:
        result.size() == 1
        result[0] instanceof FileTreeAdapter
        result[0].tree == fileTree
    }

    def resolvesAFileCollection() {
        FileCollection fileCollection = Mock()

        when:
        context.add(fileCollection)
        def result = context.resolve()

        then:
        result == [fileCollection]
    }

    def resolvesACompositeFileCollection() {
        CompositeFileCollection composite = Mock()
        FileCollection contents = Mock()

        when:
        context.add(composite)
        def result = context.resolve()

        then:
        result == [contents]
        1 * composite.resolve(!null) >> { it[0].add(contents) }
    }

    def resolvesCompositeFileCollectionsInDepthwiseOrder() {
        CompositeFileCollection parent1 = Mock()
        FileCollection child1 = Mock()
        CompositeFileCollection parent2 = Mock()
        FileCollection child2 = Mock()
        FileCollection child3 = Mock()

        when:
        context.add(parent1)
        context.add(child3)
        def result = context.resolve()

        then:
        result == [child1, child2, child3]
        1 * parent1.resolve(!null) >> { it[0].add(child1); it[0].add(parent2) }
        1 * parent2.resolve(!null) >> { it[0].add(child2) }
    }

    def resolvesAClosure() {
        FileCollection content = Mock()

        when:
        context.add { content }
        def result = context.resolve()

        then:
        result == [content]
    }

    def resolvesAClosureWhichReturnsNull() {
        when:
        context.add { null }
        def result = context.resolve()

        then:
        result == []
    }

    def resolvesACallable() {
        FileCollection content = Mock()
        Callable<?> callable = Mock()

        when:
        context.add(callable)
        def result = context.resolve()

        then:
        1 * callable.call() >> content
        result == [content]
    }

    def resolvesACallableWhichReturnsNull() {
        Callable<?> callable = Mock()

        when:
        context.add(callable)
        def result = context.resolve()

        then:
        1 * callable.call() >> null
        result == []
    }

    def resolvesAnIterable() {
        FileCollection content = Mock()
        Iterable<Object> iterable = Mock()

        when:
        context.add(iterable)
        def result = context.resolve()

        then:
        1 * iterable.iterator() >> [content].iterator()
        result == [content]
    }

    def resolvesAnArray() {
        FileCollection content = Mock()

        when:
        context.add([content] as Object[])
        def result = context.resolve()

        then:
        result == [content]
    }

    def usesFileResolverToResolveOtherTypes() {
        File file1 = new File('a')
        File file2 = new File('b')

        when:
        context.add('a')
        context.add('b')
        def result = context.resolve()

        then:
        result.size() == 2
        result[0] instanceof SingletonFileCollection
        result[0].file == file1
        result[0].builtBy == builtBy
        result[1] instanceof SingletonFileCollection
        result[1].file == file2
        result[1].builtBy == builtBy
        1 * resolver.resolve('a') >> file1
        1 * resolver.resolve('b') >> file2
    }

    def canPushContext() {
        FileResolver fileResolver = Mock()
        TaskDependency taskDependency = Mock()
        File file = new File('a')

        when:
        context.push(fileResolver, taskDependency).add('a')
        def result = context.resolve()

        then:
        result.size() == 1
        result[0] instanceof SingletonFileCollection
        result[0].file == file
        result[0].builtBy == taskDependency
        1 * fileResolver.resolve('a') >> file
    }
}
