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

import org.gradle.api.IsolatedAction
import org.gradle.api.internal.provider.AbstractMinimalProvider
import org.gradle.api.internal.provider.ValueSupplier
import org.gradle.api.internal.smalltalk.SmalltalkBuildModelRegistryInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.api.smalltalk.SmalltalkBuildModelLookup
import org.gradle.api.smalltalk.SmalltalkComputation
import org.gradle.internal.Describables
import org.gradle.internal.Try
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.cc.impl.InputTrackingState
import org.gradle.internal.cc.impl.isolation.IsolatedActionDeserializer
import org.gradle.internal.cc.impl.isolation.IsolatedActionSerializer
import org.gradle.internal.cc.impl.isolation.SerializedIsolatedActionGraph
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.serialize.graph.serviceOf
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer


internal
typealias IsolatedSmalltalkAction = IsolatedAction<in Consumer<Any?>>


internal
data class SmalltalkModelKey<T>(
    val name: String,
    val type: Class<T>
)


class DefaultSmalltalkBuildModelRegistry(
//    private val userCodeApplicationContext: UserCodeApplicationContext,
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory,
    listenerManager: ListenerManager,
    private val inputTrackingState: InputTrackingState,
    private val gradle: Gradle
) : SmalltalkBuildModelRegistryInternal, SmalltalkBuildModelLookup {

    private val computationListener = listenerManager.createAnonymousBroadcaster(SmalltalkComputationListener::class.java)
    private val providerMap = mutableMapOf<SmalltalkModelKey<*>, SmalltalkModelProvider<*>>()

    override fun <T> getModel(key: String, type: Class<T>): Provider<T> {
        val modelKey = SmalltalkModelKey(key, type)
        val provider = providerMap[modelKey]
            ?: error("No such model: key='$key', type='$type'")
        return provider.uncheckedCast()
    }

    override fun <T> registerModel(key: String, type: Class<T>, provider: SmalltalkComputation<T>): Provider<T> {
        val modelKey = SmalltalkModelKey(key, type)
        val modelProvider = createProvider(modelKey, provider)
        providerMap[modelKey] = modelProvider
        return modelProvider
    }

    private fun <T> createProvider(key: SmalltalkModelKey<T>, computation: SmalltalkComputation<T>): SmalltalkModelProvider<T> {
        val container = LazilyObtainedModelValue<T>(computation, calculatedValueContainerFactory, inputTrackingState, gradle)
        return SmalltalkModelProvider(key, container)
    }

    override fun isolateAllModelProviders() {
        for (modelProvider in providerMap.values) {
            modelProvider.isolateIfNotAlready()
        }
    }
}
