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

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.smalltalk.SmalltalkComputation
import org.gradle.api.smalltalk.SmalltalkModelRegistry

class DefaultSmalltalkModelRegistry(
    private val providerFactory: ProviderFactory,
) : SmalltalkModelRegistry {

    private val computations = mutableMapOf<Key<*>, SmalltalkComputation<*>>()

    override fun <T> getModel(key: String, type: Class<T>): Provider<T> {
        val computation = computations[Key(key, type)]
            ?: error("No such model: key='$key', type='$type'")

        return providerFactory.provider {
            val model = computation.get()
            type.cast(model)
        }
    }

    override fun <T> registerModel(key: String, type: Class<T>, provider: SmalltalkComputation<T>) {
        computations[Key(key, type)] = provider
    }

    data class Key<T>(
        val name: String,
        val type: Class<T>
    )

}
