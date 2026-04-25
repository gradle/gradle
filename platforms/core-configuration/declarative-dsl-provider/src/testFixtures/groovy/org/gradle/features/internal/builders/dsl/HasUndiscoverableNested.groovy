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
import org.gradle.features.internal.builders.TypeShape

/**
 * Contributes {@code undiscoverable(...)} to the mixing class.
 *
 * <p>Requires {@link TypeShape#ABSTRACT_CLASS} on the mixing class and rejects
 * {@code annotations(...)} on the resulting declaration (which has no public getter).</p>
 */
trait HasUndiscoverableNested {
    abstract List<PropertyTypeDeclaration> getNestedTypes()
    abstract TypeShape getShape()

    void undiscoverable(String name, String typeName,
        @DelegatesTo(value = PropertyTypeDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        if (shape == TypeShape.INTERFACE) {
            throw new IllegalStateException(
                "undiscoverable(...) requires ABSTRACT_CLASS shape; set shape(ABSTRACT_CLASS) before declaring undiscoverable properties."
            )
        }
        def nestedType = ClosureConfigure.configure(
            new PropertyTypeDeclaration(name: name, typeName: typeName, isUndiscoverable: true),
            config
        )
        if (!nestedType.allAnnotations.isEmpty()) {
            throw new IllegalStateException(
                "annotations(...) cannot be used on an undiscoverable declaration; it has no public getter."
            )
        }
        nestedTypes.add(nestedType)
    }
}
