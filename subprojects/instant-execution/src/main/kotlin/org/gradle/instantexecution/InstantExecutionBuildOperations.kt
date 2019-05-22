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

package org.gradle.instantexecution

import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.RunnableBuildOperation


class InstantExecutionStateLoadBuildOperationType private constructor()
    : BuildOperationType<InstantExecutionStateLoadBuildOperationType.Details, InstantExecutionStateLoadBuildOperationType.Results> {

    interface Details
    interface Results
}


class InstantExecutionStateStoreBuildOperationType private constructor()
    : BuildOperationType<InstantExecutionStateStoreBuildOperationType.Details, InstantExecutionStateStoreBuildOperationType.Results> {

    interface Details
    interface Results
}


private
object LoadDetails : InstantExecutionStateLoadBuildOperationType.Details


private
object LoadResults : InstantExecutionStateLoadBuildOperationType.Results


private
object StoreDetails : InstantExecutionStateStoreBuildOperationType.Details


private
object StoreResults : InstantExecutionStateLoadBuildOperationType.Results


internal
fun BuildOperationExecutor.withLoadOperation(block: () -> Unit) =
    withOperation(
        "Load instant execution state",
        LoadDetails,
        LoadResults,
        block
    )


internal
fun BuildOperationExecutor.withStoreOperation(block: () -> Unit) =
    withOperation(
        "Store instant execution state",
        StoreDetails,
        StoreResults,
        block
    )


private
fun <D, R> BuildOperationExecutor.withOperation(
    displayName: String,
    details: D,
    results: R,
    block: () -> Unit
) {
    run(object : RunnableBuildOperation {

        override fun description(): BuildOperationDescriptor.Builder =
            BuildOperationDescriptor
                .displayName(displayName)
                .details(details)

        override fun run(context: BuildOperationContext) {
            block()
            context.setResult(results)
        }
    })
}
