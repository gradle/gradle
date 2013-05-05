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

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.id.ArtifactRevisionId
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.report.ArtifactDownloadReport
import org.apache.ivy.core.report.DownloadStatus
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult
import org.gradle.api.internal.artifacts.repositories.cachemanager.EnhancedArtifactDownloadReport
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository
import spock.lang.Specification

class ExternalResourceResolverTest extends Specification {
    String name = "TestResolver"
    ExternalResourceRepository repository = Mock()
    VersionLister versionLister = Mock()
    LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder = Mock()
    BuildableArtifactResolveResult result = Mock()
    Artifact artifact = Mock()
    MavenResolver.TimestampedModuleSource moduleSource = Mock()
    EnhancedArtifactDownloadReport downloadReport = Mock()

    ExternalResourceResolver resolver
    ModuleRevisionId artifactModuleRevisionId = Mock()

    def setup() {
        //We use a spy here to avoid dealing with all the overhead ivys basicresolver brings in here.
        resolver = Spy(ExternalResourceResolver, constructorArgs: [name, repository, versionLister, locallyAvailableResourceFinder])
        resolver.download(_) >> downloadReport
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
        downloadIsFailing()

        when:
        resolver.resolve(artifact, result, moduleSource)

        then:
        1 * result.failed(_) >> { exception ->
            assert exception.message.toString() == "[Could not download artifact 'group:projectA:1.0@jar']"
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
        _ * artifact.getModuleRevisionId() >> artifactModuleRevisionId
        _ * artifactModuleRevisionId.organisation >> "group"
        _ * artifactModuleRevisionId.name >> "projectA"
        _ * artifactModuleRevisionId.revision >> "1.0"
        ModuleId moduleId = Mock()
        _ * moduleId.equals(_) >> true
        _ * artifactModuleRevisionId.moduleId >> moduleId
        _ * artifactModuleRevisionId.qualifiedExtraAttributes >> [:]
    }

    def artifactIsMissing() {
        1 * downloadReport.getDownloadStatus() >> DownloadStatus.FAILED
        1 * downloadReport.getDownloadDetails() >> ArtifactDownloadReport.MISSING_ARTIFACT;
    }

    def downloadIsFailing() {
        1 * downloadReport.getDownloadStatus() >> DownloadStatus.FAILED
        1 * downloadReport.getDownloadDetails() >> "Broken Connection";
        1 * downloadReport.getArtifact() >> artifact
        1 * artifact.getModuleRevisionId() >> {
            ModuleRevisionId moduleRevisionId = Mock()
            1 * moduleRevisionId.organisation >> "group"
            1 * moduleRevisionId.name >> "projectA"
            1 * moduleRevisionId.revision >> "1.0"
            1 * artifact.ext >> "jar"
            moduleRevisionId
        };
    }


    def artifactCanBeResolved() {
        1 * downloadReport.getDownloadStatus() >> DownloadStatus.SUCCESSFUL
        1 * downloadReport.getLocalFile() >> Mock(File);

    }
}
