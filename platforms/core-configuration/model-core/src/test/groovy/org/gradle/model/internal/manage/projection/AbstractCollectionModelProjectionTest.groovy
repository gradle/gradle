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

import org.gradle.model.ReadOnlyModelViewException
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelRegistrations
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.fixture.ProjectRegistrySpec
import org.gradle.model.internal.manage.schema.ManagedImplSchema
import org.gradle.model.internal.manage.schema.StructSchema
import org.gradle.model.internal.type.ModelType
import org.gradle.util.internal.ClosureBackedAction

import static org.gradle.model.internal.core.NodeInitializerContext.forType

abstract class AbstractCollectionModelProjectionTest<T, C extends Collection<T>> extends ProjectRegistrySpec {

    def collectionPath = ModelPath.path("collection")
    def internalType
    def internalTypeSchema
    def collectionProperty
    ModelType<C> collectionType
    private ModelReference<C> reference

    abstract Class<?> holderType()

    C checkable(List<T> list) {
        list
    }

    def setup() {
        internalType = holderType()
        internalTypeSchema = schemaStore.getSchema(internalType)
        assert internalTypeSchema instanceof StructSchema
        collectionProperty = internalTypeSchema.getProperty('items')
        collectionType = collectionProperty.type as ModelType<C>
        def collectionSchema = schemaStore.getSchema(collectionType)
        assert collectionSchema instanceof ManagedImplSchema
        def nodeInitializer = nodeInitializerRegistry.getNodeInitializer(forType(collectionSchema.getType()))
        reference = ModelReference.of(collectionPath, collectionType)
        registry.register(
            ModelRegistrations.of(collectionPath, nodeInitializer)
                .descriptor("define collection")
                .build()
        )
    }

    void mutate(@DelegatesTo(C) Closure<?> action) {
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
        list == checkable(['foo', 'bar'])

        and: "can query emptiness"
        !list.empty

        and: "can query size"
        list.size() == 2
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
        list == checkable(['bar'])
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
        list == checkable([])
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
        list == checkable(['bar'])
    }

    def "cannot mutate after view is closed"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
        }

        then:
        def list = registry.realize(collectionPath, collectionType)
        list == checkable(['foo', 'bar'])

        when:
        list.add 'baz'

        then:
        thrown(ReadOnlyModelViewException)
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

    def "can retainAll a collection which contains a same element types"() {
        when:
        mutate {
            addAll(['foo', 'bar', 'baz'])
            retainAll(['one', 'two', 'bar'])
        }


        then:
        def list = registry.realize(collectionPath, collectionType)
        list == checkable(['bar'])
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
        list == checkable(['bar', 'baz', 'foo'])
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
        list == checkable(['echo', 'data', 'bravo'])
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
        list == checkable(['bar'])
    }

    def "cannot remove using iterator after view is closed"() {
        when:
        mutate {
            add 'foo'
            add 'bar'
        }

        then:
        def list = registry.realize(collectionPath, collectionType)
        list == checkable(['foo', 'bar'])

        when:
        def it = list.iterator()
        it.next()
        it.remove()

        then:
        thrown(ReadOnlyModelViewException)
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
