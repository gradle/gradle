/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal.builders.dsl

import org.gradle.features.internal.builders.PropertyDeclaration

/**
 * Contributes {@code property(...)} and {@code listProperty(...)} DSL methods backed by
 * {@link #getProperties()}.
 */
trait HasProperties {
    abstract List<PropertyDeclaration> getProperties()

    /** Adds a {@code Property<T>} getter. */
    void property(String name, Class type) {
        properties.add(PropertyDeclaration.property(name, type))
    }

    /** Adds a {@code Property<T>} getter with optional configuration (e.g. annotations). */
    void property(String name, Class type,
        @DelegatesTo(value = PropertyDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        properties.add(ClosureConfigure.configure(PropertyDeclaration.property(name, type), config))
    }

    /** Adds a {@code ListProperty<T>} getter. */
    void listProperty(String name, Class elementType) {
        properties.add(PropertyDeclaration.listProperty(name, elementType))
    }

    /** Adds a {@code ListProperty<T>} getter with optional configuration. */
    void listProperty(String name, Class elementType,
        @DelegatesTo(value = PropertyDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        properties.add(ClosureConfigure.configure(PropertyDeclaration.listProperty(name, elementType), config))
    }
}
