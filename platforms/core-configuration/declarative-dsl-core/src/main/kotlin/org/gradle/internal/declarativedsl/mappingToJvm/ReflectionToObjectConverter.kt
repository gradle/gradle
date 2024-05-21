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

package org.gradle.internal.declarativedsl.mappingToJvm

import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.internal.declarativedsl.objectGraph.ObjectReflection


/**
 * Takes a given {@link ObjectReflection} and applies its operations to the underlying object graph.
 */
interface ReflectionToObjectConverter {
    fun apply(objectReflection: ObjectReflection, conversionFilter: ConversionFilter = ConversionFilter.none)
    fun interface ConversionFilter {
        fun filterProperties(dataObjectReflection: ObjectReflection.DataObjectReflection): Iterable<DataProperty>

        companion object {
            val none = ConversionFilter { dataObjectReflection -> dataObjectReflection.properties.keys }
        }
    }
}
