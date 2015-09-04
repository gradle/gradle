/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.internal.artifacts.ConfigurationResolver
import org.gradle.api.internal.artifacts.DefaultResolverResults
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.specs.Specs
import spock.lang.Specification

class ShortCircuitEmptyConfigurationResolverSpec extends Specification {

    def delegate = Mock(ConfigurationResolver)
    def configuration = Stub(ConfigurationInternal)
    def dependencies = Stub(DependencySet)
    def componentIdentifierFactory = Mock(ComponentIdentifierFactory)
    def results = new DefaultResolverResults()
    def dependencyResolver = new ShortCircuitEmptyConfigurationResolver(delegate, componentIdentifierFactory);

    def "returns empty result when no dependencies"() {
        given:
        dependencies.isEmpty() >> true
        configuration.getAllDependencies() >> dependencies

        when:
        dependencyResolver.resolve(configuration, results)
        dependencyResolver.resolveArtifacts(configuration, results)

        then:
        def resolvedConfig = results.resolvedConfiguration
        !resolvedConfig.hasError()
        resolvedConfig.rethrowFailure();

        resolvedConfig.getFiles(Specs.<Dependency> satisfyAll()).isEmpty()
        resolvedConfig.getFirstLevelModuleDependencies().isEmpty()
        resolvedConfig.getResolvedArtifacts().isEmpty()

        and:
        def result = results.resolutionResult
        result.allComponents.size() == 1
        result.allDependencies.empty

        and:
        0 * delegate._
    }

    def "delegates to backing service when there are one or more dependencies"() {
        given:
        dependencies.isEmpty() >> false
        configuration.getAllDependencies() >> dependencies

        when:
        dependencyResolver.resolve(configuration, results)

        then:
        1 * delegate.resolve(configuration, results)
    }
}
