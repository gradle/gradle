/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.resolver

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.ModuleMetadataParser
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.internal.component.external.model.ComponentVariant
import org.gradle.internal.component.external.model.FixedComponentArtifacts
import org.gradle.internal.component.external.model.MavenModuleResolveMetadata
import org.gradle.internal.component.external.model.MetadataSourcedComponentArtifacts
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor
import spock.lang.Specification

class MavenResolverTest extends Specification {
    def module = Mock(MavenModuleResolveMetadata)
    def result = Mock(BuildableComponentArtifactsResolveResult)
    def resolver = new MavenResolver("repo", new URI("http://localhost"), Stub(RepositoryTransport), Stub(LocallyAvailableResourceFinder), Stub(FileStore), Stub(MetaDataParser), Stub(ModuleMetadataParser), Stub(ImmutableModuleIdentifierFactory), Stub(CacheAwareExternalResourceAccessor), Stub(FileStore), Stub(FileResourceRepository), false)

    def "has useful string representation"() {
        expect:
        resolver.toString() == "Maven repository 'repo'"
    }

    def "uses variant metadata when present"() {
        given:
        module.variants >> ImmutableList.of(Stub(ComponentVariant))

        when:
        resolver.getLocalAccess().resolveModuleArtifacts(module, result)

        then:
        1 * result.resolved(_) >> { args ->
            assert args[0] instanceof MetadataSourcedComponentArtifacts
        }
    }

    def "resolve to empty when module is relocated"() {
        given:
        module.variants >> ImmutableList.of()
        module.relocated >> true

        when:
        resolver.getLocalAccess().resolveModuleArtifacts(module, result)

        then:
        1 * result.resolved(_) >> { args ->
            assert args[0] instanceof FixedComponentArtifacts
            assert args[0].artifacts.isEmpty()
        }
    }

    def "resolve artifact when module's packaging is jar"() {
        given:
        module.variants >> ImmutableList.of()
        module.knownJarPackaging >> true
        ModuleComponentArtifactMetadata artifact = Mock(ModuleComponentArtifactMetadata)
        module.artifact('jar', 'jar', null) >> artifact

        when:
        resolver.getLocalAccess().resolveModuleArtifacts(module, result)

        then:
        1 * result.resolved(_) >> { args ->
            assert args[0] instanceof FixedComponentArtifacts
            assert args[0].artifacts == [artifact] as Set
        }
    }
}
