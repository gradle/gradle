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

package org.gradle.configurationcache.services

import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.cached.CachedExternalResourceIndex
import org.gradle.internal.resource.cached.ExternalResourceFileStore
import org.gradle.internal.resource.metadata.ExternalResourceMetaDataCompare
import org.gradle.internal.resource.transfer.AccessorBackedExternalResource
import org.gradle.internal.resource.transfer.DownloadAction
import org.gradle.internal.resource.transfer.ExternalResourceConnector
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import java.net.URI


@ServiceScope(Scopes.BuildTree::class)
class RemoteScriptUpToDateChecker(
    private val lockingAccessCoordinator: ArtifactCacheLockingAccessCoordinator,
    private val startParameter: ConfigurationCacheStartParameter,
    private val temporaryFileProvider: TemporaryFileProvider,
    private val externalResourceFileStore: ExternalResourceFileStore,
    private val externalResourceConnector: ExternalResourceConnector,
    private val cachedExternalResourceIndex: CachedExternalResourceIndex<String>
) {

    fun isUpToDate(uri: URI): Boolean =
        if (startParameter.isOffline) {
            true
        } else {
            val externalResourceName = ExternalResourceName(uri)

            val cached = cachedExternalResourceIndex.lookup(externalResourceName.toString())
            val remoteMetaData = externalResourceConnector.getMetaData(externalResourceName, true)

            if (cached != null && ExternalResourceMetaDataCompare.isDefinitelyUnchanged(cached.externalResourceMetaData) { remoteMetaData }) {
                // reset the age of the cached entry to zero
                cachedExternalResourceIndex.store(externalResourceName.toString(), cached.cachedFile, cached.externalResourceMetaData)
                true
            } else {
                val externalResource = AccessorBackedExternalResource(externalResourceName, externalResourceConnector, true)
                val downloadAction = DownloadAction(externalResourceName, temporaryFileProvider, null)
                externalResource.withContentIfPresent(downloadAction)

                lockingAccessCoordinator.useCache {
                    val cachedResource = externalResourceFileStore.move(externalResource.toString(), downloadAction.destination)
                    val fileInFileStore = cachedResource.file
                    cachedExternalResourceIndex.store(externalResourceName.toString(), fileInFileStore, downloadAction.metaData)
                }

                false
            }
        }
}
