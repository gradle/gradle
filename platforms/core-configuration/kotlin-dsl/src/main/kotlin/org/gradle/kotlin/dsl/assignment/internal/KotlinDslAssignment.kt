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

package org.gradle.kotlin.dsl.assignment.internal

import org.gradle.internal.deprecation.DeprecationLogger


/**
 * This class is used in `kotlin-dsl` plugin from 4.0.2 to 4.1.0. To be removed in Gradle 9.0.
 */
@Suppress("unused")
object KotlinDslAssignment {

    init {
        DeprecationLogger.deprecate("Internal class ${KotlinDslAssignment::class.java.name}")
            .withAdvice("The class was most likely loaded from `kotlin-dsl` plugin version 4.1.0 or earlier version used in the build: avoid specifying a version for `kotlin-dsl` plugin.")
            .willBeRemovedInGradle9()
            .undocumented()
            .nagUser()
    }

    fun isAssignmentOverloadEnabled(): Boolean {
        return true
    }
}
