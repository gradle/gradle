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
import org.gradle.features.internal.builders.PropertyTypeDeclaration

/**
 * Contributes {@code sharedProperty(...)} that stores a shared-type reference as a
 * {@link PropertyDeclaration} entry in {@link #getProperties()}.
 *
 * <p>Use on contexts where the shared-typed property is rendered as a plain getter
 * returning the shared type directly (e.g. {@code BuildModelDeclaration}).</p>
 */
trait HasSharedRefInProperties {
    abstract List<PropertyDeclaration> getProperties()

    /** Adds a property whose type is a previously declared shared type. */
    void sharedProperty(String name, PropertyTypeDeclaration ref) {
        properties.add(PropertyDeclaration.sharedRef(name, ref))
    }
}
