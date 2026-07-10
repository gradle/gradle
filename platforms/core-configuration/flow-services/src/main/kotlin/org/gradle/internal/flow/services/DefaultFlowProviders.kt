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

package org.gradle.internal.flow.services

import org.gradle.api.flow.BuildWorkResult
import org.gradle.api.flow.FlowProviders
import org.gradle.api.internal.provider.AbstractMinimalProvider
import org.gradle.api.internal.provider.ValueSupplier
import org.gradle.api.provider.Provider
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


@ServiceScope(Scope.Build::class)
class DefaultFlowProviders : FlowProviders {

    private
    val buildWorkResult by lazy {
        BuildWorkResultProvider()
    }

    override fun getBuildWorkResult(): Provider<BuildWorkResult> =
        buildWorkResult
}


class BuildWorkResultProvider : AbstractMinimalProvider<BuildWorkResult>() {

    private
    var result: BuildWorkResult? = null

    fun set(result: BuildWorkResult) {
        require(this.result == null)
        this.result = result
    }

    override fun getType(): Class<BuildWorkResult> =
        BuildWorkResult::class.java

    override fun calculateOwnValue(consumer: ValueSupplier.ValueConsumer): ValueSupplier.Value<out BuildWorkResult> {
        require(result != null) {
            "Cannot access the value of '${BuildWorkResult::class.simpleName}' before it becomes available!"
        }
        return ValueSupplier.Value.ofNullable(result)
    }

    override fun calculateExecutionTimeValue(): ValueSupplier.ExecutionTimeValue<out BuildWorkResult> =
        ValueSupplier.ExecutionTimeValue.changingValue(this)
}
