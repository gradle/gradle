/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results

import org.gradle.api.Describable
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.artifacts.UnresolvedDependency
import org.gradle.api.internal.artifacts.result.DefaultMinimalResolutionResult
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Describables
import spock.lang.Specification

import java.util.function.Supplier

/**
 * Tests {@link DefaultVisitedGraphResults}
 */
class DefaultVisitedGraphResultsTest extends Specification {

    Describable name = Describables.of("Configuration", "conf")
    MinimalResolutionResult resolutionResult = new DefaultMinimalResolutionResult(Mock(Supplier), ImmutableAttributes.EMPTY)

    def "hasResolutionFailure returns true if there is a failure"() {
        given:
        def results1 = new DefaultVisitedGraphResults(name, resolutionResult, Collections.emptySet(), null)
        def results2 = new DefaultVisitedGraphResults(name, resolutionResult, Collections.singleton(Mock(UnresolvedDependency)), null)
        def results3 = new DefaultVisitedGraphResults(name, resolutionResult, Collections.emptySet(), Mock(ResolveException))

        expect:
        !results1.hasResolutionFailure()
        results2.hasResolutionFailure()
        results3.hasResolutionFailure()
    }

    def "provides resolution failure aggregating all failures"() {
        given:
        def throwable = Mock(Throwable)
        def resolveEx = Mock(ResolveException)

        def unresolved = Mock(UnresolvedDependency) {
            getProblem() >> throwable
        }

        def results1 = new DefaultVisitedGraphResults(name, resolutionResult, Collections.emptySet(), null)
        def results2 = new DefaultVisitedGraphResults(name, resolutionResult, Collections.singleton(unresolved), null)
        def results3 = new DefaultVisitedGraphResults(name, resolutionResult, Collections.emptySet(), resolveEx)
        def results4 = new DefaultVisitedGraphResults(name, resolutionResult, Collections.singleton(unresolved), resolveEx)

        expect:
        results1.resolutionFailure == null
        results2.resolutionFailure.causes == [throwable]
        results3.resolutionFailure.causes == [resolveEx]
        results4.resolutionFailure.causes == [throwable, resolveEx]
    }

    def "visits all resolution failures"() {
        given:
        def throwable = Mock(Throwable)
        def resolveEx = Mock(ResolveException)

        def unresolved = Mock(UnresolvedDependency) {
            getProblem() >> throwable
        }

        def results1 = new DefaultVisitedGraphResults(name, resolutionResult, Collections.emptySet(), null)
        def results2 = new DefaultVisitedGraphResults(name, resolutionResult, Collections.singleton(unresolved), null)
        def results3 = new DefaultVisitedGraphResults(name, resolutionResult, Collections.emptySet(), resolveEx)
        def results4 = new DefaultVisitedGraphResults(name, resolutionResult, Collections.singleton(unresolved), resolveEx)

        expect:
        visitFailures(results1) == []
        visitFailures(results2) == [throwable]
        visitFailures(results3) == [resolveEx]
        visitFailures(results4) == [throwable, resolveEx]
    }

    def "getters return the values passed to the constructor"() {
        given:
        def resolveEx = Mock(ResolveException)
        def unresolved = Mock(UnresolvedDependency)
        def results = new DefaultVisitedGraphResults(name, resolutionResult, Collections.singleton(unresolved), resolveEx)

        expect:
        results.resolutionResult == resolutionResult
        results.unresolvedDependencies == ([unresolved] as Set)
        results.additionalResolutionFailure == resolveEx
    }

    private List<Throwable> visitFailures(VisitedGraphResults results) {
        List<Throwable> result = []
        results.visitResolutionFailures { result.add(it) }
        return result
    }
}
