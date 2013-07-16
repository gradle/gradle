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
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceIndex
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.internal.filestore.FileStore
import org.gradle.internal.filestore.FileStoreEntry
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DownloadingRepositoryCacheManagerTest extends Specification {
    FileStore<ArtifactRevisionId> fileStore = Mock()
    CachedExternalResourceIndex<String> artifactUrlCachedResolutionIndex = Mock()
    CacheLockingManager lockingManager = Mock()
    TemporaryFileProvider tmpFileProvider = Mock()
    ArtifactRevisionId artifactId = Mock()
    Artifact artifact = Mock()
    ResourceDownloader resourceDownloader = Mock()
    Resource resource = Mock();
    FileStoreEntry fileStoreEntry = Mock()
    DownloadingRepositoryCacheManager downloadingRepositoryCacheManager = new DownloadingRepositoryCacheManager("TestCacheManager", fileStore, artifactUrlCachedResolutionIndex, tmpFileProvider, lockingManager)

    @Rule TestNameTestDirectoryProvider temporaryFolder;

    void "downloads artifact to temporary file and then moves it into the file store"() {
        setup:

        def downloadFile = temporaryFolder.createFile("download")
        def storeFile = temporaryFolder.createFile("store")

        _ * artifact.id >> artifactId
        _ * fileStoreEntry.file >> storeFile;
        _ * tmpFileProvider._ >> downloadFile

        when:
        downloadingRepositoryCacheManager.downloadAndCacheArtifactFile(artifact, resourceDownloader, resource)

        then:
        1 * lockingManager.useCache(_, _) >> {name, action ->
            return action.create()
        }
        1 * resourceDownloader.download(artifact, resource, downloadFile)
        1 * fileStore.move(artifactId, downloadFile) >> {key, action ->
            return fileStoreEntry
        }
    }
}
