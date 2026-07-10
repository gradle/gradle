/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.snapshot.impl

import com.google.common.collect.ImmutableList
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.state.ManagedFactoryRegistry
import spock.lang.Specification

/**
 * Unit tests for the {@link IsolatedList} type.
 */
final class IsolatedListTest extends Specification {
    def classLoaderHasher = Stub(ClassLoaderHierarchyHasher) {
        getClassLoaderHash(_ as ClassLoader) >> TestHashCodes.hashCodeFrom(123)
    }
    def managedFactoryRegistry = Mock(ManagedFactoryRegistry)
    def isolatableFactory = new DefaultIsolatableFactory(classLoaderHasher, managedFactoryRegistry)

    def "can coerce back to list #list"() {
        given:
        def isolated = isolatableFactory.isolate(list)

        when:
        def result = isolated.coerce(ArrayList)

        then:
        result == list

        where:
        list << [["a", "b", "c"], [1, 2, 3]]
    }

    def "can't coerce list to non-constructable/populatable list type or other invalid type #list -> #invalidType"() {
        given:
        def isolated = isolatableFactory.isolate(list)

        when:
        def result = isolated.coerce(ImmutableList)

        then:
        result == null

        where:
        list                | invalidType
        ["a", "b", "c"]     | Integer
        ["a", "b", "c"]     | String
        ["a", "b", "c"]     | List
        ["a", "b", "c"]     | ImmutableList
    }
}
