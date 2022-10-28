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
package org.gradle.api.internal.artifacts.repositories

import groovy.transform.CompileStatic
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.filestore.DefaultArtifactIdentifierFileStore
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultMavenLocalRepositoryTest extends Specification {
    final FileResolver resolver = Mock()
    final RepositoryTransportFactory transportFactory = Mock()
    final LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder = Mock()
    final ExternalResourceRepository resourceRepository = Mock()
    final DefaultArtifactIdentifierFileStore artifactIdentifierFileStore = Stub()
    final MetaDataParser<MutableMavenModuleResolveMetadata> pomParser = Stub()
    final GradleModuleMetadataParser metadataParser = Stub()
    final AuthenticationContainer authenticationContainer = Stub()
    final FileResourceRepository fileResourceRepository = Mock()
    final MavenMutableModuleMetadataFactory mavenMetadataFactory = DependencyManagementTestUtil.mavenMetadataFactory()
    final DefaultUrlArtifactRepository.Factory urlArtifactRepositoryFactory = new DefaultUrlArtifactRepository.Factory(resolver)

    final DefaultMavenArtifactRepository repository = createRepository()

    @CompileStatic
    DefaultMavenArtifactRepository createRepository() {
        return new DefaultMavenLocalArtifactRepository(
            resolver,
            transportFactory,
            locallyAvailableResourceFinder,
            TestUtil.instantiatorFactory(),
            artifactIdentifierFileStore,
            pomParser,
            metadataParser,
            authenticationContainer,
            fileResourceRepository,
            mavenMetadataFactory,
            SnapshotTestUtil.isolatableFactory(),
            TestUtil.objectFactory(),
            urlArtifactRepositoryFactory,
            TestUtil.checksumService,
            new VersionParser()

        )
    }

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
        repo.root == uri
    }

    private RepositoryTransport transport() {
        return Mock(RepositoryTransport) {
            getRepository() >> resourceRepository
        }
    }
}
