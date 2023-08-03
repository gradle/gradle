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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.configurationcache.problems.PropertyTrace
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readList
import org.gradle.configurationcache.serialization.withPropertyTrace
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.configurationcache.services.ConfigurationCacheEnvironmentChangeTracker


internal
object CachedEnvironmentStateCodec : Codec<ConfigurationCacheEnvironmentChangeTracker.CachedEnvironmentState> {

    override suspend fun WriteContext.encode(value: ConfigurationCacheEnvironmentChangeTracker.CachedEnvironmentState) {
        writeBoolean(value.cleared)

        writeCollection(value.updates) { update ->
            val keyString = update.key.toString()
            withPropertyTrace(PropertyTrace.SystemProperty(keyString, update.location)) {
                try {
                    writeClass(update.javaClass)
                    write(update.key)
                    write(update.value)
                } catch (error: Exception) {
                    onError(error) {
                        text("failed to write system property ")
                        reference(keyString)
                    }
                }
            }
        }

        writeCollection(value.removals) { removal ->
            writeString(removal.key)
        }
    }

    override suspend fun ReadContext.decode(): ConfigurationCacheEnvironmentChangeTracker.CachedEnvironmentState {
        val cleared = readBoolean()
        val updates = readList {
            val clazz = readClass()
            val key = read() as Any
            val value = read()
            when (clazz) {
                ConfigurationCacheEnvironmentChangeTracker.SystemPropertyMutate::class.java ->
                    ConfigurationCacheEnvironmentChangeTracker.SystemPropertyMutate(key, value, PropertyTrace.Unknown)
                ConfigurationCacheEnvironmentChangeTracker.SystemPropertyLoad::class.java ->
                    ConfigurationCacheEnvironmentChangeTracker.SystemPropertyLoad(key, value, null)
                else -> throw IllegalStateException("$clazz instances is not expected to be stored")
            }
        }

        val removals = readList {
            val key = readString()
            ConfigurationCacheEnvironmentChangeTracker.SystemPropertyRemove(key)
        }

        return ConfigurationCacheEnvironmentChangeTracker.CachedEnvironmentState(cleared, updates, removals)
    }
}
