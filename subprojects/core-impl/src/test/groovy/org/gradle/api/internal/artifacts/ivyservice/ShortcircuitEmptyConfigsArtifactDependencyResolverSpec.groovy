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
package org.gradle.api.internal.artifacts.ivyservice;


import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver
import org.gradle.api.internal.artifacts.DefaultModule
import org.gradle.api.internal.artifacts.ResolverResults
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.specs.Specs
import spock.lang.Specification

class ShortcircuitEmptyConfigsArtifactDependencyResolverSpec extends Specification {

    final ArtifactDependencyResolver delegate = Mock()
    final ConfigurationInternal configuration = Mock()
    final List<ResolutionAwareRepository> repositories = [Mock(ResolutionAwareRepository)]
    final DependencySet dependencies = Mock()

    final ShortcircuitEmptyConfigsArtifactDependencyResolver dependencyResolver = new ShortcircuitEmptyConfigsArtifactDependencyResolver(delegate);

    def "returns empty config when no dependencies"() {
        given:
        dependencies.isEmpty() >> true
        configuration.getAllDependencies() >> dependencies
        configuration.getModule() >> new DefaultModule("org", "foo", "1.0")
        configuration.getResolutionResultActions() >> []

        when:
        ResolverResults results = dependencyResolver.resolve(configuration, repositories);
        ResolvedConfiguration resolvedConfig = results.resolvedConfiguration

        then:
        !resolvedConfig.hasError()
        resolvedConfig.rethrowFailure();

        resolvedConfig.getFiles(Specs.<Dependency>satisfyAll()).isEmpty()
        resolvedConfig.getFirstLevelModuleDependencies().isEmpty()
        resolvedConfig.getResolvedArtifacts().isEmpty()
    }

    def "delegates to backing service"() {
        given:
        def resultsDummy = Mock(ResolverResults)

        dependencies.isEmpty() >> false
        configuration.getAllDependencies() >> dependencies
        delegate.resolve(configuration, repositories) >> resultsDummy

        when:
        def out = dependencyResolver.resolve(configuration, repositories)

        then:
        out == resultsDummy
    }
}
