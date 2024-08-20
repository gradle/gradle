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

package org.gradle.internal.cc.impl.isolated.models

import org.gradle.api.internal.GradleInternal
import org.gradle.api.provider.Provider
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.cc.impl.isolation.IsolatedActionDeserializer
import org.gradle.internal.cc.impl.isolation.IsolatedActionSerializer
import org.gradle.internal.cc.impl.isolation.SerializedIsolatedActionGraph
import org.gradle.internal.serialize.graph.IsolateOwner
import org.gradle.internal.serialize.graph.serviceOf
import org.gradle.internal.isolated.models.legacy.IsolatedProviderFactory

class DefaultIsolatedProviderFactory : IsolatedProviderFactory {

    override fun <T> isolate(provider: Provider<T>, owner: GradleInternal): IsolatedProviderFactory.IsolatedProviderForGradle<T> {
        val isolated = isolate(provider, IsolateOwners.OwnerGradle(owner))
        return IsolatedProviderFactory.IsolatedProviderForGradle {
            instantiate(isolated, owner)
        }
    }

    private
    fun <T> isolate(modelProducer: Provider<T>, owner: IsolateOwner) =
        IsolatedActionSerializer(owner, owner.serviceOf(), owner.serviceOf())
            .serialize(modelProducer)

    private
    fun <T> instantiate(isolated: SerializedIsolatedActionGraph<Provider<T>>, gradle: GradleInternal) = IsolateOwners.OwnerGradle(gradle).let { owner ->
        IsolatedActionDeserializer(owner, owner.serviceOf(), owner.serviceOf())
            .deserialize(isolated)
    }

}
