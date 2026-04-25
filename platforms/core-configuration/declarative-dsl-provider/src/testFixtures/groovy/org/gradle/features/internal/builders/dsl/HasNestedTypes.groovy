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

import org.gradle.features.internal.builders.PropertyTypeDeclaration

/**
 * Contributes {@code nested(...)} and {@code ndoc(...)} DSL methods backed by
 * {@link #getNestedTypes()}.
 */
trait HasNestedTypes {
    abstract List<PropertyTypeDeclaration> getNestedTypes()

    /** Adds a {@code @Nested} type with its own properties and sub-nested types. */
    void nested(String name, String nestedTypeName,
        @DelegatesTo(value = PropertyTypeDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        nestedTypes.add(ClosureConfigure.configure(
            new PropertyTypeDeclaration(name: name, typeName: nestedTypeName),
            config
        ))
    }

    /** Adds a {@code NamedDomainObjectContainer<T>} element type. */
    void ndoc(String name, String elementTypeName,
        @DelegatesTo(value = PropertyTypeDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        nestedTypes.add(ClosureConfigure.configure(
            new PropertyTypeDeclaration(name: name, typeName: elementTypeName, isNdoc: true),
            config
        ))
    }
}
