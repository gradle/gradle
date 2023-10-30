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

import org.gradle.api.artifacts.LenientConfiguration
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.ConfigurationResolver
import org.gradle.api.internal.artifacts.DefaultResolverResults
import org.gradle.api.internal.artifacts.ResolveExceptionContextualizer
import org.gradle.api.internal.artifacts.ResolverResults
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResult
import org.gradle.api.specs.Specs
import spock.lang.Specification

import static org.junit.Assert.fail

class ErrorHandlingConfigurationResolverTest extends Specification {
    private delegate = Mock(ConfigurationResolver)
    private resolvedConfiguration = Mock(ResolvedConfiguration)
    private visitedGraphResults = Mock(VisitedGraphResults)
    private projectConfigResult = Mock(ResolvedLocalComponentsResult)
    private visitedArtifactSet = Mock(VisitedArtifactSet)
    private context = Mock(ConfigurationInternal)
    private contextualizer =  new ResolveExceptionContextualizer(Mock(DomainObjectContext), Mock(DocumentationRegistry))
    private resolver = new ErrorHandlingConfigurationResolver(delegate, contextualizer);

    def delegateResults = Mock(ResolverResults) {
        getResolvedConfiguration() >> resolvedConfiguration
    }

    def setup() {
        context.displayName >> "resolve context 'foo'"
    }

    void "delegates to backing service to resolve build dependencies"() {
        when:
        def results = resolver.resolveBuildDependencies(context)

        then:
        1 * delegate.resolveBuildDependencies(context) >> delegateResults
        results == delegateResults
    }

    void "delegates to backing service to resolve graph"() {
        when:
        resolver.resolveGraph(context)

        then:
        1 * delegate.resolveGraph(context) >> delegateResults
    }

    void "wraps build dependency resolve failures"() {
        given:
        def failure = new RuntimeException()
        delegate.resolveBuildDependencies(context) >> { throw failure }

        when:
        def results = resolver.resolveBuildDependencies(context)

        then:
        results.resolvedConfiguration.hasError()

        failsWith(failure)
            .when { results.resolvedConfiguration.rethrowFailure(); }
            .when { results.resolvedConfiguration.getFiles(Specs.satisfyAll()); }
            .when { results.resolvedConfiguration.getFirstLevelModuleDependencies(); }
            .when { results.resolvedConfiguration.getResolvedArtifacts(); }
    }

    void "wraps graph resolve failures"() {
        given:
        def failure = new RuntimeException()
        delegate.resolveGraph(context) >> { throw failure }

        when:
        def results = resolver.resolveGraph(context)

        then:
        results.resolvedConfiguration.hasError()

        failsWith(failure)
            .when { results.resolvedConfiguration.rethrowFailure(); }
            .when { results.resolvedConfiguration.getFiles(Specs.satisfyAll()); }
            .when { results.resolvedConfiguration.getFirstLevelModuleDependencies(); }
            .when { results.resolvedConfiguration.getResolvedArtifacts(); }
    }

    void "wraps exceptions thrown by resolved configuration"() {
        given:
        def failure = new RuntimeException()

        resolvedConfiguration.rethrowFailure() >> { throw failure }
        resolvedConfiguration.getFiles(Specs.satisfyAll()) >> { throw failure }
        resolvedConfiguration.getFirstLevelModuleDependencies() >> { throw failure }
        resolvedConfiguration.getFirstLevelModuleDependencies(_) >> { throw failure }
        resolvedConfiguration.getResolvedArtifacts() >> { throw failure }
        resolvedConfiguration.getLenientConfiguration() >> { throw failure }

        delegate.resolveGraph(context) >> delegateResults

        when:
        def results = resolver.resolveGraph(context)

        then:
        def result = results.resolvedConfiguration
        failsWith(failure)
                .when { result.rethrowFailure() }
                .when { result.getFiles(Specs.satisfyAll()) }
                .when { result.firstLevelModuleDependencies }
                .when { result.getFirstLevelModuleDependencies(Specs.satisfyAll()) }
                .when { result.resolvedArtifacts }
                .when { result.lenientConfiguration }
    }

    void "wraps exceptions thrown by resolved lenient configuration"() {
        given:
        def failure = new RuntimeException()
        def lenientConfiguration = Stub(LenientConfiguration)

        resolvedConfiguration.getLenientConfiguration() >> lenientConfiguration
        lenientConfiguration.getFiles(_) >> { throw failure }
        lenientConfiguration.getFirstLevelModuleDependencies(_) >> { throw failure }
        lenientConfiguration.getArtifacts(_) >> { throw failure }
        lenientConfiguration.getUnresolvedModuleDependencies() >> { throw failure }

        delegate.resolveGraph(context) >> DefaultResolverResults.graphResolved(visitedGraphResults, projectConfigResult, resolvedConfiguration, visitedArtifactSet)

        when:
        def results = resolver.resolveGraph(context)

        then:
        def result = results.resolvedConfiguration.lenientConfiguration
        failsWith(failure)
                .when { result.getFiles(Specs.satisfyAll()) }
                .when { result.getFirstLevelModuleDependencies(Specs.satisfyAll()) }
                .when { result.getArtifacts(Specs.satisfyAll()) }
                .when { result.unresolvedModuleDependencies }
    }

    ExceptionFixture failsWith(Throwable failure) {
        new ExceptionFixture(failure: failure)
    }

    class ExceptionFixture {
        Throwable failure
        ExceptionFixture when(Closure cl) {
            try {
                cl();
                fail();
            } catch (ResolveException e) {
                assert e.message == "Could not resolve all dependencies for resolve context 'foo'."
                assert e.cause.is(failure);
            }
            this
        }
    }
}
