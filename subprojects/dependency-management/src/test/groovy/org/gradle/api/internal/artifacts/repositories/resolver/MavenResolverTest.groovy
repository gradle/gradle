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
import org.gradle.api.artifacts.ComponentMetadataListerDetails
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.repositories.maven.MavenMetadataLoader
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMavenPomMetadataSource
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMetadataArtifactProvider
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataArtifactProvider
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.internal.action.DefaultConfigurableRules
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.component.external.model.ComponentVariant
import org.gradle.internal.component.external.model.FixedComponentArtifacts
import org.gradle.internal.component.external.model.MavenModuleResolveMetadata
import org.gradle.internal.component.external.model.MetadataSourcedComponentArtifacts
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.action.ConfigurableRule
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.util.TestUtil
import spock.lang.Specification

class MavenResolverTest extends Specification {
    def module = Mock(MavenModuleResolveMetadata)
    def result = Mock(BuildableComponentArtifactsResolveResult)
    def resolver = resolver()

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
            assert args[0].artifacts == [artifact]
        }
    }

    def "resolvers are differentiated by useGradleMetadata flag"() {
        given:
        def resolver1 = resolver()
        def resolver2 = resolver(true)

        resolver1.addIvyPattern(new IvyResourcePattern("ivy1"))
        resolver1.addArtifactPattern(new IvyResourcePattern("artifact1"))
        resolver2.addIvyPattern(new IvyResourcePattern("ivy1"))
        resolver2.addArtifactPattern(new IvyResourcePattern("artifact1"))

        expect:
        resolver1.id != resolver2.id
    }

    def "resolvers are differentiated by alwaysProvidesMetadataForModules flag"() {
        given:
        def resolver1 = resolver( false, false)
        def resolver2 = resolver( false, true)

        resolver1.addIvyPattern(new IvyResourcePattern("ivy1"))
        resolver1.addArtifactPattern(new IvyResourcePattern("artifact1"))
        resolver2.addIvyPattern(new IvyResourcePattern("ivy1"))
        resolver2.addArtifactPattern(new IvyResourcePattern("artifact1"))

        expect:
        resolver1.id != resolver2.id
    }

    private MavenResolver resolver(boolean useGradleMetadata = false, boolean alwaysProvidesMetadataForModules = false) {
        MetadataArtifactProvider metadataArtifactProvider = new MavenMetadataArtifactProvider()
        def fileResourceRepository = Stub(FileResourceRepository)
        def moduleIdentifierFactory = Stub(ImmutableModuleIdentifierFactory)
        ImmutableMetadataSources metadataSources = Stub() {
            sources() >> {
                ImmutableList.of(new DefaultMavenPomMetadataSource(
                    metadataArtifactProvider,
                    null,
                    fileResourceRepository,
                    moduleIdentifierFactory, validator
                ))
            }
            appendId(_) >> { args ->
                args[0].putBoolean(useGradleMetadata)
                args[0].putBoolean(alwaysProvidesMetadataForModules)
            }
        }

        def supplier = new InstantiatingAction<ComponentMetadataSupplierDetails>(DefaultConfigurableRules.of(Stub(ConfigurableRule)), TestUtil.instantiatorFactory().inject(), Stub(InstantiatingAction.ExceptionHandler))
        def lister = new InstantiatingAction<ComponentMetadataListerDetails>(DefaultConfigurableRules.of(Stub(ConfigurableRule)), TestUtil.instantiatorFactory().inject(), Stub(InstantiatingAction.ExceptionHandler))

        new MavenResolver("repo", new URI("http://localhost"), Stub(RepositoryTransport), Stub(LocallyAvailableResourceFinder), Stub(FileStore), moduleIdentifierFactory, metadataSources, metadataArtifactProvider, Stub(MavenMetadataLoader), supplier, lister)
    }
}
