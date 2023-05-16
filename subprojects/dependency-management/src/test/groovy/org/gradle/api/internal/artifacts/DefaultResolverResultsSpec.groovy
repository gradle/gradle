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
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolveState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResult
import spock.lang.Specification

class DefaultResolverResultsSpec extends Specification {
    private resolvedConfiguration = Mock(ResolvedConfiguration)
    private resolutionResult = Mock(ResolutionResult)
    private projectConfigurationResult = Mock(ResolvedLocalComponentsResult)
    private visitedArtifactsSet = Mock(VisitedArtifactSet)
    private artifactResolveState = Mock(ArtifactResolveState)
    private fatalFailure = Mock(ResolveException)

    def "does not provide result in case of fatal failure"() {
        when:
        def results = DefaultResolverResults.failed(fatalFailure, fatalFailure)

        and:
        results.resolutionResult

        then:
        def ex = thrown(ResolveException)
        ex == fatalFailure

        when:
        results.resolvedLocalComponents

        then:
        def ex2 = thrown(ResolveException)
        ex2 == fatalFailure

        when:
        results.visitedArtifacts

        then:
        def ex3 = thrown(ResolveException)
        ex3 == fatalFailure
    }

    def "provides build dependencies results"() {
        when:
        def results = DefaultResolverResults.buildDependenciesResolved(resolutionResult, projectConfigurationResult, visitedArtifactsSet)

        then:
        results.resolutionResult == resolutionResult
        results.resolvedLocalComponents == projectConfigurationResult
        results.visitedArtifacts == visitedArtifactsSet
    }

    def "provides resolve results"() {
        when:
        def results = DefaultResolverResults.graphResolved(resolutionResult, projectConfigurationResult, visitedArtifactsSet, artifactResolveState)

        then:
        results.resolutionResult == resolutionResult
        results.resolvedLocalComponents == projectConfigurationResult
        results.visitedArtifacts == visitedArtifactsSet
        results.artifactResolveState == artifactResolveState

        when:
        results = DefaultResolverResults.artifactsResolved(resolutionResult, projectConfigurationResult, resolvedConfiguration, visitedArtifactsSet)

        then:
        results.resolutionResult == resolutionResult
        results.resolvedLocalComponents == projectConfigurationResult
        results.visitedArtifacts == visitedArtifactsSet
        results.resolvedConfiguration == resolvedConfiguration
    }
}
