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

package org.gradle.configurationcache.services

import org.gradle.configurationcache.extensions.getBroadcaster
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.initialization.Environment
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.util.internal.GUtil
import java.io.File


/**
 * Augments the [DefaultEnvironment] to track access to properties files, environment variables and system properties.
 **/
class ConfigurationCacheEnvironment(
    private val listenerManager: ListenerManager
) : DefaultEnvironment() {

    private
    val fileResourceListener: FileResourceListener by lazy(listenerManager::getBroadcaster)

    override fun propertiesFile(propertiesFile: File): Map<String, String>? {
        fileResourceListener.fileObserved(propertiesFile)
        return super.propertiesFile(propertiesFile)
    }
}


/**
 * Gives direct access to system resources.
 */
open class DefaultEnvironment : Environment {

    override fun propertiesFile(propertiesFile: File): Map<String, String>? = when {
        propertiesFile.isFile -> GUtil.loadProperties(propertiesFile).uncheckedCast()
        else -> null
    }
}
