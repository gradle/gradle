/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.query
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.component.Artifact
import org.gradle.api.component.Component
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory
import org.gradle.api.internal.component.ComponentTypeRegistry
import spock.lang.Specification

class DefaultArtifactResolutionQueryTest extends Specification {
    def query = new DefaultArtifactResolutionQuery(Mock(ConfigurationContainerInternal), Mock(RepositoryHandler), Mock(ResolveIvyFactory), Mock(ModuleMetadataProcessor), Mock(CacheLockingManager), Mock(ComponentTypeRegistry))

    def "cannot call withArtifacts multiple times"() {
        given:
        query.withArtifacts(Component, Artifact)

        when:
        query.withArtifacts(Component, Artifact)

        then:
        def e = thrown IllegalStateException
        e.message == "Cannot specify component type multiple times."
    }

    def "cannot call execute without first specifying arguments"() {
        when:
        query.execute()

        then:
        def e = thrown IllegalStateException
        e.message == "Must specify component type and artifacts to query."
    }
}
