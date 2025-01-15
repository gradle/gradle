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

import org.apache.http.conn.HttpHostConnectException
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata
import org.gradle.internal.component.model.ImmutableModuleSources
import org.gradle.internal.component.model.ModuleSource
import org.gradle.internal.resolve.ArtifactResolveException
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import org.gradle.internal.resource.transport.http.HttpErrorStatusCodeException
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ErrorHandlingModuleComponentRepositoryTest extends Specification {

    private static final String REPOSITORY_ID = 'abc'
    def delegate = Mock(ModuleComponentRepositoryAccess)
    def repositoryBlacklister = Mock(RepositoryDisabler)

    @Shared
    def runtimeError = new RuntimeException('Something went wrong')
    @Shared
    def socketError = new SocketException()
    @Shared
    def connectTimeout = new SocketTimeoutException()
    @Shared
    def cannotConnect = new HttpHostConnectException(null, null)
    @Shared
    def missing = status(404)
    @Shared
    def clientTimeout = status(408)
    @Shared
    def tooManyRequests = status(429)
    @Shared
    def forbidden = status(403)
    @Shared
    def unauthorized = status(401)

    @Subject
    ErrorHandlingModuleComponentRepository.ErrorHandlingModuleComponentRepositoryAccess access

    private ErrorHandlingModuleComponentRepository.ErrorHandlingModuleComponentRepositoryAccess createAccess(int maxRetries = 1, int backoff = 0) {
        new ErrorHandlingModuleComponentRepository.ErrorHandlingModuleComponentRepositoryAccess(delegate, 'abc', repositoryBlacklister, maxRetries, backoff, 'abc')
    }

    private static HttpErrorStatusCodeException status(int statusCode) {
        new HttpErrorStatusCodeException("GET", "DUMMY", statusCode, "test") {
            @Override
            String toString() {
                "status $statusCode"
            }
        }
    }

    @Unroll("can list module versions (max retries = #maxRetries, exception=#exception)")
    def "can list module versions"() {
        access = createAccess(maxRetries)

        given:
        def selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('a', 'b'), DefaultImmutableVersionConstraint.of('1.0'))
        def result = Mock(BuildableModuleVersionListingResolveResult)

        when: 'repo is not disabled'
        repositoryBlacklister.isDisabled(REPOSITORY_ID) >> false
        access.listModuleVersions(selector, DefaultComponentOverrideMetadata.EMPTY, result)

        then: 'work is delegated'
        1 * delegate.listModuleVersions(selector, _, result)

        when: 'exception is thrown in resolution'
        repositoryBlacklister.getDisabledReason(REPOSITORY_ID) >> Optional.of(exception)
        effectiveRetries * delegate.listModuleVersions(selector, _, result) >> { throw exception }
        access.listModuleVersions(selector, DefaultComponentOverrideMetadata.EMPTY, result)

        then: 'resolution fails and repo is disabled'
        1 * repositoryBlacklister.tryDisableRepository(REPOSITORY_ID, { hasCause(it, exception) })
        1 * result.failed(_ as ModuleVersionResolveException)

        when: 'repo is already disabled'
        repositoryBlacklister.isDisabled(REPOSITORY_ID) >> true
        repositoryBlacklister.getDisabledReason(REPOSITORY_ID) >> Optional.of(exception)
        access.listModuleVersions(selector, DefaultComponentOverrideMetadata.EMPTY, result)

        then: 'resolution fails directly'
        1 * result.failed(_ as ModuleVersionResolveException)
        0 * delegate._

        where:
        [maxRetries, exception, effectiveRetries] << retryCombinations()
    }

    @Unroll("can resolve component meta data (max retries = #maxRetries, exception=#exception)")
    def "can resolve component meta data"() {
        access = createAccess(maxRetries)

        given:
        def moduleComponentIdentifier = new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('a', 'b'), '1.0')
        def requestMetaData = Mock(ComponentOverrideMetadata)
        def result = Mock(BuildableModuleComponentMetaDataResolveResult)

        when: 'repo is not disabled'
        repositoryBlacklister.isDisabled(REPOSITORY_ID) >> false
        access.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)

        then: 'work is delegated'
        1 * delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)

        when: 'exception is thrown in resolution'
        repositoryBlacklister.getDisabledReason(REPOSITORY_ID) >> Optional.of(exception)
        effectiveRetries * delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result) >> { throw exception }
        access.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)

        then: 'resolution fails and repo is disabled'
        1 * repositoryBlacklister.tryDisableRepository(REPOSITORY_ID, { hasCause(it, exception) })
        1 * result.failed(_ as ModuleVersionResolveException)

        when: 'repo is already disabled'
        repositoryBlacklister.isDisabled(REPOSITORY_ID) >> true
        repositoryBlacklister.getDisabledReason(REPOSITORY_ID) >> Optional.of(exception)
        access.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)

        then: 'resolution fails directly'
        1 * result.failed(_ as ModuleVersionResolveException)
        0 * delegate._

        where:
        [maxRetries, exception, effectiveRetries] << retryCombinations()
    }

    @Unroll("can resolve artifacts with type (max retries = #maxRetries, exception=#exception)")
    def "can resolve artifacts with type"() {
        access = createAccess(maxRetries)

        given:
        def component = Mock(ComponentArtifactResolveMetadata)
        def componentId = Mock(ComponentIdentifier)
        def artifactType = ArtifactType.MAVEN_POM
        def result = Mock(BuildableArtifactSetResolveResult)
        component.getId() >> componentId

        when: 'repo is not disabled'
        repositoryBlacklister.isDisabled(REPOSITORY_ID) >> false
        access.resolveArtifactsWithType(component, artifactType, result)

        then: 'work is delegated'
        1 * delegate.resolveArtifactsWithType(component, artifactType, result)

        when: 'exception is thrown in resolution'
        repositoryBlacklister.getDisabledReason(REPOSITORY_ID) >> Optional.of(exception)
        effectiveRetries * delegate.resolveArtifactsWithType(component, artifactType, result) >> { throw exception }
        access.resolveArtifactsWithType(component, artifactType, result)

        then: 'resolution fails and repo is disabled'
        1 * repositoryBlacklister.tryDisableRepository(REPOSITORY_ID, { hasCause(it, exception) })
        1 * result.failed(_ as ArtifactResolveException)

        when: 'repo is already disabled'
        repositoryBlacklister.isDisabled(REPOSITORY_ID) >> true
        repositoryBlacklister.getDisabledReason(REPOSITORY_ID) >> Optional.of(exception)
        access.resolveArtifactsWithType(component, artifactType, result)

        then: 'resolution fails directly'
        1 * result.failed(_ as ArtifactResolveException)
        0 * delegate._

        where:
        [maxRetries, exception, effectiveRetries] << retryCombinations()
    }

    @Unroll("can resolve artifact (max retries = #maxRetries, exception=#exception)")
    def "can resolve artifact"() {
        access = createAccess(maxRetries)

        given:
        def artifact = Mock(ComponentArtifactMetadata)
        def artifactId = Mock(ComponentArtifactIdentifier)
        def moduleSources = ImmutableModuleSources.of(Mock(ModuleSource))
        def result = Mock(BuildableArtifactFileResolveResult)
        artifact.getId() >> artifactId

        when: 'repo is not disabled'
        repositoryBlacklister.isDisabled(REPOSITORY_ID) >> false
        access.resolveArtifact(artifact, moduleSources, result)
        repositoryBlacklister.getDisabledReason(REPOSITORY_ID) >> Optional.of(exception)

        then: 'work is delegated'
        1 * delegate.resolveArtifact(artifact, moduleSources, result)

        when: 'exception is thrown in resolution'
        repositoryBlacklister.getDisabledReason(REPOSITORY_ID) >> Optional.of(exception)
        effectiveRetries * delegate.resolveArtifact(artifact, moduleSources, result) >> { throw exception }
        access.resolveArtifact(artifact, moduleSources, result)

        then: 'resolution fails and repo is disabled'
        1 * repositoryBlacklister.tryDisableRepository(REPOSITORY_ID, { hasCause(it, exception) })
        1 * result.failed(_ as ArtifactResolveException)

        when: 'repo is already disabled'
        repositoryBlacklister.isDisabled(REPOSITORY_ID) >> true
        repositoryBlacklister.getDisabledReason(REPOSITORY_ID) >> Optional.of(exception)
        access.resolveArtifact(artifact, moduleSources, result)

        then: 'resolution fails directly'
        1 * result.failed(_ as ArtifactResolveException)
        0 * delegate._

        where:
        [maxRetries, exception, effectiveRetries] << retryCombinations()
    }

    @Unroll
    def "blocks repo immediately when receives #desc"() {
        access = createAccess(1)

        given:
        def artifact = Mock(ComponentArtifactMetadata)
        def artifactId = Mock(ComponentArtifactIdentifier)
        def moduleSources = ImmutableModuleSources.of(Mock(ModuleSource))
        def result = Mock(BuildableArtifactFileResolveResult)
        artifact.getId() >> artifactId
        repositoryBlacklister.getDisabledReason(REPOSITORY_ID) >> Optional.of(exception)
        delegate.resolveArtifact(artifact, moduleSources, result) >> { throw exception }
        repositoryBlacklister.isDisabled(REPOSITORY_ID) >> false

        when: 'repo is not disabled'
        access.resolveArtifact(artifact, moduleSources, result)

        then: 'resolution fails and repo is disabled'
        1 * repositoryBlacklister.tryDisableRepository(REPOSITORY_ID, { hasCause(it, exception) })
        1 * result.failed(_ as ArtifactResolveException)

        when: 'repo is already disabled'
        repositoryBlacklister.isDisabled(REPOSITORY_ID) >> true
        repositoryBlacklister.getDisabledReason(REPOSITORY_ID) >> Optional.of(exception)
        access.resolveArtifact(artifact, moduleSources, result)

        then: 'resolution fails directly'
        1 * result.failed(_ as ArtifactResolveException)
        0 * delegate._

        where:
        desc | exception
        "unauthorized (401)" | unauthorized
        "forbidden (403)" | forbidden
    }

    List<List<?>> retryCombinations() {
        def retries = []
        (1..3).each { ret ->
            // no retries on runtime errors, missing resources, authentication errors
            retries << [ret, runtimeError, 1]
            retries << [ret, missing, 1]
            retries << [ret, forbidden, 1]
            retries << [ret, unauthorized, 1]
            // retries on connect timeouts, too many requests, client timeouts
            retries << [ret, connectTimeout, ret]
            retries << [ret, cannotConnect, ret]
            retries << [ret, tooManyRequests, ret]
            retries << [ret, clientTimeout, ret]
            retries << [ret, socketError, ret]
            // retries on server errors
            if (ret < 3) {
                // testing 1 and 2 is good enough coverage
                (500..<600).each { code ->
                    retries << [ret, status(code), ret]
                }
                // no retries on arbitrary codes
                (100..<200).each { code ->
                    retries << [ret, status(code), 1]
                }
            }
        }
        retries
    }

    static boolean hasCause(Throwable actual, Throwable cause) {
        if (actual == cause) {
            return true
        }
        if (actual.cause && actual.cause != actual) {
            return hasCause(actual.cause, cause)
        }
        return false
    }
}
