/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.attributes.immutable

import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.DefaultAttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil

/**
 * Supplies some example attributes and a factory for easy testing of {@link ImmutableAttributes} and related types.
 */
trait TestsImmutableAttributes {
    static final Attribute<String> FOO = Attribute.of("foo", String)
    static final Attribute<String> BAR = Attribute.of("bar", String)
    static final Attribute<Object> OTHER_BAR = Attribute.of("bar", Object.class)
    static final Attribute<String> BAZ = Attribute.of("baz", String)

    IsolatableFactory isolatableFactory = SnapshotTestUtil.isolatableFactory()
    NamedObjectInstantiator instantiator = TestUtil.objectInstantiator()

    AttributesFactory factory = new DefaultAttributesFactory(isolatableFactory, instantiator)
}
