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

import org.gradle.internal.configurationcache.ConfigurationCacheLoadBuildOperationType
import org.gradle.internal.configurationcache.ConfigurationCacheStoreBuildOperationType
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.CallableBuildOperation


internal
fun <T : Any> BuildOperationExecutor.withLoadOperation(block: () -> T) =
    withOperation("Load configuration cache state", "Loading configuration cache state", block, LoadDetails, LoadResult)


internal
fun BuildOperationExecutor.withStoreOperation(@Suppress("UNUSED_PARAMETER") cacheKey: String, block: () -> Unit) =
    withOperation("Store configuration cache state", "Storing configuration cache state", block, StoreDetails, StoreResult)


private
object LoadDetails : ConfigurationCacheLoadBuildOperationType.Details


private
object LoadResult : ConfigurationCacheLoadBuildOperationType.Result


private
object StoreDetails : ConfigurationCacheStoreBuildOperationType.Details


private
object StoreResult : ConfigurationCacheStoreBuildOperationType.Result


private
fun <T : Any, D : Any, R : Any> BuildOperationExecutor.withOperation(displayName: String, progressDisplayName: String, block: () -> T, details: D, result: R): T =
    call(object : CallableBuildOperation<T> {
        override fun description(): BuildOperationDescriptor.Builder =
            BuildOperationDescriptor.displayName(displayName).progressDisplayName(progressDisplayName).details(details)

        override fun call(context: BuildOperationContext): T =
            block().also { context.setResult(result) }
    })
