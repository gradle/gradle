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

package org.gradle.api.internal.externalresource.ivy

import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager
import org.gradle.internal.TimeProvider
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData

class ArtifactAtRepositoryCachedArtifactIndexTest extends Specification {

    CacheLockingManager cacheLockingManager = Mock()
    TimeProvider timeProvider = Mock()
    ArtifactAtRepositoryKey key = Mock()
    ExternalResourceMetaData metaData = Mock()

    @Rule TemporaryFolder folder = new TemporaryFolder();
    ArtifactAtRepositoryCachedArtifactIndex index = new ArtifactAtRepositoryCachedArtifactIndex(folder.createFile("cacheFile"), timeProvider, cacheLockingManager)


    def "storing null artifactFile not supported"() {
        when:
        index.store(key, null,  0)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "artifactFile cannot be null"
    }

    def "artifact key must be provided"() {
        when:
        index.store(null, Mock(File),  0)
        then:
        def e = thrown(IllegalArgumentException)
        e.message == "key cannot be null"
    }
}
