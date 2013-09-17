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

import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver
import org.gradle.api.internal.artifacts.ResolverResults
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.specs.Specs
import spock.lang.Specification

import static org.junit.Assert.fail

public class ErrorHandlingArtifactDependencyResolverTest extends Specification {

    private delegate = Mock(ArtifactDependencyResolver)
    private resolvedConfiguration = Mock(ResolvedConfiguration)
    private resolutionResult = Mock(ResolutionResult)
    private configuration = Mock(ConfigurationInternal.class, name: 'coolConf')
    private repositories = [Mock(ResolutionAwareRepository)]
    private resolver = new ErrorHandlingArtifactDependencyResolver(delegate);

    void "delegates to backing service"() {
        given:
        delegate.resolve(configuration, repositories) >> new ResolverResults(resolvedConfiguration, resolutionResult)

        when:
        ResolverResults outerResults = resolver.resolve(configuration, repositories);
        outerResults.resolvedConfiguration.hasError()
        outerResults.resolvedConfiguration.rethrowFailure()
        outerResults.resolvedConfiguration.getFiles(Specs.satisfyAll())
        outerResults.resolutionResult.getRoot()

        then:
        1 * resolvedConfiguration.hasError()
        1 * resolvedConfiguration.rethrowFailure()
        1 * resolvedConfiguration.getFiles(Specs.satisfyAll())
        1 * resolutionResult.getRoot()
    }

    void "wraps operations with the failure"() {
        given:
        def failure = new RuntimeException()
        delegate.resolve(configuration, repositories) >> { throw failure }

        when:
        ResolverResults results = resolver.resolve(configuration, repositories);

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

        delegate.resolve(configuration, repositories) >> { new ResolverResults(resolvedConfiguration, resolutionResult) }

        when:
        def result = resolver.resolve(configuration, repositories).resolvedConfiguration

        then:
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

        delegate.resolve(configuration, repositories) >> { new ResolverResults(resolvedConfiguration, resolutionResult) }

        when:
        def result = resolver.resolve(configuration, repositories).resolvedConfiguration.lenientConfiguration

        then:
        failsWith(failure)
                .when { result.getFiles(Specs.satisfyAll()) }
                .when { result.getFirstLevelModuleDependencies(Specs.satisfyAll()) }
                .when { result.getArtifacts(Specs.satisfyAll()) }
                .when { result.unresolvedModuleDependencies }
    }

    void "wraps exceptions thrown by resolution result"() {
        given:
        def failure = new RuntimeException()

        resolutionResult.root >> { throw failure }

        delegate.resolve(configuration, repositories) >> { new ResolverResults(resolvedConfiguration, resolutionResult) }

        when:
        def result = resolver.resolve(configuration, repositories).resolutionResult

        then:
        failsWith(failure)
                .when { result.root }
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
                assert e.message == "Could not resolve all dependencies for Mock for type 'ConfigurationInternal' named 'coolConf'."
                assert e.cause.is(failure);
            }
            this
        }
    }
}
