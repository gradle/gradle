/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.declarativedsl.project

import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.internal.declarativedsl.schemaBuilder.ExtractionResult
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractionMetadata
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractionResult
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import kotlin.reflect.KClass


internal
class ExtensionProperties(private val extensionPropertiesByClass: Map<KClass<*>, Iterable<DataProperty>>) : PropertyExtractor {
    override fun extractProperties(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): Iterable<PropertyExtractionResult> =
        extensionPropertiesByClass[kClass]?.mapNotNull {
            if (propertyNamePredicate(it.name)) {
                ExtractionResult.Extracted(it, PropertyExtractionMetadata(emptyList(), null))
            } else null
        } ?: emptyList()
}
