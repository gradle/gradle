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
import com.google.common.collect.ImmutableList
import org.gradle.internal.state.ManagedFactory
import org.gradle.util.TestUtil

class DefaultListPropertyTest extends CollectionPropertySpec<List<String>> {
    @Override
    AbstractCollectionProperty<String, List<String>> propertyWithDefaultValue() {
        return property()
    }

    @Override
    DefaultListProperty<String> property() {
        return new DefaultListProperty<String>(host, String)
    }

    @Override
    Class<List<String>> type() {
        return List
    }

    @Override
    protected Class<? extends ImmutableCollection<?>> getImmutableCollectionType() {
        return ImmutableList.class
    }

    @Override
    protected List<String> toImmutable(Collection<String> values) {
        return ImmutableList.copyOf(values)
    }

    @Override
    protected List<String> toMutable(Collection<String> values) {
        return new ArrayList<String>(values)
    }

    @Override
    ManagedFactory managedFactory() {
        return new ManagedFactories.ListPropertyManagedFactory(TestUtil.propertyFactory())
    }
}
