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

package org.gradle.instantexecution.westline

import org.gradle.api.Action
import org.gradle.api.internal.provider.AbstractReadOnlyProvider
import org.gradle.api.internal.provider.WestlineProviderFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.WestlineProvider
import org.gradle.api.provider.WestlineProviderParameters
import org.gradle.api.provider.WestlineProviderSpec
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolation.Isolatable
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.service.DefaultServiceRegistry


class DefaultWestlineProviderFactory(
    private val objects: ObjectFactory,
    private val instantiatorFactory: InstantiatorFactory,
    private val isolatableFactory: IsolatableFactory
) : WestlineProviderFactory {

    override fun <T : Any, P : WestlineProviderParameters> createProviderOf(
        providerType: Class<out WestlineProvider<T, P>>,
        configuration: Action<in WestlineProviderSpec<P>>
    ): Provider<T> {
        val parametersType = extractParametersType<P, WestlineProvider<T, P>, WestlineProviderParameters>(
            providerType,
            1
        )
        val parameters = objects.newInstance(parametersType)
        configuration.execute(DefaultWestlineProviderSpec(parameters))
        val isolatedParameters = isolatableFactory.isolate(parameters)
        return DefaultWestlineProviderProvider(
            providerType,
            parametersType,
            isolatedParameters,
            instantiatorFactory
        )
    }
}


private
class DefaultWestlineProviderSpec<P : WestlineProviderParameters>(
    private val parameters: P
) : WestlineProviderSpec<P> {
    override fun getParameters(): P = parameters
    override fun parameters(configuration: Action<in P>) {
        configuration.execute(parameters)
    }
}


private
class DefaultWestlineProviderProvider<T, P : WestlineProviderParameters>(
    private val providerType: Class<out WestlineProvider<T, P>>,
    private val parametersType: Class<P>,
    private val parameters: Isolatable<P>,
    private val instantiatorFactory: InstantiatorFactory
) : AbstractReadOnlyProvider<T>() {

    private
    val value: T? by lazy {
        createService().provide()
    }

    private
    fun createService(): WestlineProvider<T, P> {
        val serviceRegistry = DefaultServiceRegistry().apply {
            add(parametersType, parameters.isolate())
        }
        val instantiator = instantiatorFactory.inject(serviceRegistry)
        return instantiator.newInstance(providerType)
    }

    override fun getType(): Class<T>? = null

    override fun isPresent(): Boolean = value != null

    override fun getOrNull(): T? = value
}


