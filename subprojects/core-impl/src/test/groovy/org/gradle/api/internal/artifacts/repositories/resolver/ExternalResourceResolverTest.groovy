/*
 * Copyright 2012 the original author or authors.
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
import org.apache.ivy.core.module.id.ArtifactRevisionId
import org.gradle.api.artifacts.ArtifactIdentifier
import org.gradle.api.internal.artifacts.DefaultArtifactIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ArtifactResolveException
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository
import spock.lang.Specification

class ExternalResourceResolverTest extends Specification {
    String name = "TestResolver"
    ExternalResourceRepository repository = Mock()
    VersionLister versionLister = Mock()
    LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder = Mock()
    BuildableArtifactResolveResult result = Mock()
    ArtifactIdentifier artifact = new DefaultArtifactIdentifier(DefaultModuleVersionIdentifier.newId("group", "module", "version"), "name", "type", "ext", "classifier")
    MavenResolver.TimestampedModuleSource moduleSource = Mock()
    File downloadedFile = Mock(File)
    ExternalResourceResolver resolver

    def setup() {
        //We use a spy here to avoid dealing with all the overhead ivys basicresolver brings in here.
        resolver = Spy(ExternalResourceResolver, constructorArgs: [name, repository, versionLister, locallyAvailableResourceFinder])
    }

    def reportsNotFoundArtifactResolveResult() {
        given:
        artifactIsMissing()

        when:
        resolver.resolve(artifact, result, moduleSource)

        then:
        1 * result.notFound(artifact)
        0 * result._
    }

    def reportsFailedArtifactResolveResult() {
        given:
        downloadIsFailing(new IOException("DOWNLOAD FAILURE"))

        when:
        resolver.resolve(artifact, result, moduleSource)

        then:
        1 * result.failed(_) >> { ArtifactResolveException exception ->
            assert exception.message == "Could not download artifact 'group:module:version:classifier@ext'"
            assert exception.cause.message == "DOWNLOAD FAILURE"
        }
        0 * result._
    }

    def reportsResolvedArtifactResolveResult() {
        given:
        artifactCanBeResolved()

        when:
        resolver.resolve(artifact, result, moduleSource)

        then:
        1 * result.resolved(_)
        0 * result._
    }

    def reportsResolvedArtifactResolveResultWithSnapshotVersion() {
        given:
        artifactIsTimestampedSnapshotVersion()
        artifactCanBeResolved()

        when:
        resolver.resolve(artifact, result, moduleSource)

        then:
        1 * result.resolved(_)
        0 * result._
    }

    def artifactIsTimestampedSnapshotVersion() {
        _ * moduleSource.timestampedVersion >> "1.0-20100101.120001-1"
    }

    def artifactIsMissing() {
        resolver.download(_) >> null
    }

    def downloadIsFailing(IOException failure) {
        resolver.download(_) >> {
            throw failure
        }
    }

    def artifactCanBeResolved() {
        resolver.download(_) >> downloadedFile
    }
}
