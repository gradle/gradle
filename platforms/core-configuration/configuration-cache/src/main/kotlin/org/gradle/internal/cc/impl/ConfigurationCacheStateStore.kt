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

package org.gradle.internal.cc.impl

import org.gradle.cache.internal.streams.ValueStore
import java.io.File


internal
interface ConfigurationCacheStateStore {

    data class StateFile(val stateType: StateType, val file: File) {
        val name: String get() = file.name
    }

    data class StateAccessResult<T>(val value: T, val accessedFiles: List<File>)

    fun assignSpoolFile(stateType: StateType): StateFile

    /**
     * Loads some value from zero or more state files.
     */
    fun <T : Any> useForStateLoad(action: ConfigurationCacheRepository.Layout.() -> T): StateAccessResult<T>

    /**
     * Loads some value from a specific state file.
     */
    fun <T : Any> useForStateLoad(stateType: StateType, action: (ConfigurationCacheStateFile) -> T): StateAccessResult<T>

    /**
     * Writes some value to zero or more state files.
     */
    fun <T> useForStore(action: ConfigurationCacheRepository.Layout.() -> T): StateAccessResult<T>

    /**
     * Creates a new [ValueStore] that can be used to load and store multiple values.
     */
    fun <T> createValueStore(
        stateType: StateType,
        writer: ValueStore.Writer<T>,
        reader: ValueStore.Reader<T>
    ): ValueStore<T>
}
