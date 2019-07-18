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

package org.gradle.instantexecution.serialization.codecs

import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.serialization.MutableReadContext
import org.gradle.instantexecution.serialization.MutableWriteContext
import org.gradle.instantexecution.serialization.beans.makeAccessible
import org.gradle.instantexecution.serialization.readList
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.operations.DefaultBuildOperationListenerManager
import java.lang.reflect.Field


/**
 * Captures a white list of listeners.
 *
 * Only for gradle-profiler --benchmark-config-time for now.
 *
 * Makes assumptions on the manager implementation.
 */
internal
class BuildOperationListenersCodec {

    companion object {

        private
        val classNameWhitelist = setOf(
            "org.gradle.trace.buildops.BuildOperationTrace${'$'}RecordingListener"
        )
    }

    suspend fun MutableWriteContext.writeBuildOperationListeners(manager: BuildOperationListenerManager) {
        writeCollection(manager.listeners) { listener ->
            write(listener)
        }
    }

    suspend fun MutableReadContext.readBuildOperationListeners(): List<BuildOperationListener> =
        readList().uncheckedCast()

    private
    val BuildOperationListenerManager.listeners: List<BuildOperationListener>
        get() = getListenersField().unwrapped().whiteListed()

    private
    fun BuildOperationListenerManager.getListenersField(): List<BuildOperationListener> =
        DefaultBuildOperationListenerManager::class.java
            .getDeclaredField("listeners")
            .also(Field::makeAccessible)
            .get(this)
            .uncheckedCast()

    private
    fun List<BuildOperationListener>.unwrapped() =
        map { listener ->
            listener.javaClass.getDeclaredField("delegate")
                .also(Field::makeAccessible)
                .get(listener) as BuildOperationListener
        }

    private
    fun List<BuildOperationListener>.whiteListed() =
        filter {
            it.javaClass.name in classNameWhitelist
        }
}
