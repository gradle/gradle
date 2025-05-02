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


import org.gradle.api.artifacts.UnresolvedDependency
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult
import org.gradle.api.internal.attributes.ImmutableAttributes
import spock.lang.Specification

import java.util.function.Supplier

/**
 * Tests {@link DefaultVisitedGraphResults}
 */
class DefaultVisitedGraphResultsTest extends Specification {

    MinimalResolutionResult resolutionResult = new MinimalResolutionResult(0, Mock(Supplier), ImmutableAttributes.EMPTY)

    def "hasResolutionFailure returns true if there is a failure"() {
        given:
        def results1 = new DefaultVisitedGraphResults(resolutionResult, Collections.emptySet())
        def results2 = new DefaultVisitedGraphResults(resolutionResult, Collections.singleton(Mock(UnresolvedDependency)))

        expect:
        !results1.hasAnyFailure()
        results2.hasAnyFailure()
    }

    def "visits all resolution failures"() {
        given:
        def throwable = Mock(Throwable)
        def unresolved = Mock(UnresolvedDependency) {
            getProblem() >> throwable
        }

        def results1 = new DefaultVisitedGraphResults(resolutionResult, Collections.emptySet())
        def results2 = new DefaultVisitedGraphResults(resolutionResult, Collections.singleton(unresolved))

        expect:
        visitFailures(results1) == []
        visitFailures(results2) == [throwable]
    }

    def "getters return the values passed to the constructor"() {
        given:
        def unresolved = Mock(UnresolvedDependency)
        def results = new DefaultVisitedGraphResults(resolutionResult, Collections.singleton(unresolved))

        expect:
        results.resolutionResult == resolutionResult
        results.unresolvedDependencies == ([unresolved] as Set)
    }

    private List<Throwable> visitFailures(VisitedGraphResults results) {
        List<Throwable> result = []
        results.visitFailures { result.add(it) }
        return result
    }
}
