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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.DependencyMetadata
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
        def dependency = Mock(DependencyMetadata)
        def result = Mock(BuildableModuleVersionListingResolveResult)
        def componentSelector = new DefaultModuleComponentSelector('a', 'b', '1.0')

        when:
        access.listModuleVersions(dependency, result)

        then:
        1 * repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> false
        0 * repositoryBlacklister._
        1 * delegate.listModuleVersions(dependency, result)
        0 * delegate._
        0 * result._

        when:
        access.listModuleVersions(dependency, result)

        then:
        1 * repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> false
        1 * repositoryBlacklister.blacklistRepository(REPOSITORY_ID, someException) >> true
        1 * delegate.listModuleVersions(dependency, result) >> { throw someException }
        0 * delegate._
        1 * dependency.getSelector() >> componentSelector
        1 * result.failed(_ as ModuleVersionResolveException)

        when:
        access.listModuleVersions(dependency, result)

        then:
        1 * repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> true
        0 * repositoryBlacklister._
        1 * dependency.getSelector() >> componentSelector
        1 * result.failed(_ as ModuleVersionResolveException)
        0 * delegate._
    }

    def "can resolve component meta data"() {
        def moduleComponentIdentifier = Mock(ModuleComponentIdentifier)
        def requestMetaData = Mock(ComponentOverrideMetadata)
        def result = Mock(BuildableModuleComponentMetaDataResolveResult)

        when:
        access.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)

        then:
        1 * repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> false
        0 * repositoryBlacklister._
        1 * delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)
        0 * delegate._
        0 * result._

        when:
        access.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)

        then:
        1 * repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> false
        1 * repositoryBlacklister.blacklistRepository(REPOSITORY_ID, someException) >> true
        1 * delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result) >> { throw someException }
        0 * delegate._
        1 * result.failed(_ as ModuleVersionResolveException)
        1 * moduleComponentIdentifier.getGroup() >> 'a'
        1 * moduleComponentIdentifier.getModule() >> 'b'
        1 * moduleComponentIdentifier.getVersion() >> '1.0'

        when:
        access.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)

        then:
        1 * repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> true
        0 * repositoryBlacklister._
        1 * result.failed(_ as ModuleVersionResolveException)
        0 * delegate._
        1 * moduleComponentIdentifier.getGroup() >> 'a'
        1 * moduleComponentIdentifier.getModule() >> 'b'
        1 * moduleComponentIdentifier.getVersion() >> '1.0'
    }

    def "can resolve artifacts with type"() {
        def component = Mock(ComponentResolveMetadata)
        def componentId = Mock(ComponentIdentifier)
        def artifactType = ArtifactType.MAVEN_POM
        def result = Mock(BuildableArtifactSetResolveResult)

        when:
        access.resolveArtifactsWithType(component, artifactType, result)

        then:
        1 * repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> false
        0 * repositoryBlacklister._
        1 * delegate.resolveArtifactsWithType(component, artifactType, result)
        0 * delegate._
        0 * result._

        when:
        access.resolveArtifactsWithType(component, artifactType, result)

        then:
        1 * repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> false
        1 * repositoryBlacklister.blacklistRepository(REPOSITORY_ID, someException) >> true
        1 * delegate.resolveArtifactsWithType(component, artifactType, result) >> { throw someException }
        0 * delegate._
        1 * result.failed(_ as ArtifactResolveException)
        1 * component.getComponentId() >> componentId

        when:
        access.resolveArtifactsWithType(component, artifactType, result)

        then:
        1 * repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> true
        0 * repositoryBlacklister._
        1 * result.failed(_ as ArtifactResolveException)
        0 * delegate._
        1 * component.getComponentId() >> componentId
    }

    def "can resolve artifacts"() {
        def component = Mock(ComponentResolveMetadata)
        def componentId = Mock(ComponentIdentifier)
        def result = Mock(BuildableComponentArtifactsResolveResult)

        when:
        access.resolveArtifacts(component, result)

        then:
        1 * repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> false
        0 * repositoryBlacklister._
        1 * delegate.resolveArtifacts(component, result)
        0 * delegate._
        0 * result._

        when:
        access.resolveArtifacts(component, result)

        then:
        1 * repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> false
        1 * repositoryBlacklister.blacklistRepository(REPOSITORY_ID, someException) >> true
        1 * delegate.resolveArtifacts(component, result) >> { throw someException }
        0 * delegate._
        1 * result.failed(_ as ArtifactResolveException)
        1 * component.getComponentId() >> componentId

        when:
        access.resolveArtifacts(component, result)

        then:
        1 * repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> true
        0 * repositoryBlacklister._
        1 * result.failed(_ as ArtifactResolveException)
        0 * delegate._
        1 * component.getComponentId() >> componentId
    }

    def "can resolve artifact"() {
        def artifact = Mock(ComponentArtifactMetadata)
        def artifactId = Mock(ComponentArtifactIdentifier)
        def moduleSource = Mock(ModuleSource)
        def result = Mock(BuildableArtifactResolveResult)

        when:
        access.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> false
        0 * repositoryBlacklister._
        1 * delegate.resolveArtifact(artifact, moduleSource, result)
        0 * delegate._
        0 * result._

        when:
        access.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> false
        1 * repositoryBlacklister.blacklistRepository(REPOSITORY_ID, someException) >> true
        1 * delegate.resolveArtifact(artifact, moduleSource, result) >> { throw someException }
        0 * delegate._
        1 * result.failed(_ as ArtifactResolveException)
        1 * artifact.getId() >> artifactId

        when:
        access.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * repositoryBlacklister.isBlacklisted(REPOSITORY_ID) >> true
        0 * repositoryBlacklister._
        1 * result.failed(_ as ArtifactResolveException)
        0 * delegate._
        1 * artifact.getId() >> artifactId
    }
}
