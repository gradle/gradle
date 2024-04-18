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

package org.gradle.internal.declarativedsl.model.annotations

import com.google.common.collect.ImmutableSet
import org.gradle.declarative.dsl.model.annotations.RestrictedNested
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.properties.PropertyVisitor
import org.gradle.internal.properties.annotations.AbstractPropertyAnnotationHandler
import org.gradle.internal.properties.annotations.PropertyAnnotationHandler
import org.gradle.internal.properties.annotations.PropertyMetadata


class RestrictedNestedAnnotationHandler : AbstractPropertyAnnotationHandler(RestrictedNested::class.java, PropertyAnnotationHandler.Kind.OTHER, ImmutableSet.of()) {
    override fun isPropertyRelevant(): Boolean {
        return true
    }

    override fun visitPropertyValue(propertyName: String, value: PropertyValue, propertyMetadata: PropertyMetadata, visitor: PropertyVisitor) {
        TODO("Not yet implemented")
    }
}
