/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.projection

import org.gradle.api.internal.ClosureBackedAction
import org.gradle.model.ModelViewClosedException
import org.gradle.model.internal.core.ModelCreators
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification

class ListModelProjectionTest extends Specification {

    def collectionPath = ModelPath.path("collection")
    def collectionType = new ModelType<List<String>>() {}
    def schemaStore = DefaultModelSchemaStore.instance
    def registry = new ModelRegistryHelper()
    private ModelReference<List<String>> reference = ModelReference.of(collectionPath, new ModelType<List<String>>() {})

    def setup() {
        registry.create(
            ModelCreators.of(collectionPath, schemaStore.getSchema(collectionType).nodeInitializer)
                .descriptor("define collection")
                .build()
        )
    }

    void mutate(@DelegatesTo(List) Closure<?> action) {
        registry.mutate(reference, new ClosureBackedAction<>(action))
    }

    def "can add and query elements"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
        }

        then:
        def list = registry.realize(collectionPath, collectionType)
        list == ['foo', 'bar']

        and: "can query emptiness"
        !list.empty

        and: "can query size"
        list.size() == 2
    }

    def "can add using index"() {
        when:
        mutate {
            add 'foo'
            add(0, 'bar')
        }

        then:
        def list = registry.realize(collectionPath, collectionType)
        list == ['bar', 'foo']

    }

    def "can remove elements"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
            remove 'foo'
        }

        then:
        def list = registry.realize(collectionPath, collectionType)
        list == ['bar']
    }

    def "can remove all elements"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
            clear()
        }

        then:
        def list = registry.realize(collectionPath, collectionType)
        list == []
    }

    def "can removeAll elements"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
            add 'baz'
            removeAll(['foo', 'baz'])
        }

        then:
        def list = registry.realize(collectionPath, collectionType)
        list == ['bar']
    }

    def "can remove using index"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
            add 'baz'
            remove(1)
        }

        then:
        def list = registry.realize(collectionPath, collectionType)
        list == ['foo', 'baz']
    }

    def "cannot mutate after view is closed"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
        }

        then:
        def list = registry.realize(collectionPath, collectionType)
        list == ['foo', 'bar']

        when:
        list.add 'baz'

        then:
        thrown(ModelViewClosedException)
    }

    def "cannot add a different type than the declared one"() {
        when:
        mutate {
            add 'foo'
            add 666
        }

        registry.realize(collectionPath, collectionType)

        then:
        ModelRuleExecutionException ex = thrown()
        ex.cause.message == 'Cannot add an element of type java.lang.Integer to a collection of java.lang.String'
    }

    def "can addAll a collection which contains a same element types"() {
        when:
        mutate {
            add 'foo'
            addAll(['bar', 'baz'])
            addAll(1, ['one', 'two'])
        }


        then:
        def list = registry.realize(collectionPath, collectionType)
        list == ['foo', 'one', 'two', 'bar', 'baz']
    }

    def "can retainAll a collection which contains a same element types"() {
        when:
        mutate {
            addAll(['foo', 'bar', 'baz'])
            retainAll(['one', 'two', 'bar'])
        }


        then:
        def list = registry.realize(collectionPath, collectionType)
        list == ['bar']
    }

    def "cannot addAll a collection which contains a different element type"() {
        when:
        mutate {
            add 'foo'
            addAll(['bar', 666])
        }

        registry.realize(collectionPath, collectionType)

        then:
        ModelRuleExecutionException ex = thrown()
        ex.cause.message == 'Cannot add an element of type java.lang.Integer to a collection of java.lang.String'
    }

    def "can sort within mutate block"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
            add 'baz'
            sort()
        }


        then:
        def list = registry.realize(collectionPath, collectionType)
        list == ['bar', 'baz', 'foo']
    }

    def "can sort within mutate block using custom comparator"() {
        when:
        mutate {
            add 'echo'
            add 'bravo'
            add 'data'
            sort { it.length() }
        }


        then:
        def list = registry.realize(collectionPath, collectionType)
        list == ['echo', 'data', 'bravo']
    }

    def "can remove using iterator"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
            def iterator = delegate.iterator()
            iterator.next()
            iterator.remove()
        }

        then:
        def list = registry.realize(collectionPath, collectionType)
        list == ['bar']
    }

    def "cannot remove using iterator after view is closed"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
        }

        then:
        def list = registry.realize(collectionPath, collectionType)
        list == ['foo', 'bar']

        when:
        def it = list.iterator()
        it.next()
        it.remove()

        then:
        thrown(ModelViewClosedException)
    }

    def "can use set(index, obj)"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
            add 'baz'
            set(0, 'foorepl')
            delegate[1] = 'barrepl'
        }


        then:
        def list = registry.realize(collectionPath, collectionType)
        list == ['foorepl', 'barrepl', 'baz']
    }

    def "can query using get(index)"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
            add 'baz'
        }


        then:
        def list = registry.realize(collectionPath, collectionType)
        list.get(1) == 'bar'
        list[2] == 'baz'
    }

    def "can convert to array"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
            add 'baz'
        }


        then:
        def list = registry.realize(collectionPath, collectionType)
        list.toArray() == ['foo', 'bar', 'baz'] as String[]
        list.toArray(new String[0]) == ['foo', 'bar', 'baz'] as String[]
    }

    def "can check contents"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
            add 'baz'
        }

        then:
        def list = registry.realize(collectionPath, collectionType)
        list.contains('foo')
        !list.contains('echo')
        list.containsAll(['foo', 'baz'])
        !list.containsAll(['foo', 'echo', 'baz'])
    }

}
