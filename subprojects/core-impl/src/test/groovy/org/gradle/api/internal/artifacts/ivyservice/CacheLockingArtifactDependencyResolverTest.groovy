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

import org.gradle.api.internal.artifacts.ArtifactDependencyResolver
import org.gradle.api.internal.artifacts.ResolverResults
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import spock.lang.Specification

class CacheLockingArtifactDependencyResolverTest extends Specification {
    final CacheLockingManager lockingManager = Mock()
    final ArtifactDependencyResolver target = Mock()
    final List<ResolutionAwareRepository> repositories = [Mock(ResolutionAwareRepository)]
    final CacheLockingArtifactDependencyResolver resolver = new CacheLockingArtifactDependencyResolver(lockingManager, target)

    def "resolves while holding a lock on the cache"() {
        ConfigurationInternal configuration = Mock()
        ResolverResults resolverResults = Mock()

        when:
        def results = resolver.resolve(configuration, repositories)

        then:
        results == resolverResults

        and:
        1 * lockingManager.useCache("resolve $configuration", !null) >> {
            it[1].create()
        }
        1 * target.resolve(configuration, repositories) >> resolverResults
    }
}
