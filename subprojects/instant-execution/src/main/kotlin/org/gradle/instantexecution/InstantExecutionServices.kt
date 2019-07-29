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

package org.gradle.instantexecution

import org.gradle.api.internal.BuildDefinition
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry


class InstantExecutionServices : AbstractPluginServiceRegistry() {

    override fun registerBuildServices(registration: ServiceRegistration) {
        registration.addProvider(BuildServices)
    }

    override fun registerGradleServices(registration: ServiceRegistration) {
        registration.add(InstantExecutionHost::class.java)
        registration.add(DefaultInstantExecution::class.java)
    }

    internal
    object BuildServices {

        fun createClassLoaderScopeRegistryListener(
            buildDefinition: BuildDefinition,
            listenerManager: ListenerManager
        ): InstantExecutionClassLoaderScopeRegistryListener =
            when {
                SystemProperties.isEnabled in buildDefinition.startParameter.systemPropertiesArgs -> {
                    InstantExecutionClassLoaderScopeRegistryListener(
                        onDisable = listenerManager::removeListener
                    ).also(
                        listenerManager::addListener
                    )
                }
                else -> {
                    InstantExecutionClassLoaderScopeRegistryListener(
                        onDisable = { }
                    )
                }
            }
    }
}
