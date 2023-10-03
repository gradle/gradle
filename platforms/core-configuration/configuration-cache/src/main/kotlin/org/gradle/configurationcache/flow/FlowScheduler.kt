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

import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.flow.FlowParameters
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.process.ExecOperations


@ServiceScope(Scopes.Build::class)
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
    fun injectableServicesOf(serviceRegistry: ServiceRegistry): DefaultServiceRegistry {
        return DefaultServiceRegistry().apply {
            add(serviceRegistry.get(ArchiveOperations::class.java))
            add(serviceRegistry.get(ExecOperations::class.java))
            add(serviceRegistry.get(FileSystemOperations::class.java))
        }
    }
}
