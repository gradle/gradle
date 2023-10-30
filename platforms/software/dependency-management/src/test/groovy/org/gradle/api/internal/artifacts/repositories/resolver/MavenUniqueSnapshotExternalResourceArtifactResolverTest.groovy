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

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.resolve.result.ResourceAwareResolveResult
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import spock.lang.Specification

class MavenUniqueSnapshotExternalResourceArtifactResolverTest extends Specification {

    def delegate = Mock(ExternalResourceArtifactResolver)
    def resolver = new MavenUniqueSnapshotExternalResourceArtifactResolver(delegate, new MavenUniqueSnapshotModuleSource("timestamp"))

    def "creates timestamped artifact"() {
        when:
        def originalComponentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "name"), "1.0-SNAPSHOT")
        def originalIvyName = Mock(IvyArtifactName)
        def originalArtifact = new DefaultModuleComponentArtifactMetadata(originalComponentId, originalIvyName)
        def artifact = resolver.timestamp(originalArtifact)

        then:
        with(artifact.id.componentIdentifier) {
            group == "group"
            module == "name"
            version == "1.0-SNAPSHOT"
            timestamp == "timestamp"
            timestampedVersion == "1.0-timestamp"
        }
    }

    def "delegates with timestamped artifact"() {
        given:
        def originalComponentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "name"), "1.0-SNAPSHOT")
        def originalIvyName = new DefaultIvyArtifactName("name", "type", "extension")
        def originalArtifact = new DefaultModuleComponentArtifactMetadata(originalComponentId, originalIvyName)
        def artifact = resolver.timestamp(originalArtifact)
        def result = Mock(ResourceAwareResolveResult)
        def resource2 = Mock(LocallyAvailableExternalResource)

        when:
        1 * delegate.resolveArtifact({ it.id == artifact.id }, result) >> resource2
        1 * delegate.artifactExists({ it.id == artifact.id }, result) >> true

        then:
        resolver.resolveArtifact(originalArtifact, result) == resource2
        resolver.artifactExists(originalArtifact, result)
    }
}
