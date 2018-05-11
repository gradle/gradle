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

package org.gradle.kotlin.dsl.provider.spi

import org.gradle.internal.classpath.ClassPath


interface AccessorsProvider {

    fun forEachAccessorOf(accessibleSchema: ProjectSchema<TypeAccessibility>, action: (String) -> Unit)
}


data class AccessorsClassPath(val bin: ClassPath, val src: ClassPath) {
    companion object {
        val empty = AccessorsClassPath(ClassPath.EMPTY, ClassPath.EMPTY)
    }
}


sealed class TypeAccessibility {
    data class Accessible(val type: String) : TypeAccessibility()
    data class Inaccessible(val type: String, val reasons: List<InaccessibilityReason>) : TypeAccessibility()
}


sealed class InaccessibilityReason {

    data class NonPublic(val type: String) : InaccessibilityReason()
    data class NonAvailable(val type: String) : InaccessibilityReason()
    data class Synthetic(val type: String) : InaccessibilityReason()
    data class TypeErasure(val type: String) : InaccessibilityReason()

    val explanation
        get() = when (this) {
            is NonPublic -> "`$type` is not public"
            is NonAvailable -> "`$type` is not available"
            is Synthetic -> "`$type` is synthetic"
            is TypeErasure -> "`$type` parameter types are missing"
        }
}


/**
 * Location of the project schema snapshot taken by the _kotlinDslAccessorsSnapshot_ task relative to the root project.
 */
const val PROJECT_SCHEMA_RESOURCE_PATH = "gradle/project-schema.json"
