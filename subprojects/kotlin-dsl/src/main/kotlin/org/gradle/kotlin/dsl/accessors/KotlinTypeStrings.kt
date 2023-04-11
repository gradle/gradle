/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.accessors

import org.gradle.api.reflect.TypeOf


internal
fun kotlinTypeStringFor(type: TypeOf<*>): String = type.run {
    when {
        isArray ->
            "Array<${kotlinTypeStringFor(componentType!!)}>"
        isParameterized ->
            "$parameterizedTypeDefinition<${actualTypeArguments.joinToString(transform = ::kotlinTypeStringFor)}>"
        isWildcard ->
            (upperBound ?: lowerBound)?.let(::kotlinTypeStringFor) ?: "Any"
        else ->
            toString().let { primitiveTypeStrings[it] ?: it }
    }
}


internal
val primitiveTypeStrings =
    mapOf(
        "java.lang.Object" to "Any",
        "java.lang.String" to "String",
        "java.lang.Character" to "Char",
        "char" to "Char",
        "java.lang.Boolean" to "Boolean",
        "boolean" to "Boolean",
        "java.lang.Byte" to "Byte",
        "byte" to "Byte",
        "java.lang.Short" to "Short",
        "short" to "Short",
        "java.lang.Integer" to "Int",
        "int" to "Int",
        "java.lang.Long" to "Long",
        "long" to "Long",
        "java.lang.Float" to "Float",
        "float" to "Float",
        "java.lang.Double" to "Double",
        "double" to "Double"
    )


internal
val primitiveKotlinTypeNames = primitiveTypeStrings.values.toHashSet()
