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

import org.gradle.configurationcache.ConfigurationCacheProblemsException
import org.gradle.configurationcache.problems.PropertyTrace
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readList
import org.gradle.configurationcache.serialization.withPropertyTrace
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.configurationcache.services.EnvironmentChangeTracker
import java.io.IOException


internal
object CachedEnvironmentStateCodec : Codec<EnvironmentChangeTracker.CachedEnvironmentState> {

    override suspend fun WriteContext.encode(value: EnvironmentChangeTracker.CachedEnvironmentState) {
        writeBoolean(value.cleared)

        writeCollection(value.updates) { update ->
            val keyString = update.key.toString()
            withPropertyTrace(PropertyTrace.SystemProperty(keyString, update.location ?: PropertyTrace.Unknown)) {
                try {
                    write(update.key)
                    write(update.value)
                } catch (passThrough: IOException) {
                    throw passThrough
                } catch (passThrough: ConfigurationCacheProblemsException) {
                    throw passThrough
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

    override suspend fun ReadContext.decode(): EnvironmentChangeTracker.CachedEnvironmentState {
        val cleared = readBoolean()
        val updates = readList {
            val key = read() as Any
            val value = read()
            EnvironmentChangeTracker.SystemPropertySet(key, value, null)
        }

        val removals = readList {
            val key = readString()
            EnvironmentChangeTracker.SystemPropertyRemove(key)
        }

        return EnvironmentChangeTracker.CachedEnvironmentState(cleared, updates, removals)
    }
}
