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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.repositories.descriptor.MavenRepositoryDescriptor
import org.gradle.api.internal.artifacts.repositories.maven.MavenMetadataLoader
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMavenPomMetadataSource
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMetadataArtifactProvider
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataArtifactProvider
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataSource
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.internal.action.ConfigurableRule
import org.gradle.internal.action.DefaultConfigurableRules
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.maven.MavenModuleResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.util.TestUtil
import spock.lang.Specification

class MavenResolverTest extends Specification {
    def module = Mock(MavenModuleResolveMetadata)
    def transport = Stub(RepositoryTransport)

    def "has useful string representation"() {
        def resolver = resolver()

        expect:
        resolver.toString() == "Maven repository 'repo'"
    }

    def "correctly sets caching of component metadata rules depending on maven repository transport"() {
        given:
        transport.isLocal() >> isLocal
        ModuleComponentIdentifier moduleComponentIdentifier = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org", "foo"), "1.0")
        ImmutableMetadataSources metadataSources = mockMetadataSourcesForComponentMetadataRulesCachingTest()
        def resolver = resolver(metadataSources)

        when:
        BuildableModuleComponentMetaDataResolveResult result = new DefaultBuildableModuleComponentMetaDataResolveResult()
        resolver.getRemoteAccess().resolveComponentMetaData(moduleComponentIdentifier, Mock(ComponentOverrideMetadata), result)

        then:
        result.hasResult()
        result.getMetaData().isComponentMetadataRuleCachingEnabled() == isCachingEnabled

        where:
        isLocal | isCachingEnabled
        true    | false
        false   | true
    }

    private ImmutableMetadataSources mockMetadataSourcesForComponentMetadataRulesCachingTest() {
        // By default component metadata rules caching is enabled.
        boolean isCachingEnabled = true
        ModuleComponentResolveMetadata immutableMetadata = Mock(ModuleComponentResolveMetadata) {
            isComponentMetadataRuleCachingEnabled() >> { isCachingEnabled }
        }
        MutableModuleComponentResolveMetadata mutableMetadata = Mock(MutableModuleComponentResolveMetadata) {
            asImmutable() >> immutableMetadata
            setComponentMetadataRuleCachingEnabled(_) >> { arguments -> isCachingEnabled = arguments[0] }
        }

        ImmutableMetadataSources metadataSources = Mock(ImmutableMetadataSources) {
            sources() >> ImmutableList.of(Mock(MetadataSource) {
                create(_, _, _, _, _, _) >> mutableMetadata
            })
        }
        return metadataSources
    }

    private MavenResolver resolver(ImmutableMetadataSources metadataSources = null) {
        MetadataArtifactProvider metadataArtifactProvider = new MavenMetadataArtifactProvider()
        def fileResourceRepository = Stub(FileResourceRepository)
        def moduleIdentifierFactory = Stub(ImmutableModuleIdentifierFactory)
        if (metadataSources == null) {
            metadataSources = Stub() {
                sources() >> {
                    ImmutableList.of(new DefaultMavenPomMetadataSource(
                        metadataArtifactProvider,
                        null,
                        fileResourceRepository,
                        moduleIdentifierFactory, validator
                    ))
                }
            }
        }

        def supplier = new InstantiatingAction<ComponentMetadataSupplierDetails>(DefaultConfigurableRules.of(Stub(ConfigurableRule)), TestUtil.instantiatorFactory().inject(), Stub(InstantiatingAction.ExceptionHandler))
        def lister = new InstantiatingAction<ComponentMetadataListerDetails>(DefaultConfigurableRules.of(Stub(ConfigurableRule)), TestUtil.instantiatorFactory().inject(), Stub(InstantiatingAction.ExceptionHandler))

        def builder = new MavenRepositoryDescriptor.Builder("repo", new URI("http://localhost"))
        builder.metadataSources = []
        builder.authenticated = false
        builder.authenticationSchemes = []
        builder.artifactUrls = []
        def descriptor = builder.create()
        new MavenResolver(descriptor, new URI("http://localhost"), transport, Stub(LocallyAvailableResourceFinder), Stub(FileStore), metadataSources, metadataArtifactProvider, Stub(MavenMetadataLoader), supplier, lister, Mock(Instantiator), TestUtil.checksumService)
    }
}
