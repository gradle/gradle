/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.provider

import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableSet
import org.gradle.internal.state.ManagedFactory
import org.gradle.util.TestUtil

class DefaultSetPropertyTest extends CollectionPropertySpec<Set<String>> {
    @Override
    DefaultSetProperty<String> property() {
        return new DefaultSetProperty<String>(host, String)
    }

    @Override
    Class<Set<String>> type() {
        return Set
    }

    @Override
    protected Class<? extends ImmutableCollection<?>> getImmutableCollectionType() {
        return ImmutableSet.class
    }

    @Override
    protected Set<String> toImmutable(Collection<String> values) {
        return ImmutableSet.copyOf(values)
    }

    @Override
    protected Set<String> toMutable(Collection<String> values) {
        return new LinkedHashSet<String>(values)
    }

    @Override
    ManagedFactory managedFactory() {
        return new ManagedFactories.SetPropertyManagedFactory(TestUtil.propertyFactory())
    }

    def "discards duplicates values and retains iteration order of added elements"() {
        given:
        property.set(["123"] as Set)
        property.add("abc")
        property.addAll(Providers.of(["123", "abc", "123", "456"]))
        property.add("123")
        property.add("def")

        expect:
        property.get() as List == ["123", "abc", "456", "def"]
    }
}
