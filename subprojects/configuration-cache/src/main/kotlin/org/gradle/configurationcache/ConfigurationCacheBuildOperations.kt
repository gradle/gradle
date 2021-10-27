/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.CallableBuildOperation


internal
fun <T> BuildOperationExecutor.withLoadOperation(block: () -> T) =
    withOperation("Load configuration cache state", block)


internal
fun BuildOperationExecutor.withStoreOperation(block: () -> Unit) =
    withOperation("Store configuration cache state", block)


private
fun <T> BuildOperationExecutor.withOperation(displayName: String, block: () -> T): T {
    return call(object : CallableBuildOperation<T> {

        override fun description(): BuildOperationDescriptor.Builder =
            BuildOperationDescriptor.displayName(displayName)

        override fun call(context: BuildOperationContext): T {
            return block()
        }
    })
}
