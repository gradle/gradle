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
import org.gradle.api.internal.smalltalk.SmalltalkBuildModelRegistryInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.smalltalk.SmalltalkBuildModelLookup
import org.gradle.api.smalltalk.SmalltalkComputation
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.cc.impl.isolation.IsolatedActionDeserializer
import org.gradle.internal.cc.impl.isolation.IsolatedActionSerializer
import org.gradle.internal.cc.impl.isolation.SerializedIsolatedActionGraph
import org.gradle.internal.serialize.graph.serviceOf
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer


private
typealias IsolatedSmalltalkAction = IsolatedAction<in Consumer<Any?>>


internal
data class SmalltalkModelKey<T>(
    val name: String,
    val type: Class<T>
)


private
class IsolatedModelHolder(
    private val gradle: Gradle,
    private val isolated: SerializedIsolatedActionGraph<IsolatedSmalltalkAction>
) {

    fun get(): Any? {
        val owner = IsolateOwners.OwnerGradle(gradle)
        val action = IsolatedActionDeserializer(owner, owner.serviceOf(), owner.serviceOf())
            .deserialize(isolated)

        val result = AtomicReference<Any?>(null)
        action.execute {
            result.set(it)
        }
        return result.get()
    }
}


class DefaultSmalltalkBuildModelRegistry(
//    private val userCodeApplicationContext: UserCodeApplicationContext,
    private val providerFactory: ProviderFactory,
) : SmalltalkBuildModelRegistryInternal, SmalltalkBuildModelLookup {

    private val computations = mutableMapOf<SmalltalkModelKey<*>, SmalltalkComputation<*>>()

    private lateinit var isolatedComputations: Map<SmalltalkModelKey<*>, IsolatedModelHolder>

    override fun <T> getModel(key: String, type: Class<T>): Provider<T> {
        val computation = isolatedComputations[SmalltalkModelKey(key, type)]
            ?: error("No such model: key='$key', type='$type'")

        // TODO: should not create provider every time
        return providerFactory.provider {
            val model = computation.get()
            type.cast(model)
        }
    }

    override fun <T> registerModel(key: String, type: Class<T>, provider: SmalltalkComputation<T>): Provider<T> {
        computations[SmalltalkModelKey(key, type)] = provider

        return providerFactory.provider { TODO("not implemented") }
    }

    override fun isolateFor(gradle: Gradle) {
        isolatedComputations = computations.mapValues {
            val t = isolate(gradle, it.value)
            IsolatedModelHolder(gradle, t)
        }
        computations.clear()
    }

    private fun isolate(gradle: Gradle, computation: SmalltalkComputation<*>): SerializedIsolatedActionGraph<IsolatedSmalltalkAction> {
        val owner = IsolateOwners.OwnerGradle(gradle)
        return IsolatedActionSerializer(owner, owner.serviceOf(), owner.serviceOf())
            .serialize(toAction(computation))
    }

    private fun toAction(computation: SmalltalkComputation<*>): IsolatedSmalltalkAction {
        return IsolatedSmalltalkAction {
            accept(computation.get())
        }
    }
}
