/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache.flow

import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.RequestedTasksResult
import org.gradle.api.internal.provider.AbstractMinimalProvider
import org.gradle.api.internal.provider.ValueSupplier
import org.gradle.api.provider.Provider
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope


@ServiceScope(Scopes.Build::class)
class DefaultFlowProviders : FlowProviders {

    private
    val requestedTasksResult by lazy {
        RequestedTasksResultProvider()
    }

    override fun getRequestedTasksResult(): Provider<RequestedTasksResult> =
        requestedTasksResult
}


internal
class RequestedTasksResultProvider : AbstractMinimalProvider<RequestedTasksResult>() {

    private
    var result: RequestedTasksResult? = null

    fun set(result: RequestedTasksResult) {
        require(this.result == null)
        this.result = result
    }

    override fun getType(): Class<RequestedTasksResult> = RequestedTasksResult::class.java

    override fun calculateOwnValue(consumer: ValueSupplier.ValueConsumer): ValueSupplier.Value<out RequestedTasksResult> {
        require(result != null) {
            "Cannot access flow provider value before it becomes available!"
        }
        return ValueSupplier.Value.ofNullable(result)
    }

    override fun calculateExecutionTimeValue(): ValueSupplier.ExecutionTimeValue<out RequestedTasksResult> {
        return ValueSupplier.ExecutionTimeValue.changingValue(this)
    }
}
