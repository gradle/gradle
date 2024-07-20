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

import org.gradle.api.internal.smalltalk.SmalltalkComputationListener
import org.gradle.api.invocation.Gradle
import org.gradle.api.smalltalk.SmalltalkComputation
import org.gradle.internal.Describables
import org.gradle.internal.Try
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.serialize.isolated.SerializedIsolatedActionGraph
import org.gradle.internal.event.AnonymousListenerBroadcast
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.serialize.graph.serviceOf
import org.gradle.internal.serialize.isolated.IsolatedActionDeserializer
import org.gradle.internal.serialize.isolated.IsolatedActionSerializer
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer


internal
class IsolatedSmalltalkComputation(
    private val gradle: Gradle,
    private val isolated: SerializedIsolatedActionGraph<IsolatedSmalltalkAction>
) {

    fun compute(): Any? {
        val owner = IsolateOwners.OwnerGradle(gradle)
        val action = IsolatedActionDeserializer(owner, owner.serviceOf(), owner.serviceOf())
            .deserialize(isolated)

        val result = AtomicReference<Any?>(null)
        action.execute(Consumer {
            result.set(it)
        })
        return result.get()
    }
}


class LazilyObtainedModelValue<T>(
    computation: SmalltalkComputation<*>,
    calculatedValueContainerFactory: CalculatedValueContainerFactory,
    computationListener: AnonymousListenerBroadcast<SmalltalkComputationListener>,
    gradle: Gradle
) {

    // TODO: how to clear this state after the model value has been computed?
    private val modelComputation: CalculatedValue<IsolatedSmalltalkComputation> =
        calculatedValueContainerFactory.create<IsolatedSmalltalkComputation>(Describables.of("Model computation")) {
            isolate(gradle, computation)
        }

    private val modelValue: CalculatedValue<T> =
        calculatedValueContainerFactory.create<T>(Describables.of("Model value")) {
            compute(computationListener)
        }

    fun hasBeenObtained(): Boolean {
        return false
    }

    fun obtain(): Try<T> {
        modelValue.finalizeIfNotAlready()
        return modelValue.value
    }

    fun isolateIfNotAlready() {
        modelComputation.finalizeIfNotAlready()
    }

    @Suppress("UNCHECKED_CAST")
    private fun compute(computationListener: AnonymousListenerBroadcast<SmalltalkComputationListener>): T {
        computationListener.source.beforeSmalltalkModelObtained()
        return try {
            modelComputation.finalizeIfNotAlready()
            val result = modelComputation.get().compute()
            result as T
        } finally {
            computationListener.source.afterSmalltalkModelObtained()
        }
    }

    private fun isolate(gradle: Gradle, computation: SmalltalkComputation<*>): IsolatedSmalltalkComputation {
        val owner = IsolateOwners.OwnerGradle(gradle)
        val serialized = IsolatedActionSerializer(owner, owner.serviceOf(), owner.serviceOf())
            .serialize(toAction(computation))

        return IsolatedSmalltalkComputation(gradle, serialized)
    }

    private fun toAction(computation: SmalltalkComputation<*>): IsolatedSmalltalkAction {
        return IsolatedSmalltalkAction {
            val consumer: Consumer<Any?> = this.uncheckedCast()
            consumer.accept(computation.get())
        }
    }
}
