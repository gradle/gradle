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

package org.gradle.model.internal.core

import org.gradle.api.DomainObjectCollection
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.DefaultPolymorphicNamedEntityInstantiator
import org.gradle.internal.Actions
import spock.lang.Specification

class DomainObjectCollectionBackedModelMapTest extends Specification {
    def "created items get added to the backing collection"() {
        given:
        def backingCollection = Mock(DomainObjectCollection)
        def instantiator = Mock(NamedEntityInstantiator)
        def modelMap = DomainObjectCollectionBackedModelMap.wrap("thing", SomeType, backingCollection, instantiator, new Named.Namer(), Actions.doNothing())

        when:
        modelMap.create("alma")

        then:
        1 * instantiator.create("alma", SomeType) >>  { new SomeType(name: "alma") }
        1 * backingCollection.add({ item -> item.name == "alma" })
        1 * backingCollection.iterator() >> { Collections.emptyIterator() }
        0 * _
    }

    class SomeType implements Named {
        String name
    }

    def "reasonable error message when creating a non-constructible type"() {
        given:
        def backingCollection = new DefaultDomainObjectSet(SomeType, CollectionCallbackActionDecorator.NOOP)
        def instantiator = new DefaultPolymorphicNamedEntityInstantiator(SomeType, "the collection")
        instantiator.registerFactory(SomeType, new NamedDomainObjectFactory<SomeType>(){
            SomeType create(String name) {
                return new SomeType(name: name)
            }
        })
        def modelMap = new DomainObjectCollectionBackedModelMap("thing", SomeType, backingCollection, instantiator, new Named.Namer(), Actions.doNothing())

        when:
        modelMap.create("alma", List)

        then:
        def e = thrown InvalidUserDataException
        e.message.contains("Cannot create a List because this type is not known to the collection. Known types are: SomeType")
    }
}
