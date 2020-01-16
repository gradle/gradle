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
package org.gradle.api.internal.artifacts.repositories

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ComponentMetadataListerDetails
import org.gradle.api.artifacts.ComponentMetadataSupplier
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails
import org.gradle.api.artifacts.ComponentMetadataVersionLister
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResolver
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.filestore.DefaultArtifactIdentifierFileStore
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.cached.DefaultExternalResourceFileStore
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import javax.inject.Inject

class DefaultIvyArtifactRepositoryTest extends Specification {
    final instantiator = TestUtil.instantiatorFactory().decorateLenient()
    final FileResolver fileResolver = Mock()
    final RepositoryTransportFactory transportFactory = Mock()
    final LocallyAvailableResourceFinder locallyAvailableResourceFinder = Mock()
    final ExternalResourceRepository resourceRepository = Mock()
    final DefaultArtifactIdentifierFileStore artifactIdentifierFileStore = Stub()
    final DefaultExternalResourceFileStore externalResourceFileStore = Stub()
    final AuthenticationContainer authenticationContainer = Stub()
    final ivyContextManager = Mock(IvyContextManager)
    final ImmutableModuleIdentifierFactory moduleIdentifierFactory = Mock()
    final GradleModuleMetadataParser moduleMetadataParser = new GradleModuleMetadataParser(Mock(ImmutableAttributesFactory), moduleIdentifierFactory, Mock(NamedObjectInstantiator))
    final IvyMutableModuleMetadataFactory metadataFactory = DependencyManagementTestUtil.ivyMetadataFactory()
    final DefaultUrlArtifactRepository.Factory urlArtifactRepositoryFactory = new DefaultUrlArtifactRepository.Factory(fileResolver)

    final DefaultIvyArtifactRepository repository = instantiator.newInstance(DefaultIvyArtifactRepository.class, fileResolver, transportFactory, locallyAvailableResourceFinder,
        artifactIdentifierFileStore, externalResourceFileStore, authenticationContainer, ivyContextManager,
        moduleIdentifierFactory, TestUtil.instantiatorFactory(), Mock(FileResourceRepository), moduleMetadataParser,
        metadataFactory, SnapshotTestUtil.valueSnapshotter(), Mock(ObjectFactory), urlArtifactRepositoryFactory,
        TestUtil.checksumService
    )

    def "default values"() {
        expect:
        repository.url == null
        !repository.resolve.dynamicMode
    }

    def "creates a resolver for HTTP patterns"() {
        repository.name = 'name'
        repository.artifactPattern 'http://host/[organisation]/[artifact]-[revision].[ext]'
        repository.artifactPattern 'http://other/[module]/[artifact]-[revision].[ext]'
        repository.ivyPattern 'http://host/[module]/ivy-[revision].xml'

        given:
        fileResolver.resolveUri('http://host/') >> new URI('http://host/')
        fileResolver.resolveUri('http://other/') >> new URI('http://other/')
        standardMockHttpTransport()

        when:
        def resolver = repository.createResolver()

        then:
        with(resolver) {
            it instanceof IvyResolver
            repository == resourceRepository
            name == 'name'
            artifactPatterns == ['http://host/[organisation]/[artifact]-[revision].[ext]', 'http://other/[module]/[artifact]-[revision].[ext]']
            ivyPatterns == ['http://host/[module]/ivy-[revision].xml']
        }
    }

    def "creates a resolver for file patterns"() {
        repository.name = 'name'
        repository.artifactPattern 'repo/[organisation]/[artifact]-[revision].[ext]'
        repository.artifactPattern 'repo/[organisation]/[module]/[artifact]-[revision].[ext]'
        repository.ivyPattern 'repo/[organisation]/[module]/ivy-[revision].xml'
        def file = new File("test")
        def fileUri = file.toURI()

        given:
        fileResolver.resolveUri('repo/') >> fileUri
        standardMockFileTransport()

        when:
        def resolver = repository.createResolver()

        then:
        with(resolver, IvyResolver) {
            repository instanceof ExternalResourceRepository
            name == 'name'
            artifactPatterns == ["${fileUri}/[organisation]/[artifact]-[revision].[ext]", "${fileUri}/[organisation]/[module]/[artifact]-[revision].[ext]"]
            ivyPatterns == ["${fileUri}/[organisation]/[module]/ivy-[revision].xml"]
        }
    }

    def "uses ivy patterns with specified url and default layout"() {
        repository.name = 'name'
        repository.url = 'http://host'
        repository.layout 'ivy'

        given:
        fileResolver.resolveUri('http://host') >> new URI('http://host/')
        standardMockHttpTransport()

        when:
        def resolver = repository.createResolver()

        then:
        with(resolver) {
            it instanceof IvyResolver
            repository instanceof ExternalResourceRepository
            name == 'name'
            artifactPatterns == ['http://host/[organisation]/[module]/[revision]/[type]s/[artifact](.[ext])']
            ivyPatterns == ["http://host/[organisation]/[module]/[revision]/[type]s/[artifact](.[ext])"]
        }
    }

    def "uses gradle patterns with specified url and default layout"() {
        repository.name = 'name'
        repository.url = 'http://host'

        given:
        fileResolver.resolveUri('http://host') >> new URI('http://host/')
        standardMockHttpTransport()

        when:
        def resolver = repository.createResolver()

        then:
        with(resolver) {
            it instanceof IvyResolver
            repository instanceof ExternalResourceRepository
            name == 'name'
            artifactPatterns == ['http://host/[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])']
            ivyPatterns == ["http://host/[organisation]/[module]/[revision]/ivy-[revision].xml"]
        }
    }

    def "uses maven patterns with specified url and maven layout"() {
        repository.name = 'name'
        repository.url = 'http://host'
        repository.layout 'maven'

        given:
        fileResolver.resolveUri('http://host') >> new URI('http://host/')
        standardMockHttpTransport()

        when:
        def resolver = repository.createResolver()

        then:
        with(resolver) {
            it instanceof IvyResolver
            repository instanceof ExternalResourceRepository
            name == 'name'
            artifactPatterns == ['http://host/[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])']
            ivyPatterns == ["http://host/[organisation]/[module]/[revision]/ivy-[revision].xml"]
            m2compatible
        }
    }

    def "uses specified base url with configured pattern layout"() {
        repository.name = 'name'
        repository.url = 'http://host'
        repository.patternLayout {
            artifact '[module]/[revision]/[artifact](.[ext])'
            ivy '[module]/[revision]/ivy.xml'
        }

        given:
        fileResolver.resolveUri('http://host') >> new URI('http://host/')
        standardMockHttpTransport()

        when:
        def resolver = repository.createResolver()

        then:
        with(resolver) {
            it instanceof IvyResolver
            repository instanceof ExternalResourceRepository
            name == 'name'
            artifactPatterns == ['http://host/[module]/[revision]/[artifact](.[ext])']
            ivyPatterns == ["http://host/[module]/[revision]/ivy.xml"]
            !resolver.m2compatible
        }
    }

    def "when requested uses maven patterns with configured pattern layout"() {
        repository.name = 'name'
        repository.url = 'http://host'
        repository.patternLayout {
            artifact '[module]/[revision]/[artifact](.[ext])'
            ivy '[module]/[revision]/ivy.xml'
            m2compatible = true
        }

        given:
        fileResolver.resolveUri('http://host') >> new URI('http://host/')
        standardMockHttpTransport()

        when:
        def resolver = repository.createResolver()

        then:
        with(resolver) {
            it instanceof IvyResolver
            repository instanceof ExternalResourceRepository
            name == 'name'
            artifactPatterns == ['http://host/[module]/[revision]/[artifact](.[ext])']
            ivyPatterns == ["http://host/[module]/[revision]/ivy.xml"]
            m2compatible
        }
    }

    def "combines layout patterns with additionally specified patterns"() {
        repository.name = 'name'
        repository.url = 'http://host/'
        repository.artifactPattern 'http://host/[other]/artifact'
        repository.ivyPattern 'http://host/[other]/ivy'

        given:
        fileResolver.resolveUri('http://host/') >> new URI('http://host/')
        standardMockHttpTransport()

        when:
        def resolver = repository.createResolver()

        then:
        with(resolver) {
            it instanceof IvyResolver
            repository instanceof ExternalResourceRepository
            name == 'name'
            artifactPatterns == ['http://host/[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])', 'http://host/[other]/artifact']
            ivyPatterns == ["http://host/[organisation]/[module]/[revision]/ivy-[revision].xml", 'http://host/[other]/ivy']
        }
    }

    def "uses artifact pattern for ivy files when no ivy pattern provided"() {
        repository.name = 'name'
        repository.url = 'http://host'
        repository.patternLayout {
            artifact '[layoutPattern]'
        }
        repository.artifactPattern 'http://other/[additionalPattern]'
        standardMockHttpTransport()

        given:
        fileResolver.resolveUri('http://host') >> new URI('http://host')
        fileResolver.resolveUri('http://other/') >> new URI('http://other/')

        when:
        def resolver = repository.createResolver()

        then:
        resolver.artifactPatterns == ['http://host/[layoutPattern]', 'http://other/[additionalPattern]']
        resolver.ivyPatterns == resolver.artifactPatterns
    }

    def "fails when no artifact patterns specified"() {
        given:
        transportFactory.createHttpTransport('name', null) >> transport()

        when:
        repository.createResolver()

        then:
        InvalidUserDataException e = thrown()
        e.message == 'You must specify a base url or at least one artifact pattern for an Ivy repository.'
    }

    def "can set a custom metadata rule"() {
        repository.name = 'name'
        repository.url = 'http://host'
        fileResolver.resolveUri('http://host') >> new URI('http://host/')
        standardMockHttpTransport()

        given:
        repository.setMetadataSupplier(CustomMetadataSupplier)

        when:
        def supplier = repository.createResolver().componentMetadataSupplier

        then:
        supplier.rules.configurableRules[0].ruleClass == CustomMetadataSupplier
        supplier.rules.configurableRules[0].ruleParams.isolate() == [] as Object[]
    }

    def "can inject configuration into a custom metadata rule"() {
        repository.name = 'name'
        repository.url = 'http://host'
        fileResolver.resolveUri('http://host') >> new URI('http://host/')
        standardMockHttpTransport()

        given:
        repository.setMetadataSupplier(CustomMetadataSupplierWithParams) { it.params("a", 12, [1, 2, 3]) }

        when:
        def resolver = repository.createResolver()
        def supplier = resolver.getComponentMetadataSupplier()

        then:
        supplier.rules.configurableRules[0].ruleClass == CustomMetadataSupplierWithParams
        supplier.rules.configurableRules[0].ruleParams.isolate() == ["a", 12, [1,2,3]] as Object[]
    }

    def "can set a custom version lister"() {
        repository.name = 'name'
        repository.url = 'http://host'
        fileResolver.resolveUri('http://host') >> new URI('http://host/')
        standardMockHttpTransport()

        given:
        repository.setComponentVersionsLister(CustomVersionLister)

        when:
        def lister = repository.createResolver().providedVersionLister

        then:
        lister.rules.configurableRules[0].ruleClass == CustomVersionLister
        lister.rules.configurableRules[0].ruleParams.isolate() == [] as Object[]
    }

    def "can inject configuration into a custom version lister"() {
        repository.name = 'name'
        repository.url = 'http://host'
        fileResolver.resolveUri('http://host') >> new URI('http://host/')
        standardMockHttpTransport()

        given:
        repository.setComponentVersionsLister(CustomVersionListerWithParams) { it.params("a", 12, [1, 2, 3]) }

        when:
        def lister = repository.createResolver().providedVersionLister

        then:
        lister.rules.configurableRules[0].ruleClass == CustomVersionListerWithParams
        lister.rules.configurableRules[0].ruleParams.isolate() == ["a", 12, [1,2,3]] as Object[]
    }

    private void standardMockFileTransport() {
        transportFactory.createTransport({ it == ['file'] as Set }, 'name', _, _) >> transport()
    }

    private void standardMockHttpTransport() {
        transportFactory.createTransport({ it == ['http'] as Set }, 'name', _, _) >> transport()
    }

    static class CustomVersionLister implements ComponentMetadataVersionLister {

        @Override
        void execute(ComponentMetadataListerDetails details) {
        }
    }

    static class CustomVersionListerWithParams implements ComponentMetadataVersionLister {
        String s
        Number n
        List<Integer> v

        @Inject
        CustomVersionListerWithParams(String s, Number n, List<Integer> v) {
            this.s = s
            this.n = n
            this.v = v
        }

        @Override
        void execute(ComponentMetadataListerDetails details) {
        }
    }

    static class CustomMetadataSupplier implements ComponentMetadataSupplier {
        @Override
        void execute(ComponentMetadataSupplierDetails details) {
        }
    }

    static class CustomMetadataSupplierWithParams implements ComponentMetadataSupplier {
        String s
        Number n
        List<Integer> v

        @Inject
        CustomMetadataSupplierWithParams(String s, Number n, List<Integer> v) {
            this.s = s
            this.n = n
            this.v = v
        }

        @Override
        void execute(ComponentMetadataSupplierDetails details) {
        }
    }

    private RepositoryTransport transport() {
        return Mock(RepositoryTransport) {
            getRepository() >> resourceRepository
        }
    }
}
