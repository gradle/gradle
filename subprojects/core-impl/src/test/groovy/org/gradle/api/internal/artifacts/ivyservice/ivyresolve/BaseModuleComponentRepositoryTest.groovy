/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData
import spock.lang.Specification

class BaseModuleComponentRepositoryTest extends Specification {
    final delegate = Mock(ModuleComponentRepository)
    final localAccess = Mock(ModuleComponentRepositoryAccess)
    final remoteAccess = Mock(ModuleComponentRepositoryAccess)

    def "delegates id and name methods"() {
        when:
        final repository = new BaseModuleComponentRepository(delegate, localAccess, remoteAccess)
        1 * delegate.id >> "id"
        1 * delegate.name >> "name"

        then:
        repository.id == "id"
        repository.name == "name"
    }

    def "delegates artifact methods"() {
        when:
        final artifactMetaData = Mock(ComponentArtifactMetaData)
        final moduleSource = Mock(ModuleSource)
        final result = Mock(BuildableArtifactResolveResult)
        final repository = new BaseModuleComponentRepository(delegate)
        repository.resolveArtifact(artifactMetaData, moduleSource, result)

        then:
        delegate.resolveArtifact(artifactMetaData, moduleSource, result)
    }

    def "delegates access methods"() {
        when:
        final repository = new BaseModuleComponentRepository(delegate)

        then:
        repository.localAccess == localAccess
        repository.remoteAccess == remoteAccess

        and:
        1 * delegate.localAccess >> localAccess
        1 * delegate.remoteAccess >> remoteAccess
    }

    def "returns supplied local and remote access"() {
        when:
        final repository = new BaseModuleComponentRepository(delegate, localAccess, remoteAccess)

        then:
        repository.localAccess == localAccess
        repository.remoteAccess == remoteAccess

        and:
        0 * delegate._
    }
}
