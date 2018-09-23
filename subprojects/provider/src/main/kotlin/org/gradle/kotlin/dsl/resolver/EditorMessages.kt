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

package org.gradle.kotlin.dsl.resolver


object EditorMessages {

    private
    const val ideLogs = "see IDE logs for more information"

    const val failure = "Script dependencies resolution failed, $ideLogs"
    const val failureUsingPrevious = "Script dependencies resolution failed, using previous dependencies, $ideLogs"

    private
    const val gradleTasks = "run 'gradle tasks' for more information"

    const val buildConfigurationFailed = "Build configuration failed, $gradleTasks"
    const val buildConfigurationFailedInCurrentScript = "This script caused build configuration to fail, $gradleTasks"

    fun defaultErrorMessageFor(cause: Throwable) =
        "${cause::class.java.name}, $gradleTasks"
}
