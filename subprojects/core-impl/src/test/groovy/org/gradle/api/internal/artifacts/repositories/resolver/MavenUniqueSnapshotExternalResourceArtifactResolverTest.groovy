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

package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier
import org.gradle.api.internal.artifacts.metadata.DefaultIvyArtifactName
import org.gradle.api.internal.artifacts.metadata.DefaultModuleVersionArtifactMetaData
import org.gradle.api.internal.artifacts.metadata.IvyArtifactName
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource
import spock.lang.Specification

class MavenUniqueSnapshotExternalResourceArtifactResolverTest extends Specification {

    def delegate = Mock(ExternalResourceArtifactResolver)
    def resolver = new MavenUniqueSnapshotExternalResourceArtifactResolver(delegate, "timestamp")

    def "creates timestamped artifact"() {
        when:
        def originalComponentId = DefaultModuleComponentIdentifier.newId("group", "name", "1.0-SNAPSHOT")
        def originalIvyName = Mock(IvyArtifactName)
        def originalArtifact = new DefaultModuleVersionArtifactMetaData(originalComponentId, originalIvyName)
        def artifact = resolver.timestamp(originalArtifact)

        then:
        with (artifact.id.componentIdentifier) {
            group == "group"
            module == "name"
            version == "1.0-SNAPSHOT"
            timestamp == "timestamp"
            timestampedVersion == "1.0-timestamp"
        }
    }

    def "delegates with timestamped artifact"() {
        given:
        def originalComponentId = DefaultModuleComponentIdentifier.newId("group", "name", "1.0-SNAPSHOT")
        def originalIvyName = new DefaultIvyArtifactName("name", "type", "extension")
        def originalArtifact = new DefaultModuleVersionArtifactMetaData(originalComponentId, originalIvyName)
        def artifact = resolver.timestamp(originalArtifact)
        def resource1 = Mock(LocallyAvailableExternalResource)
        def resource2 = Mock(LocallyAvailableExternalResource)

        when:
        1 * delegate.resolveMetaDataArtifact({ it.id == artifact.id }) >> resource1
        1 * delegate.resolveArtifact({ it.id == artifact.id }) >> resource2
        1 * delegate.artifactExists({ it.id == artifact.id }) >> true

        then:
        resolver.resolveMetaDataArtifact(originalArtifact) == resource1
        resolver.resolveArtifact(originalArtifact) == resource2
        resolver.artifactExists(originalArtifact)
    }
}
