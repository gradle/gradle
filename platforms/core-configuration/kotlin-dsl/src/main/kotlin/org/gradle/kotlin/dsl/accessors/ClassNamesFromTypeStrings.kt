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

package org.gradle.kotlin.dsl.accessors

import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.primitiveKotlinTypeNames
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.typeProjectionStrings


internal
class ClassNamesFromTypeStrings {

    private val cache = mutableMapOf<String, ClassNamesFromTypeString>()

    fun classNamesFrom(typeString: String): ClassNamesFromTypeString =
        cache.computeIfAbsent(typeString) { classNamesFromTypeString(typeString) }
}

internal
class ClassNamesFromTypeString(
    val all: List<String>,
    val leaves: List<String>
)

internal
fun classNamesFromTypeString(typeString: String): ClassNamesFromTypeString {
    val all = mutableListOf<String>()
    val leafs = mutableListOf<String>()
    val buffer = StringBuilder()

    fun nonPrimitiveKotlinType(): String? =
        buffer.takeIf(StringBuilder::isNotEmpty)?.toString()?.let {
            if (it in primitiveKotlinTypeNames || it in typeProjectionStrings) null
            else it
        }

    typeString.forEach { char ->
        when (char) {
            '<' -> {
                nonPrimitiveKotlinType()?.also { all.add(it) }
                buffer.clear()
            }

            in " ,>" -> {
                nonPrimitiveKotlinType()?.also {
                    all.add(it)
                    leafs.add(it)
                }
                buffer.clear()
            }

            else -> buffer.append(char)
        }
    }
    nonPrimitiveKotlinType()?.also {
        all.add(it)
        leafs.add(it)
    }
    return ClassNamesFromTypeString(all, leafs)
}
