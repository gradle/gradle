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
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolveContext
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactSetResolveResult
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData
import spock.lang.Specification

class BaseModuleComponentRepositoryTest extends Specification {
    final delegate = Mock(ModuleComponentRepository)
    final localAccess = Mock(ModuleComponentRepositoryAccess)
    final remoteAccess = Mock(ModuleComponentRepositoryAccess)
    final repository = new BaseModuleComponentRepository(delegate, localAccess, remoteAccess)

    def "delegates id and name methods"() {
        when:
        1 * delegate.id >> "id"
        1 * delegate.name >> "name"

        then:
        repository.id == "id"
        repository.name == "name"
    }

    def "delegates artifact methods"() {

        when:
        final componentMetaData = Mock(ComponentMetaData)
        final context = Mock(ArtifactResolveContext)
        final artifactsResult = Mock(BuildableArtifactSetResolveResult)
        repository.resolveModuleArtifacts(componentMetaData, context, artifactsResult)

        then:
        delegate.resolveModuleArtifacts(componentMetaData, context, artifactsResult)

        when:
        final artifactMetaData = Mock(ComponentArtifactMetaData)
        final moduleSource = Mock(ModuleSource)
        final result = Mock(BuildableArtifactResolveResult)
        repository.resolveArtifact(artifactMetaData, moduleSource, result)

        then:
        delegate.resolveArtifact(artifactMetaData, moduleSource, result)
    }

    def "returns supplied local and remote access"() {
        expect:
        repository.localAccess == localAccess
        repository.remoteAccess == remoteAccess
    }
}
