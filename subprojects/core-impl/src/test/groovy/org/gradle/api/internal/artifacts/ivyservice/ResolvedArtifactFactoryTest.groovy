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
package org.gradle.api.internal.artifacts.ivyservice

import org.apache.ivy.core.module.descriptor.Artifact
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.internal.Factory
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact
import spock.lang.Specification

class ResolvedArtifactFactoryTest extends Specification {
    final CacheLockingManager lockingManager = Mock()
    final ResolvedArtifactFactory factory = new ResolvedArtifactFactory(lockingManager)

    def "creates an artifact backed by module resolve result"() {
        Artifact artifact = Mock()
        ArtifactResolver artifactResolver = Mock()
        ResolvedDependency resolvedDependency = Mock()
        File file = new File("something.jar")

        given:
        artifact.qualifiedExtraAttributes >> [:]

        when:
        ResolvedArtifact resolvedArtifact = factory.create(resolvedDependency, artifact, artifactResolver)

        then:
        resolvedArtifact instanceof DefaultResolvedArtifact

        when:
        resolvedArtifact.file

        then:
        1 * lockingManager.useCache(!null, !null) >> {String displayName, Factory<?> action ->
            return action.create()
        }
        1 * artifactResolver.resolve(artifact, _) >> { args -> args[1].resolved(file, null) }
        0 * _._
    }
}
