/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.ModuleSource
import org.gradle.internal.resolve.ArtifactResolveException
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import spock.lang.Specification
import spock.lang.Subject

class ErrorHandlingModuleComponentRepositoryTest extends Specification {

    private static final String REPOSITORY_ID = 'abc'
    def delegate = Mock(ModuleComponentRepositoryAccess)
    def repositoryBlacklister = Mock(RepositoryBlacklister)
    def someException = new RuntimeException('Something went wrong')
    @Subject def access = new ErrorHandlingModuleComponentRepository.ErrorHandlingModuleComponentRepositoryAccess(delegate, 'abc', repositoryBlacklister)

    def "can list module versions"() {
        given:
        def dependency = Mock(ModuleDependencyMetadata)
        def result = Mock(BuildableModuleVersionListingResolveResult)
        dependency.getSelector() >> DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('a', 'b'), DefaultImmutableVersionConstraint.of('1.0'))

        when: 'repo is not blacklisted'
        repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> false
        access.listModuleVersions(dependency, result)

        then: 'work is delegated'
        1 * delegate.listModuleVersions(dependency, result)

        when: 'exception is thrown in resolution'
        delegate.listModuleVersions(dependency, result) >> { throw someException }
        access.listModuleVersions(dependency, result)

        then: 'resolution fails and repo is blacklisted'
        1 * repositoryBlacklister.blacklistRepository(REPOSITORY_ID, someException)
        1 * result.failed(_ as ModuleVersionResolveException)

        when: 'repo is already blacklisted'
        repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> true
        access.listModuleVersions(dependency, result)

        then: 'resolution fails directly'
        1 * result.failed(_ as ModuleVersionResolveException)
        0 * delegate._
    }

    def "can resolve component meta data"() {
        given:
        def moduleComponentIdentifier = new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('a', 'b'), '1.0')
        def requestMetaData = Mock(ComponentOverrideMetadata)
        def result = Mock(BuildableModuleComponentMetaDataResolveResult)

        when: 'repo is not blacklisted'
        repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> false
        access.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)

        then: 'work is delegated'
        1 * delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)

        when: 'exception is thrown in resolution'
        delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result) >> { throw someException }
        access.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)

        then: 'resolution fails and repo is blacklisted'
        1 * repositoryBlacklister.blacklistRepository(REPOSITORY_ID, someException)
        1 * result.failed(_ as ModuleVersionResolveException)

        when: 'repo is already blacklisted'
        repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> true
        access.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)

        then: 'resolution fails directly'
        1 * result.failed(_ as ModuleVersionResolveException)
        0 * delegate._
    }

    def "can resolve artifacts with type"() {
        given:
        def component = Mock(ComponentResolveMetadata)
        def componentId = Mock(ComponentIdentifier)
        def artifactType = ArtifactType.MAVEN_POM
        def result = Mock(BuildableArtifactSetResolveResult)
        component.getId() >> componentId

        when: 'repo is not blacklisted'
        repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> false
        access.resolveArtifactsWithType(component, artifactType, result)

        then: 'work is delegated'
        1 * delegate.resolveArtifactsWithType(component, artifactType, result)

        when: 'exception is thrown in resolution'
        delegate.resolveArtifactsWithType(component, artifactType, result) >> { throw someException }
        access.resolveArtifactsWithType(component, artifactType, result)

        then: 'resolution fails and repo is blacklisted'
        1 * repositoryBlacklister.blacklistRepository(REPOSITORY_ID, someException)
        1 * result.failed(_ as ArtifactResolveException)

        when: 'repo is already blacklisted'
        repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> true
        access.resolveArtifactsWithType(component, artifactType, result)

        then: 'resolution fails directly'
        1 * result.failed(_ as ArtifactResolveException)
        0 * delegate._
    }

    def "can resolve artifacts"() {
        given:
        def component = Mock(ComponentResolveMetadata)
        def componentId = Mock(ComponentIdentifier)
        def result = Mock(BuildableComponentArtifactsResolveResult)
        component.getId() >> componentId

        when: 'repo is not blacklisted'
        repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> false
        access.resolveArtifacts(component, result)

        then: 'work is delegated'
        1 * delegate.resolveArtifacts(component, result)

        when: 'exception is thrown in resolution'
        delegate.resolveArtifacts(component, result) >> { throw someException }
        access.resolveArtifacts(component, result)

        then: 'resolution fails and repo is blacklisted'
        1 * repositoryBlacklister.blacklistRepository(REPOSITORY_ID, someException)
        1 * result.failed(_ as ArtifactResolveException)

        when: 'repo is already blacklisted'
        repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> true
        access.resolveArtifacts(component, result)

        then: 'resolution fails directly'
        1 * result.failed(_ as ArtifactResolveException)
        0 * delegate._
    }

    def "can resolve artifact"() {
        given:
        def artifact = Mock(ComponentArtifactMetadata)
        def artifactId = Mock(ComponentArtifactIdentifier)
        def moduleSource = Mock(ModuleSource)
        def result = Mock(BuildableArtifactResolveResult)
        artifact.getId() >> artifactId

        when: 'repo is not blacklisted'
        repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> false
        access.resolveArtifact(artifact, moduleSource, result)

        then: 'work is delegated'
        1 * delegate.resolveArtifact(artifact, moduleSource, result)

        when: 'exception is thrown in resolution'
        delegate.resolveArtifact(artifact, moduleSource, result) >> { throw someException }
        access.resolveArtifact(artifact, moduleSource, result)

        then: 'resolution fails and repo is blacklisted'
        1 * repositoryBlacklister.blacklistRepository(REPOSITORY_ID, someException)
        1 * result.failed(_ as ArtifactResolveException)

        when: 'repo is already blacklisted'
        repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> true
        access.resolveArtifact(artifact, moduleSource, result)

        then: 'resolution fails directly'
        1 * result.failed(_ as ArtifactResolveException)
        0 * delegate._
    }
}
