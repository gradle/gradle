/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.extensions.core

import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.CallableBuildOperation


/**
 * Simplified [CallableBuildOperation] runner.
 */
fun <T : Any> BuildOperationRunner.call(
    descriptor: () -> BuildOperationDescriptor.Builder,
    operation: (BuildOperationContext) -> T
): T = call(callableBuildOperation<T>(descriptor, operation))


/**
 * Simplified [CallableBuildOperation] builder.
 */
fun <T : Any> callableBuildOperation(
    displayName: String,
    operation: (BuildOperationContext) -> T
) = callableBuildOperation(
    { BuildOperationDescriptor.displayName(displayName) },
    operation
)


/**
 * Simplified [CallableBuildOperation] builder.
 */
fun <T : Any> callableBuildOperation(
    descriptor: () -> BuildOperationDescriptor.Builder,
    operation: (BuildOperationContext) -> T
) = object : CallableBuildOperation<T> {
    override fun description(): BuildOperationDescriptor.Builder =
        descriptor()

    override fun call(context: BuildOperationContext): T =
        operation(context)
}
