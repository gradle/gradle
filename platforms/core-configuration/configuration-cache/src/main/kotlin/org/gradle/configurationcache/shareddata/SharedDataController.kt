/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache.shareddata

import org.gradle.configurationcache.ConfigurationCacheStateStore
import org.gradle.configurationcache.StateType
import org.gradle.configurationcache.models.ProjectStateStore
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.shareddata.SharedDataStorage.ProjectProducedSharedData
import org.gradle.util.Path

internal class SharedDataController(store: ConfigurationCacheStateStore) : ProjectStateStore<Path, ProjectProducedSharedData>(store, StateType.SharedData) {
    override fun projectPathForKey(key: Path): Path = key

    override fun write(encoder: Encoder, value: ProjectProducedSharedData) {
        TODO("Not yet implemented")
    }

    override fun read(decoder: Decoder): ProjectProducedSharedData {
        TODO("Not yet implemented")
    }

}
