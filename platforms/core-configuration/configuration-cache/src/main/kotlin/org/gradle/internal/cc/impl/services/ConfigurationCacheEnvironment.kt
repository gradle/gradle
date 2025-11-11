/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.cc.impl.services

import org.gradle.initialization.Environment
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.extensions.core.getBroadcaster
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.internal.service.scopes.EventScope
import org.gradle.internal.service.scopes.Scope
import java.io.File


/**
 * Tracks and reports access to properties files, environment variables and system properties.
 **/
class ConfigurationCacheEnvironment(
    private val listenerManager: ListenerManager,
    private val delegate: Environment
) : Environment {

    @EventScope(Scope.BuildTree::class)
    interface Listener {
        fun systemPropertiesPrefixedBy(prefix: String, snapshot: Map<String, String?>)
        fun systemProperty(name: String, value: String?)
        fun envVariablesPrefixedBy(prefix: String, snapshot: Map<String, String?>)
        fun envVariable(name: String, value: String?)
    }

    private
    val fileResourceListener: FileResourceListener by lazy(listenerManager::getBroadcaster)

    private
    val listener: Listener by lazy(listenerManager::getBroadcaster)

    override fun propertiesFile(propertiesFile: File): Map<String, String>? {
        fileResourceListener.fileObserved(propertiesFile)
        return delegate.propertiesFile(propertiesFile)
    }

    override fun getSystemProperties(): Environment.Properties =
        TrackingProperties(
            delegate.systemProperties,
            { prefix, snapshot -> listener.systemPropertiesPrefixedBy(prefix, snapshot) },
            { name, value -> listener.systemProperty(name, value) }
        )

    override fun getVariables(): Environment.Properties =
        TrackingProperties(
            delegate.variables,
            { prefix, snapshot -> listener.envVariablesPrefixedBy(prefix, snapshot) },
            { name, value -> listener.envVariable(name, value) }
        )

    private
    class TrackingProperties(
        private val delegate: Environment.Properties,
        private val onByNamePrefix: (String, Map<String, String?>) -> Unit,
        private val onByName: (String, String?) -> Unit
    ) : Environment.Properties {

        override fun byNamePrefix(prefix: String): Map<String, String> =
            delegate.byNamePrefix(prefix).also { snapshot ->
                onByNamePrefix(prefix, snapshot)
            }

        override fun get(name: String): String? =
            delegate.get(name).also { value ->
                onByName(name, value)
            }
    }
}
