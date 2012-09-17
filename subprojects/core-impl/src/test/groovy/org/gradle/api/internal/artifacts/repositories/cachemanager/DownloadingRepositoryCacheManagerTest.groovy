/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.cachemanager

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.id.ArtifactRevisionId
import org.apache.ivy.plugins.repository.Resource
import org.apache.ivy.plugins.repository.ResourceDownloader
import org.apache.ivy.plugins.resolver.util.ResolvedResource
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceIndex
import org.gradle.api.internal.filestore.FileStore
import org.gradle.api.internal.filestore.FileStoreEntry
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class DownloadingRepositoryCacheManagerTest extends Specification {
    FileStore<ArtifactRevisionId> fileStore = Mock()
    CachedExternalResourceIndex<String> artifactUrlCachedResolutionIndex = Mock()
    DownloadingRepositoryCacheManager downloadingRepositoryCacheManager = new DownloadingRepositoryCacheManager("TestCacheManager", fileStore, artifactUrlCachedResolutionIndex)
    Artifact artifact = Mock()
    ResourceDownloader resourceDownloader = Mock()
    ResolvedResource artifactRef = Mock()
    Resource resource = Mock();
    FileStoreEntry fileStoreEntry = Mock()

    @Rule TemporaryFolder temporaryFolder;

    void "downloadArtifactFile passes download action to filestore"() {
        setup:
        def targetFile = temporaryFolder.createFile("testFile")
        1 * artifactRef.getResource() >> resource
        1 * fileStoreEntry.getFile() >> targetFile;
        when:
        downloadingRepositoryCacheManager.downloadArtifactFile(artifact, resourceDownloader, artifactRef)
        then:
        1 * fileStore.add(_, _) >> {key, action ->
            action.execute(targetFile)
            return fileStoreEntry
        }
        1 * resourceDownloader.download(artifact, resource, targetFile)

    }
}
