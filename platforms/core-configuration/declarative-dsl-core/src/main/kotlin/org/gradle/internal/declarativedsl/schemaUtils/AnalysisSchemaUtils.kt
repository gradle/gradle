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

package org.gradle.internal.declarativedsl.schemaUtils

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.SchemaMemberFunction


inline fun <reified T> AnalysisSchema.findTypeFor(): DataClass? =
    findTypeFor(T::class.java)


fun AnalysisSchema.findTypeFor(javaClass: Class<*>): DataClass? =
    dataClassesByFqName.values.find { it.name.qualifiedName == javaClass.kotlin.qualifiedName }


inline fun <reified T> AnalysisSchema.typeFor(): DataClass =
    typeFor(T::class.java)


fun AnalysisSchema.typeFor(javaClass: Class<*>): DataClass =
    findTypeFor(javaClass)
        ?: throw NoSuchElementException("no type found in the schema for '${javaClass.name}'")


fun DataClass.findPropertyNamed(name: String): DataProperty? =
    properties.find { it.name == name }


fun DataClass.propertyNamed(name: String): DataProperty =
    findPropertyNamed(name)
        ?: throw NoSuchElementException("no property named $name was found in the type $this")


fun DataClass.hasFunctionNamed(name: String): Boolean =
    memberFunctions.any { it.simpleName == name }


/**
 * A utility that finds an unambiguous [SchemaMemberFunction] by the [name] in the [DataClass.memberFunctions].
 *
 * To locate a function among multiple overloads, use [DataClass] APIs instead.
 */
fun DataClass.singleFunctionNamed(name: String): SchemaMemberFunction =
    memberFunctions.single { it.simpleName == name }
