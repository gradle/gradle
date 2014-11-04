/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.collection.internal

import org.gradle.internal.Factory
import org.gradle.model.Managed
import org.gradle.model.internal.core.ModelType
import org.gradle.model.internal.manage.instance.DefaultModelInstantiator
import org.gradle.model.internal.manage.schema.ModelSchema
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import spock.lang.Specification

class DefaultManagedSetTest extends Specification {

    def schemaStore = new DefaultModelSchemaStore()
    def instantiator = new DefaultModelInstantiator(schemaStore)

    @Managed
    static interface Person {
        String getName()

        void setName(String name)
    }

    def <T> DefaultManagedSet<T> createManagedSet(Class<T> elementClass) {
        ModelSchema<T> elementSchema = schemaStore.getSchema(ModelType.of(elementClass))
        Factory<T> factory = Mock(Factory) {
            create() >> {
                instantiator.newInstance(elementSchema)
            }
        }
        new DefaultManagedSet<T>(factory)
    }

    def "mutative Set methods are not supported"() {
        def set = new DefaultManagedSet(null)

        when:
        set.add(null)

        then:
        thrown(UnsupportedOperationException)

        when:
        set.addAll(null)

        then:
        thrown(UnsupportedOperationException)

        when:
        set.clear()

        then:
        thrown(UnsupportedOperationException)

        when:
        set.remove(null)

        then:
        thrown(UnsupportedOperationException)

        when:
        set.removeAll([])

        then:
        thrown(UnsupportedOperationException)

        when:
        set.retainAll([])

        then:
        thrown(UnsupportedOperationException)
    }

    def "can check size"() {
        when:
        def set = createManagedSet(Person)

        then:
        set.size() == 0

        when:
        set.create {}

        then:
        set.size() == 1
    }

    def "can check emptiness"() {
        when:
        def set = createManagedSet(Person)

        then:
        set.empty

        when:
        set.create {}

        then:
        !set.empty
    }

    def "can get elements as array"() {
        given:
        def set = createManagedSet(Person)
        set.create {
            it.name = "Managed"
        }

        when:
        def array = set.toArray()

        then:
        array.size() == 1
        array.first().name == "Managed"

        when:
        array = set.toArray(new Person[0])

        then:
        array.size() == 1
        array.first().name == "Managed"
    }

    def "can check if elements are part of the set"() {
        given:
        def first = createManagedSet(Person)
        def second = createManagedSet(Person)

        when:
        first.create {}
        second.create {}
        def firstSetElement = first.toArray().first()
        def secondSetElement = second.toArray().first()

        then:
        first.contains(firstSetElement)
        second.contains(secondSetElement)

        and:
        !first.contains(secondSetElement)
        !second.contains(firstSetElement)

        and:
        first.containsAll([firstSetElement])
        !first.containsAll([firstSetElement, secondSetElement])
    }

    def "can get iterator for elements"() {
        given:
        def set = createManagedSet(Person)

        when:
        set.create {
            it.name = "Managed"
        }
        def iterator = set.iterator()

        then:
        iterator.hasNext()
        iterator.next().name == "Managed"
        !iterator.hasNext()
    }
}
