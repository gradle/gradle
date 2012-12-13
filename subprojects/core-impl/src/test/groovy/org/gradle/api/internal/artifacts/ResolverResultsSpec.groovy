/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts

import org.gradle.api.artifacts.ResolveException
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.result.ResolutionResult
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 10/16/12
 */
class ResolverResultsSpec extends Specification {

    private resolvedConfiguration = Mock(ResolvedConfiguration)
    private resolutionResult = Mock(ResolutionResult)
    private fatalFailure = Mock(ResolveException)

    def "does not provide ResolutionResult in case of fatal failure"() {
        when:
        def results = new ResolverResults(resolvedConfiguration, fatalFailure)

        then:
        results.resolvedConfiguration

        when:
        results.resolutionResult
        then:
        def ex = thrown(ResolveException)
        ex == fatalFailure
    }

    def "provides resolve results"() {
        when:
        def results = new ResolverResults(resolvedConfiguration, resolutionResult)

        then:
        results.resolvedConfiguration == resolvedConfiguration
        results.resolutionResult == resolutionResult
    }
}