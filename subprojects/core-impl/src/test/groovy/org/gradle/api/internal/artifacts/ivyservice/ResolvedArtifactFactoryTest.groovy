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

import java.util.concurrent.Callable
import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.resolve.ResolveEngine
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact
import spock.lang.Specification

class ResolvedArtifactFactoryTest extends Specification {
    final CacheLockingManager lockingManager = Mock()
    final ResolvedArtifactFactory factory = new ResolvedArtifactFactory(lockingManager)

    def "creates an artifact backed by resolve engine"() {
        Artifact artifact = Mock()
        ResolveEngine resolveEngine = Mock()
        ResolvedDependency resolvedDependency = Mock()

        expect:
        factory.create(resolvedDependency, artifact, resolveEngine) instanceof DefaultResolvedArtifact
    }

    def "creates an artifact backed by resolver"() {
        Artifact artifact = Mock()
        ArtifactToFileResolver resolver = Mock()
        ResolvedDependency resolvedDependency = Mock()
        File file = new File("something.jar")

        when:
        ResolvedArtifact resolvedArtifact = factory.create(resolvedDependency, artifact, resolver)

        then:
        resolvedArtifact instanceof DefaultResolvedArtifact

        when:
        resolvedArtifact.file

        then:
        1 * lockingManager.withCacheLock(!null, !null) >> {String displayName, Callable action ->
            return action.call()
        }
        1 * resolver.resolve(artifact) >> file
        0 * _._
    }
}
