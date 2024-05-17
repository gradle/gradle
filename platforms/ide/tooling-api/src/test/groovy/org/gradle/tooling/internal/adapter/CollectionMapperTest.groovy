/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.adapter

import org.gradle.tooling.model.DomainObjectSet
import spock.lang.Specification

class CollectionMapperTest extends Specification {
    final def mapper = new CollectionMapper()

    def "maps collection types"() {
        expect:
        def collection = mapper.createEmptyCollection(sourceType)
        collection.class == collectionType

        where:
        sourceType      | collectionType
        Collection      | ArrayList
        List            | ArrayList
        DomainObjectSet | ArrayList
        Set             | LinkedHashSet
        SortedSet       | TreeSet
    }

    def "maps map types"() {
        expect:
        def map = mapper.createEmptyMap(sourceType)
        map.getClass() == mapType

        where:
        sourceType | mapType
        Map        | LinkedHashMap
        SortedMap  | TreeMap
    }
}
