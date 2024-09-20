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

package org.gradle.internal.declarativedsl.dom.mutation

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.schemaUtils.findTypeFor


inline fun <reified T> AnalysisSchema.typeFor(): DataClass =
    typeFor(T::class.java)


fun AnalysisSchema.typeFor(javaClass: Class<*>): DataClass =
    findTypeFor(javaClass)
        ?: throw NoSuchElementException("no type found in the schema for '${javaClass.name}'")


fun AnalysisSchema.singleType(predicate: (DataClass) -> Boolean): DataClass =
    dataClassTypesByFqName.values.filterIsInstance<DataClass>().single(predicate)


fun AnalysisSchema.anyType(predicate: (DataClass) -> Boolean): Boolean =
    dataClassTypesByFqName.values.filterIsInstance<DataClass>().any(predicate)
