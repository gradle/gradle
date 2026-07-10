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


object EditorReports {

    const val locationAwareEditorHintsPropertyName = "org.gradle.kotlin.dsl.internal.locationAwareEditorHints"
}


object EditorMessages {

    private
    const val usingPrevious = "using previous dependencies"

    private
    const val ideLogs = "see IDE logs for more information"

    internal
    const val failure = "Script dependencies resolution failed, $ideLogs"

    internal
    const val failureUsingPrevious = "Script dependencies resolution failed, $usingPrevious, $ideLogs"

    private
    const val gradleTasks = "run 'gradle tasks' for more information"

    const val buildConfigurationFailed = "Build configuration failed, $gradleTasks"

    const val buildConfigurationFailedUsingPrevious = "Build configuration failed, $usingPrevious, $gradleTasks"

    const val buildConfigurationFailedInCurrentScript = "This script caused build configuration to fail, $gradleTasks"

    fun defaultLocationAwareHintMessageFor(runtimeFailure: Throwable) =
        "${runtimeFailure::class.java.name}, $gradleTasks"
}
