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


import org.gradle.api.artifacts.ResolveException
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver
import org.gradle.api.internal.artifacts.ResolverResults
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.specs.Specs
import spock.lang.Specification

import static org.junit.Assert.fail

public class ErrorHandlingArtifactDependencyResolverTest extends Specification {

    private delegate = Mock(ArtifactDependencyResolver)
    private resolvedConfiguration = Mock(ResolvedConfiguration)
    private resolutionResult = Mock(ResolutionResult)
    private configuration = Mock(ConfigurationInternal.class, name: 'coolConf')
    private resolver = new ErrorHandlingArtifactDependencyResolver(delegate);

    void "delegates to backing service"() {
        given:
        delegate.resolve(configuration) >> new ResolverResults(resolvedConfiguration, resolutionResult)

        when:
        ResolverResults outerResults = resolver.resolve(configuration);
        outerResults.resolvedConfiguration.hasError()
        outerResults.resolvedConfiguration.rethrowFailure()
        outerResults.resolvedConfiguration.getFiles(Specs.satisfyAll())

        then:
        1 * resolvedConfiguration.hasError()
        1 * resolvedConfiguration.rethrowFailure()
        1 * resolvedConfiguration.getFiles(Specs.satisfyAll())
        outerResults.resolutionResult == resolutionResult
    }

    void "wraps operations with the failure"() {
        given:
        def failure = new RuntimeException()
        delegate.resolve(configuration) >> { throw failure }

        when:
        ResolverResults results = resolver.resolve(configuration);

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
        resolvedConfiguration.getResolvedArtifacts() >> { throw failure }

        delegate.resolve(configuration) >> { new ResolverResults(resolvedConfiguration, resolutionResult) }

        when:
        ResolverResults results = resolver.resolve(configuration);

        then:
        failsWith(failure)
                .when { results.resolvedConfiguration.rethrowFailure(); }
                .when { results.resolvedConfiguration.getFiles(Specs.satisfyAll()); }
                .when { results.resolvedConfiguration.getFirstLevelModuleDependencies(); }
                .when { results.resolvedConfiguration.getResolvedArtifacts(); }
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
