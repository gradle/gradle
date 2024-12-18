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

import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.flow.FlowParameters
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.process.ExecOperations


@ServiceScope(Scope.Build::class)
internal
class FlowScheduler(
    instantiatorFactory: InstantiatorFactory,
    serviceRegistry: ServiceRegistry,
) {
    private
    val instantiator by lazy {
        instantiatorFactory
            .injectScheme()
            .withServices(injectableServicesOf(serviceRegistry))
            .instantiator()
    }

    fun schedule(scheduled: List<RegisteredFlowAction>) {
        scheduled.forEach { flowAction ->
            instantiator
                .newInstance(flowAction.type)
                .execute(flowAction.parameters ?: FlowParameters.None.INSTANCE)
        }
    }

    private
    fun injectableServicesOf(serviceRegistry: ServiceRegistry): ServiceRegistry {
        return ServiceRegistryBuilder.builder()
            .displayName("flow services")
            .provider { registration ->
                registration.add(ArchiveOperations::class.java, serviceRegistry.get(ArchiveOperations::class.java))
                registration.add(ExecOperations::class.java, serviceRegistry.get(ExecOperations::class.java))
                registration.add(FileSystemOperations::class.java, serviceRegistry.get(FileSystemOperations::class.java))
                // TODO: injecting the router directly leaves a hole of late discovery of models dependent on tasks
                //  as it might be too late to execute tasks at the flow execution time (e.g., in build-finished callback)
//                registration.add(IsolatedModelRouter::class.java, serviceRegistry.get(IsolatedModelRouter::class.java))
            }
            .build()
    }
}
