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

import org.gradle.api.Action
import org.gradle.api.NonExtensible
import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowActionSpec
import org.gradle.api.flow.FlowParameters
import org.gradle.api.flow.FlowScope
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolated.IsolationScheme
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import javax.inject.Inject


@NonExtensible
@ServiceScope(Scopes.Build::class)
open class BuildFlowScope @Inject constructor(
    instantiatorFactory: InstantiatorFactory,
    services: ServiceRegistry
) : FlowScope {

    private
    val isolationScheme by lazy {
        IsolationScheme(
            FlowAction::class.java,
            FlowParameters::class.java,
            FlowParameters.None::class.java
        )
    }

    private
    val paramsInstantiator by lazy {
        instantiatorFactory.decorateScheme().withServices(services).instantiator()
    }

    private
    val specInstantiator by lazy {
        instantiatorFactory.decorateLenientScheme().withServices(services).instantiator()
    }

    private
    val actions = mutableListOf<RegisteredFlowAction>()

    override fun <P : FlowParameters> always(
        action: Class<out FlowAction<P>>,
        configure: Action<in FlowActionSpec<P>>
    ): FlowScope.Registration<P> {
        val parameters = configureParametersFor(action, configure)
        synchronized(action) {
            actions.add(RegisteredFlowAction(action, parameters))
        }
        return DefaultFlowScopeRegistration()
    }

    private
    fun <P : FlowParameters> configureParametersFor(
        action: Class<out FlowAction<P>>,
        configure: Action<in FlowActionSpec<P>>
    ): P? = parametersTypeOf(action)?.let { parametersType ->
        val parameters = paramsInstantiator.newInstance(parametersType)
        val spec = specInstantiator.newInstance(DefaultFlowActionSpec::class.java, parameters)
        configure.execute(spec.uncheckedCast())
        parameters
    }

    private
    fun <P : FlowParameters, T : FlowAction<P>> parametersTypeOf(action: Class<T>): Class<P>? =
        isolationScheme.parameterTypeFor(action)
}


private
data class RegisteredFlowAction(
    val type: Class<out FlowAction<*>>,
    val parameters: Any?
)


private
class DefaultFlowScopeRegistration<P : FlowParameters> : FlowScope.Registration<P>


@NonExtensible
private
open class DefaultFlowActionSpec<P : FlowParameters>(
    private val parameters: P
) : FlowActionSpec<P> {

    override fun getParameters(): P =
        parameters

    override fun toString(): String =
        "FlowActionSpec($parameters)"
}
