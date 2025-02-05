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

import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.state.ManagedFactoryRegistry
import spock.lang.Specification

/**
 * Unit tests for the {@link IsolatedArray} type.
 */
final class IsolatedArrayTest extends Specification {
    def classLoaderHasher = Stub(ClassLoaderHierarchyHasher) {
        getClassLoaderHash(_ as ClassLoader) >> TestHashCodes.hashCodeFrom(123)
    }
    def managedFactoryRegistry = Mock(ManagedFactoryRegistry)
    def isolatableFactory = new DefaultIsolatableFactory(classLoaderHasher, managedFactoryRegistry)

    def "can coerce back to array"() {
        given:
        def array = ["a", "b", "c"] as String[]
        def isolated = isolatableFactory.isolate(array)

        when:
        def result = isolated.coerce(String[].class)

        then:
        result == array
    }
}
