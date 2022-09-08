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

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ComponentMetadataListerDetails
import org.gradle.api.artifacts.ComponentMetadataSupplier
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails
import org.gradle.api.artifacts.ComponentMetadataVersionLister
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.filestore.DefaultArtifactIdentifierFileStore
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.cached.DefaultExternalResourceFileStore
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import javax.inject.Inject

class DefaultMavenArtifactRepositoryTest extends Specification {
    final FileResolver resolver = Mock()
    final RepositoryTransportFactory transportFactory = Mock()
    final LocallyAvailableResourceFinder locallyAvailableResourceFinder = Mock()
    final ExternalResourceRepository resourceRepository = Mock()
    final DefaultArtifactIdentifierFileStore artifactIdentifierFileStore = Stub()
    final DefaultExternalResourceFileStore externalResourceFileStore = Stub()
    final MetaDataParser pomParser = Stub()
    final GradleModuleMetadataParser metadataParser = Stub()
    final AuthenticationContainer authenticationContainer = Stub()
    final MavenMutableModuleMetadataFactory mavenMetadataFactory = DependencyManagementTestUtil.mavenMetadataFactory()
    final DefaultUrlArtifactRepository.Factory urlArtifactRepositoryFactory = new DefaultUrlArtifactRepository.Factory(resolver)
    final ProviderFactory providerFactory = Mock()

    final DefaultMavenArtifactRepository repository = new DefaultMavenArtifactRepository(
            resolver, transportFactory, locallyAvailableResourceFinder, TestUtil.instantiatorFactory(),
            artifactIdentifierFileStore, pomParser, metadataParser, authenticationContainer, externalResourceFileStore,
            Mock(FileResourceRepository), mavenMetadataFactory, SnapshotTestUtil.isolatableFactory(),
            TestUtil.objectFactory(), urlArtifactRepositoryFactory, TestUtil.checksumService, providerFactory, new VersionParser())

    def "creates local repository"() {
        given:
        def file = new File('repo')
        def uri = file.toURI()
        _ * resolver.resolveUri('repo-dir') >> uri
        transportFactory.createTransport('file', 'repo', _, _) >> transport()

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
        def uri = new URI("https://localhost:9090/repo")
        _ * resolver.resolveUri('repo-dir') >> uri
        transportFactory.createTransport('https', 'repo', _, _) >> transport()

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
        def uri = new URI("https://localhost:9090/repo")
        def uri1 = new URI("https://localhost:9090/repo1")
        def uri2 = new URI("https://localhost:9090/repo2")
        _ * resolver.resolveUri('repo-dir') >> uri
        _ * resolver.resolveUri('repo1') >> uri1
        _ * resolver.resolveUri('repo2') >> uri2
        transportFactory.createTransport('https', 'repo', _, _) >> transport()

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
        transportFactory.createTransport(_, 'repo', _, _) >> transport()

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
        def uri = new URI("https://localhost:9090/repo")
        _ * resolver.resolveUri(_) >> uri
        transportFactory.createTransport(_, 'repo', _, _) >> transport()

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
        repository.url = 'https://host'
        resolver.resolveUri('https://host') >> new URI('https://host/')
        transportFactory.createTransport('https', 'name', _, _) >> transport()

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
        repository.url = 'https://host'
        resolver.resolveUri('https://host') >> new URI('https://host/')
        transportFactory.createTransport('https', 'name', _, _) >> transport()

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
        repository.url = 'https://host'
        resolver.resolveUri('https://host') >> new URI('https://host/')
        transportFactory.createTransport('https', 'name', _, _) >> transport()

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
        repository.url = 'https://host'
        resolver.resolveUri('https://host') >> new URI('https://host/')
        transportFactory.createTransport('https', 'name', _, _) >> transport()

        given:
        repository.setComponentVersionsLister(CustomVersionListerWithParams) { it.params("a", 12, [1, 2, 3]) }

        when:
        def lister = repository.createResolver().providedVersionLister

        then:
        lister.rules.configurableRules[0].ruleClass == CustomVersionListerWithParams
        lister.rules.configurableRules[0].ruleParams.isolate() == ["a", 12, [1, 2, 3]] as Object[]
    }

    def "can retrieve metadataSources"() {
        repository.name = 'name'
        repository.url = 'https://host'
        resolver.resolveUri('https://host') >> new URI('https://host/')
        transportFactory.createTransport('https', 'name', _, _) >> transport()

        given:
        repository.metadataSources(new Action<MavenArtifactRepository.MetadataSources>() {
            @Override
            void execute(MavenArtifactRepository.MetadataSources metadataSources) {
                metadataSources.mavenPom()
                metadataSources.artifact()
                metadataSources.gradleMetadata()
                metadataSources.ignoreGradleMetadataRedirection()
            }
        })

        when:
        MavenArtifactRepository.MetadataSources metadataSources = repository.getMetadataSources()

        then:
        metadataSources.isMavenPomEnabled()
        metadataSources.isArtifactEnabled()
        metadataSources.isGradleMetadataEnabled()
        metadataSources.isIgnoreGradleMetadataRedirectionEnabled()
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
