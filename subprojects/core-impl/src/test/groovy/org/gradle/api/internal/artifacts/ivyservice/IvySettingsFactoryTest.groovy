/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.artifacts.ArtifactRepositoryContainer
import spock.lang.Specification

class IvySettingsFactoryTest extends Specification {
    final File cacheDir = new File('user-dir')
    final ArtifactCacheMetaData cacheMetaData = Mock()
    final IvySettingsFactory factory = new IvySettingsFactory(cacheMetaData)

    def "creates and configures an IvySettings instance"() {
        given:
        _ * cacheMetaData.cacheDir >> cacheDir

        when:
        def settings = factory.create()

        then:
        settings.defaultCache == new File(cacheDir, 'ivy')
        settings.defaultCacheArtifactPattern == ArtifactRepositoryContainer.DEFAULT_CACHE_ARTIFACT_PATTERN
    }
}
