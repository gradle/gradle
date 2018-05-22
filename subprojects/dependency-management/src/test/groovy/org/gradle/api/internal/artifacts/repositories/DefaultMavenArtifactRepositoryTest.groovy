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
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.ModuleMetadataParser
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.changedetection.state.isolation.IsolatableFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.filestore.ivy.ArtifactIdentifierFileStore
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.cached.ExternalResourceFileStore
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.util.TestUtil
import spock.lang.Specification

import javax.inject.Inject

class DefaultMavenArtifactRepositoryTest extends Specification {
    final FileResolver resolver = Mock()
    final RepositoryTransportFactory transportFactory = Mock()
    final LocallyAvailableResourceFinder locallyAvailableResourceFinder = Mock()
    final ExternalResourceRepository resourceRepository = Mock()
    final ArtifactIdentifierFileStore artifactIdentifierFileStore = Stub()
    final ExternalResourceFileStore externalResourceFileStore = Stub()
    final MetaDataParser pomParser = Stub()
    final ModuleMetadataParser metadataParser = Stub()
    final AuthenticationContainer authenticationContainer = Stub()
    final ImmutableModuleIdentifierFactory moduleIdentifierFactory = Stub()
    final MavenMutableModuleMetadataFactory mavenMetadataFactory = new MavenMutableModuleMetadataFactory(moduleIdentifierFactory, TestUtil.attributesFactory(), TestUtil.objectInstantiator(), TestUtil.featurePreviews())
    final IsolatableFactory isolatableFactory = TestUtil.valueSnapshotter()

    final DefaultMavenArtifactRepository repository = new DefaultMavenArtifactRepository(
        resolver, transportFactory, locallyAvailableResourceFinder, TestUtil.instantiatorFactory(), artifactIdentifierFileStore, pomParser, metadataParser, authenticationContainer, moduleIdentifierFactory, externalResourceFileStore, Mock(FileResourceRepository), TestUtil.featurePreviews(), mavenMetadataFactory, TestUtil.valueSnapshotter())

    def "creates local repository"() {
        given:
        def file = new File('repo')
        def uri = file.toURI()
        _ * resolver.resolveUri('repo-dir') >> uri
        transportFactory.createTransport('file', 'repo', _) >> transport()

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'

        when:
        def repo = repository.createRealResolver()

        then:
        repo instanceof MavenResolver
        repo.root == uri
    }

    def "creates http repository"() {
        given:
        def uri = new URI("http://localhost:9090/repo")
        _ * resolver.resolveUri('repo-dir') >> uri
        transportFactory.createTransport('http', 'repo', _) >> transport()

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'

        when:
        def repo = repository.createRealResolver()

        then:
        repo instanceof MavenResolver
        repo.root == uri
    }

    def "creates repository with additional artifact URLs"() {
        given:
        def uri = new URI("http://localhost:9090/repo")
        def uri1 = new URI("http://localhost:9090/repo1")
        def uri2 = new URI("http://localhost:9090/repo2")
        _ * resolver.resolveUri('repo-dir') >> uri
        _ * resolver.resolveUri('repo1') >> uri1
        _ * resolver.resolveUri('repo2') >> uri2
        transportFactory.createTransport('http', 'repo', _) >> transport()

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'
        repository.artifactUrls('repo1', 'repo2')

        when:
        def repo = repository.createRealResolver()

        then:
        repo instanceof MavenResolver
        repo.root == uri
        repo.artifactPatterns.size() == 3
        repo.artifactPatterns.any { it.startsWith uri.toString() }
        repo.artifactPatterns.any { it.startsWith uri1.toString() }
        repo.artifactPatterns.any { it.startsWith uri2.toString() }
    }

    def "creates s3 repository"() {
        given:
        def uri = new URI("s3://localhost:9090/repo")
        _ * resolver.resolveUri(_) >> uri
        transportFactory.createTransport(_, 'repo', _) >> transport()

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'

        when:
        def repo = repository.createRealResolver()

        then:
        repo instanceof MavenResolver
        repo.root == uri

    }

    def "fails when no root url specified"() {
        when:
        repository.createRealResolver()

        then:
        InvalidUserDataException e = thrown()
        e.message == 'You must specify a URL for a Maven repository.'
    }

    def "create repository from strongly typed URI"() {
        given:
        def uri = new URI("http://localhost:9090/repo")
        _ * resolver.resolveUri(_) >> uri
        transportFactory.createTransport(_, 'repo', _) >> transport()

        and:
        repository.name = 'repo'
        repository.url = uri

        when:
        def repo = repository.createRealResolver()

        then:
        repo instanceof MavenResolver
        repo.root == uri
    }

    def "can set a custom metadata rule"() {
        repository.name = 'name'
        repository.url = 'http://host'
        resolver.resolveUri('http://host') >> new URI('http://host/')
        transportFactory.createTransport('http', 'name', _) >> transport()

        given:
        repository.setMetadataSupplier(CustomMetadataSupplier)

        when:
        def resolver = repository.createResolver()
        def supplier = resolver.componentMetadataSupplier

        then:
        supplier.rules.configurableRules[0].ruleClass == CustomMetadataSupplier
        supplier.rules.configurableRules[0].ruleParams.isolate() == [] as Object[]
    }

    def "can inject configuration into a custom metadata rule"() {
        repository.name = 'name'
        repository.url = 'http://host'
        resolver.resolveUri('http://host') >> new URI('http://host/')
        transportFactory.createTransport('http', 'name', _) >> transport()

        given:
        repository.setMetadataSupplier(CustomMetadataSupplierWithParams) { it.params("a", 12, [1, 2, 3]) }

        when:
        def resolver = repository.createResolver()
        def supplier = resolver.getComponentMetadataSupplier()

        then:
        supplier.rules.configurableRules[0].ruleClass == CustomMetadataSupplierWithParams
        supplier.rules.configurableRules[0].ruleParams.isolate() == ["a", 12, [1, 2, 3]] as Object[]
    }

    def "can set a custom version lister"() {
        repository.name = 'name'
        repository.url = 'http://host'
        resolver.resolveUri('http://host') >> new URI('http://host/')
        transportFactory.createTransport('http', 'name', _) >> transport()

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
        resolver.resolveUri('http://host') >> new URI('http://host/')
        transportFactory.createTransport('http', 'name', _) >> transport()

        given:
        repository.setComponentVersionsLister(CustomVersionListerWithParams) { it.params("a", 12, [1, 2, 3]) }

        when:
        def lister = repository.createResolver().providedVersionLister

        then:
        lister.rules.configurableRules[0].ruleClass == CustomVersionListerWithParams
        lister.rules.configurableRules[0].ruleParams.isolate() == ["a", 12, [1, 2, 3]] as Object[]
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
