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
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.repositories.descriptor.IvyRepositoryDescriptor
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultIvyDescriptorMetadataSource
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMetadataArtifactProvider
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataArtifactProvider
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataSource
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.internal.action.ConfigurableRule
import org.gradle.internal.action.DefaultConfigurableRules
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.newId
import static org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult.State.Missing

class IvyResolverTest extends Specification {
    def externalResourceAccessor = Mock(CacheAwareExternalResourceAccessor)
    def transport = Stub(RepositoryTransport)

    static ModuleIdentifier mid(String group, String name) {
        DefaultModuleIdentifier.newId(group, name)
    }

    def "has useful string representation"() {
        expect:
        def resolver = resolver()
        resolver.toString() == "Ivy repository 'repo'"
    }

    def "remote access fails directly for module id #moduleId with layout #layoutPattern"() {
        given:
        def overrideMetadata = DefaultComponentOverrideMetadata.EMPTY
        def result = new DefaultBuildableModuleComponentMetaDataResolveResult()

        when:
        resolver(layoutPattern).getRemoteAccess().resolveComponentMetaData(moduleId, overrideMetadata, result)

        then:
        result.state == Missing
        0 * externalResourceAccessor._
        0 * _

        where:
        moduleId                        | layoutPattern
        newId(mid("", ""), "")          | IvyArtifactRepository.GRADLE_IVY_PATTERN
        newId(mid("", ""), "")          | "[module]"
        newId(mid("group", ""), "1")    | IvyArtifactRepository.GRADLE_IVY_PATTERN
        newId(mid("group", ""), "1")    | "[module]"
        newId(mid("", "name"), "1")     | IvyArtifactRepository.GRADLE_IVY_PATTERN
        newId(mid("", "name"), "1")     | "[organisation]/[module]"
        newId(mid("group", "name"), "") | IvyArtifactRepository.GRADLE_IVY_PATTERN
        newId(mid("group", "name"), "") | "[module]-[revision]"
        newId(mid("", "name"), "")      | "([branch])[organisation]/[module]-[revision]"
        newId(mid("", "name"), "")      | "([organisation])/[module]-[revision]"
        newId(mid("", "name"), "")      | "([branch])[organization]/[module]-[revision]"
        newId(mid("", "name"), "")      | "([organization])/[module]-[revision]"
    }

    def "remote access attempts to access metadata for id #moduleId with layout #layoutPattern"() {
        given:
        def overrideMetadata = DefaultComponentOverrideMetadata.EMPTY
        def result = new DefaultBuildableModuleComponentMetaDataResolveResult()

        when:
        resolver(layoutPattern).getRemoteAccess().resolveComponentMetaData(moduleId, overrideMetadata, result)

        then:
        1 * externalResourceAccessor.getResource(_, _, _, _) >> null
        0 * _

        where:
        moduleId                         | layoutPattern
        newId(mid("group", "name"), "1") | IvyArtifactRepository.GRADLE_IVY_PATTERN
        newId(mid("group", "name"), "1") | "[module]"
        newId(mid("", "name"), "1")      | "[module]"
        newId(mid("", "name"), "1")      | "[module]-[revision]"
        newId(mid("group", "name"), "")  | "[module]"
        newId(mid("group", "name"), "")  | "[organisation]/[module]"
        newId(mid("", "name"), "1")      | "([organisation]/)[module]-[revision]"
        newId(mid("group", "name"), "")  | "[organisation]/[module]-([revision])"
        newId(mid("group", "name"), "")  | "[organization]/[module]"
        newId(mid("", "name"), "1")      | "([organization]/)[module]-[revision]"
        newId(mid("group", "name"), "")  | "[organization]/[module]-([revision])"
    }

    def "correctly sets caching of component metadata rules depending on ivy repository transport"() {
        given:
        transport.isLocal() >> isLocal
        ModuleComponentIdentifier moduleComponentIdentifier = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org", "foo"), "1.0")
        ImmutableMetadataSources metadataSources = mockMetadataSourcesForComponentMetadataRulesCachingTest()
        def resolver = resolver(null, metadataSources)

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

    private IvyResolver resolver(String ivyPattern = null, ImmutableMetadataSources metadataSources = null) {
        transport.resourceAccessor >> externalResourceAccessor

        MetadataArtifactProvider metadataArtifactProvider = new IvyMetadataArtifactProvider()
        def fileResourceRepository = Stub(FileResourceRepository)
        if (metadataSources == null) {
            metadataSources = Stub() {
                sources() >> {
                    ImmutableList.of(new DefaultIvyDescriptorMetadataSource(
                        metadataArtifactProvider,
                        null,
                        fileResourceRepository,
                        TestUtil.checksumService
                    ))
                }
            }
        }

        def supplier = new InstantiatingAction<ComponentMetadataSupplierDetails>(DefaultConfigurableRules.of(Stub(ConfigurableRule)), TestUtil.instantiatorFactory().inject(), Stub(InstantiatingAction.ExceptionHandler))
        def lister = new InstantiatingAction<ComponentMetadataListerDetails>(DefaultConfigurableRules.of(Stub(ConfigurableRule)), TestUtil.instantiatorFactory().inject(), Stub(InstantiatingAction.ExceptionHandler))

        def builder = new IvyRepositoryDescriptor.Builder("repo", new URI("http://localhost"))
        builder.m2Compatible = false
        builder.metadataSources = []
        builder.authenticated = false
        builder.authenticationSchemes = []
        builder.layoutType = "test"
        if (ivyPattern != null) {
            builder.addIvyResource(new URI("http://localhost"), ivyPattern)
        }
        def descriptor = builder.create()

        new IvyResolver(
            descriptor,
            transport,
            Stub(LocallyAvailableResourceFinder),
            false,
            Stub(FileStore),
            supplier,
            lister,
            metadataSources,
            metadataArtifactProvider, Mock(Instantiator),
            TestUtil.checksumService)
    }
}
