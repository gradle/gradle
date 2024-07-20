/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.smalltalk

import org.gradle.api.internal.provider.AbstractMinimalProvider
import org.gradle.api.internal.provider.ValueSupplier
import org.gradle.api.internal.tasks.TaskDependencyResolveContext


class SmalltalkModelProvider<T>(
    private val key: SmalltalkModelKey<T>,
    private val value: LazilyObtainedModelValue<T>
) : AbstractMinimalProvider<T>() {

    fun isolateIfNotAlready() {
        value.isolateIfNotAlready()
    }

    override fun toStringNoReentrance(): String {
        return String.format("modelFor('%s'): %s", key.name, key.type.simpleName)
    }

    override fun getProducer(): ValueSupplier.ValueProducer {
        // TODO: not necessarily external value
        return ValueSupplier.ValueProducer.unknown()
    }

    override fun visitDependencies(context: TaskDependencyResolveContext) {
    }

    override fun isImmutable(): Boolean {
        return true
    }

    fun hasBeenObtained(): Boolean {
        return value.hasBeenObtained()
    }

    override fun getType(): Class<T>? {
        // TODO: can we do better?
        return null
    }

    override fun calculateExecutionTimeValue(): ValueSupplier.ExecutionTimeValue<T> {
        return if (value.hasBeenObtained()) {
            ValueSupplier.ExecutionTimeValue.ofNullable(value.obtain().get())
        } else {
            ValueSupplier.ExecutionTimeValue.changingValue(this)
        }
    }

    override fun calculateOwnValue(consumer: ValueSupplier.ValueConsumer): ValueSupplier.Value<out T> {
        return ValueSupplier.Value.ofNullable(value.obtain().get())
    }
}
