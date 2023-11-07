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

package org.gradle.configurationcache.shareddata

import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.provider.Provider
import org.gradle.configurationcache.BuildTreeConfigurationCache
import org.gradle.internal.shareddata.SharedDataStorage
import org.gradle.util.Path


internal
class ConfigurationCacheAwareSharedDataStorage(
    private val projectStateRegistry: ProjectStateRegistry,
    private val delegate: SharedDataStorage,
    private val cache: BuildTreeConfigurationCache,
) : SharedDataStorage {
    override fun discardAll() {
        delegate.discardAll()
    }

    override fun <T : Any?> put(sourceProjectIdentityPath: Path, type: Class<T>, identifier: String?, dataProvider: Provider<T>) {
        delegate.put(sourceProjectIdentityPath, type, identifier, dataProvider)
    }

    override fun getProjectDataResolver(consumerProjectIdentityPath: Path, sourceProjectIdentitityPath: Path): SharedDataStorage.ProjectProducedSharedData =
        if (consumerProjectIdentityPath.equals(sourceProjectIdentitityPath)) {
            // Don't trigger serialization if it's the producer project querying its own data.
            // The producer project might still be in configuration phase, so it can later attempt to mutate its shared data.
            delegate.getProjectDataResolver(consumerProjectIdentityPath, sourceProjectIdentitityPath)
        } else {
            cache.loadOrCreateProjectSharedData(sourceProjectIdentitityPath) {
                // If not found in the cache, ensure the project gets configured and registers its shared data.
                // The project should get its own fingerprint tracked while it is being configured.
                projectStateRegistry.stateFor(sourceProjectIdentitityPath).ensureConfigured()

                delegate.getProjectDataResolver(consumerProjectIdentityPath, sourceProjectIdentitityPath)
            }
        }
}
