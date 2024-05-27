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

import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.internal.tasks.properties.InspectionSchemeFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistry


internal
object FlowServicesProvider : ServiceRegistrationProvider {

    @Provides
    fun createFlowProviders(): FlowProviders =
        DefaultFlowProviders()

    @Provides
    fun createBuildFlowScope(
        objectFactory: ObjectFactory,
        flowScheduler: FlowScheduler,
        flowProviders: FlowProviders,
        flowParametersInstantiator: FlowParametersInstantiator,
        instantiatorFactory: InstantiatorFactory,
        listenerManager: ListenerManager
    ): FlowScope = objectFactory.newInstance(
        BuildFlowScope::class.java,
        flowScheduler,
        flowProviders,
        flowParametersInstantiator,
        instantiatorFactory
    ).also {
        listenerManager.addListener(it)
    }

    @Provides
    fun createFlowScheduler(
        instantiatorFactory: InstantiatorFactory,
        services: ServiceRegistry,
    ): FlowScheduler = FlowScheduler(
        instantiatorFactory,
        services,
    )

    @Provides
    fun createFlowParametersInstantiator(
        inspectionSchemeFactory: InspectionSchemeFactory,
        instantiatorFactory: InstantiatorFactory,
        services: ServiceRegistry,
    ) = FlowParametersInstantiator(
        inspectionSchemeFactory,
        instantiatorFactory,
        services
    )
}
