/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.plugins.ide.internal.resolver
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.specs.Specs
import spock.lang.Ignore
import spock.lang.Specification

class DefaultIdeDependencyResolverTest extends Specification {

    DefaultIdeDependencyResolver dependencyResolver

    def setup() {
        dependencyResolver = new DefaultIdeDependencyResolver()
    }

    def "getIdeRepoFileDependencies ignores project dependencies"() {
        when:
        def graph = graph {
            projectDependency {
                projectDependency {
                }
            }
        }
        then:
        dependencyResolver.getIdeRepoFileDependencies(graph) == []
    }

    def "getIdeRepoFileDependencies lists direct external dependencies"() {
        when:
        def graph = graph {
            externalDependency{}
        }
        then:
        dependencyResolver.getIdeRepoFileDependencies(graph).size() == 1
    }

    @Ignore
    def "getIdeRepoFileDependencies finds all external dependencies in the graph"() {
        given:
        def graph = graph {
            projectDependency {
                projectDependency {
                    externalDependency{}
                }
            }
        }

        expect:
        dependencyResolver.getIdeRepoFileDependencies(graph).size() == 1
    }


    def "findAllResolvedDependencyResultsAndTheirDependencies filters non matching types"() {
        setup:
        List<ResolvedComponentResult> matches = []
        when:
        dependencyResolver.findAllResolvedDependencyResultsAndTheirDependencies(matches, [], withProjectDependency(), ModuleComponentIdentifier.class)
        then:
        matches.size() == 0

        when:
        dependencyResolver.findAllResolvedDependencyResultsAndTheirDependencies(matches, [], withProjectDependency(), ProjectComponentIdentifier.class)
        then:
        matches.size() == 1
    }


    def graph(Closure closure){
        def call = closure.call()
        Set<? extends DependencyResult> dependencies = call == null ? [] : call

        withDependencies(dependencies)
    }

    def projectDependency(Closure closure){
        def call = closure.call()
        Set<? extends DependencyResult> dependencies = call == null ? [] : call
        withProjectDependency(dependencies)
    }

    def externalDependency(Closure closure){
        def call = closure.call()
        Set<? extends DependencyResult> dependencies = call == null ? [] : call
        withExternalDependency(dependencies)
    }

    Configuration withDependencies(Set<? extends DependencyResult> dependencies = []) {
        Configuration configuration = Mock()

        ResolvedConfiguration resolvedConfiguration = Mock()
        LenientConfiguration lenientConfiguration = Mock()
        ResolvableDependencies incoming = Mock()
        ResolutionResult resolutionResult = Mock()
        ResolvedComponentResult root = Mock()

        _ * configuration.getResolvedConfiguration() >> resolvedConfiguration
        _ * resolvedConfiguration.getLenientConfiguration() >> lenientConfiguration

        def artifacts = dependencies.collect { ResolvedDependencyResult result ->
            ResolvedArtifact resolvedArtifact = Mock()
            ResolvedModuleVersion resolvedModuleVersion = Mock()
            _ * resolvedArtifact.getModuleVersion() >> resolvedModuleVersion
            _ * resolvedModuleVersion.getId() >> result.getSelected().getModuleVersion()
            resolvedArtifact
        }

        _ * lenientConfiguration.getArtifacts(Specs.SATISFIES_ALL) >> artifacts
        _ * incoming.getResolutionResult() >> resolutionResult
        _ * configuration.getIncoming() >> incoming
        _ * root.getDependencies() >> dependencies
        _ * resolutionResult.getRoot() >> root

        configuration
    }

    def withProjectDependency(Set<? extends DependencyResult> transitives = []) {
        withDependency(Mock(ProjectComponentIdentifier), transitives)
    }

    def withExternalDependency(Set<? extends DependencyResult> transitives = []) {
        withDependency(Mock(ModuleComponentIdentifier), transitives)
    }

    private Set<? extends DependencyResult> withDependency(def componentIdentifier, Set<? extends DependencyResult> transitives) {
        Set<? extends DependencyResult> dependencies = new HashSet<>()
        ResolvedDependencyResult result = Mock()
        ModuleVersionIdentifier moduleVersionIdentifier = Mock()
        ResolvedComponentResult resolvedComponentResult = Mock()
        _ * resolvedComponentResult.getDependencies() >> transitives
        _ * resolvedComponentResult.getId() >> componentIdentifier
        _ * resolvedComponentResult.getModuleVersion() >> moduleVersionIdentifier
        _ * result.getSelected() >> resolvedComponentResult
        dependencies.add(result)

        return dependencies
    }

}
