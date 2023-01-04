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

import com.google.common.collect.Maps
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.ImmutableModuleSources
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult
import org.gradle.internal.resources.ProjectLeaseRegistry
import org.gradle.internal.service.ServiceRegistry
import spock.lang.Specification

class RepositoryChainArtifactResolverTest extends Specification {
    final artifactFile = new File("dontcare")
    final moduleVersionId = DefaultModuleVersionIdentifier.newId("group", "name", "1.0")
    final artifact = new DefaultModuleComponentArtifactMetadata(DefaultModuleComponentIdentifier.newId(moduleVersionId), new DefaultIvyArtifactName("name", "jar", "jar"))
    final result = new DefaultBuildableArtifactResolveResult()
    final calculatedValueContainerFactory = new CalculatedValueContainerFactory(Mock(ProjectLeaseRegistry), Mock(ServiceRegistry))

    final cache = Maps.newHashMap()

    final repo1 = Stub(ModuleComponentRepository) {
        getId() >> "repo1"
    }

    final localAccess2 = Mock(ModuleComponentRepositoryAccess)
    final remoteAccess2 = Mock(ModuleComponentRepositoryAccess)
    final repo2 = Stub(ModuleComponentRepository) {
        getLocalAccess() >> localAccess2
        getRemoteAccess() >> remoteAccess2
        getArtifactCache() >> cache
        getId() >> "repo2"
    }
    final repo2Source = new RepositoryChainModuleSource(repo2)

    final moduleSources = ImmutableModuleSources.of(repo2Source)

    final resolver = new RepositoryChainArtifactResolver(calculatedValueContainerFactory)

    def setup() {
        resolver.add(repo1)
        resolver.add(repo2)
    }

    def "locates artifact with local access in repository defined by module source"() {
        when:
        resolver.resolveArtifact(moduleVersionId, artifact, moduleSources, result)
        then:
        result.hasResult()
        cache.size() == 1
        cache.values().contains(result.result)
        and:
        0 * _._

        when:
        result.result.file
        then:
        1 * localAccess2.resolveArtifact(artifact, moduleSources, _) >> {
            it[2].resolved(artifactFile)
        }
        0 * _._
        and:
        result.result.file == artifactFile

        when:
        resolver.resolveArtifact(moduleVersionId, artifact, moduleSources, result)
        then:
        result.hasResult()
        result.result.file == artifactFile
        and:
        0 * _._
    }

    def "locates artifact with remote access in repository defined by module source"() {
        when:
        resolver.resolveArtifact(moduleVersionId, artifact, moduleSources, result)
        then:
        result.hasResult()
        cache.size() == 1
        cache.values().contains(result.result)
        and:
        0 * _._

        when:
        result.result.file
        then:
        // not found locally
        1 * localAccess2.resolveArtifact(artifact, moduleSources, _)
        // found remotely
        1 * remoteAccess2.resolveArtifact(artifact, moduleSources, _) >> {
            it[2].resolved(artifactFile)
        }
        0 * _._
        and:
        result.result.file == artifactFile

        when:
        resolver.resolveArtifact(moduleVersionId, artifact, moduleSources, result)
        then:
        result.hasResult()
        result.result.file == artifactFile
        and:
        0 * _._
    }
}
