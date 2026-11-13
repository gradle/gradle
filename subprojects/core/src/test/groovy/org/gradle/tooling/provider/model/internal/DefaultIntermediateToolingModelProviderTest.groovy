/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.tooling.provider.model.internal

import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildToolingModelController
import org.gradle.internal.buildtree.IntermediateBuildActionRunner
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.problems.failure.FailureFactory
import spock.lang.Specification

import java.util.function.Function
import java.util.function.Supplier

class DefaultIntermediateToolingModelProviderTest extends Specification {
    def scope = Mock(ToolingModelScope)
    def controller = Stub(BuildToolingModelController) {
        locateBuilderForTarget(_, _) >> scope
    }
    def build = Stub(BuildState) {
        withToolingModels(true, _) >> { boolean resilient, Function action -> action.apply(controller) }
    }
    def requester = Stub(ProjectState)
    def target = Stub(ProjectState) {
        getOwner() >> build
    }
    def actionRunner = Stub(IntermediateBuildActionRunner) {
        run(_) >> { arguments -> arguments[0].collect { Supplier action -> action.get() } }
    }
    def failureFactory = Mock(FailureFactory)
    def provider = new DefaultIntermediateToolingModelProvider(
        actionRunner,
        Stub(ToolingModelParameterCarrier.Factory),
        Stub(ToolingModelProjectDependencyListener),
        failureFactory
    )

    def "preserves model builder failures when the model is #model"() {
        given:
        def originals = [new RuntimeException("first"), new RuntimeException("second")]
        def failures = originals.collect { original -> Stub(Failure) { getOriginal() >> original } }
        def clientResult = ToolingModelBuilderResultInternal.of(model, failures)
        scope.getModel(_, _) >> ToolingModelScopeResult.withModelBuilderFailures(clientResult, originals)

        when:
        def results = provider.getModelsAllowingFailures(requester, [target], String, null)

        then:
        results.size() == 1
        results[0].model == model
        results[0].failures == failures
        results[0].modelBuilderFailures == originals

        where:
        model << ["partial model", null]
    }

    def "configuration failures accompanying a partial model are not model builder failures"() {
        given:
        def configurationFailure = new RuntimeException("a sibling failed to configure")
        def clientFailure = Stub(Failure)
        def clientResult = ToolingModelBuilderResultInternal.of("partial model", [clientFailure])
        scope.getModel(_, _) >> ToolingModelScopeResult.withConfigurationFailure(clientResult, configurationFailure)

        when:
        def results = provider.getModelsAllowingFailures(requester, [target], String, null)

        then:
        results[0].model == "partial model"
        results[0].failures == [clientFailure]
        results[0].modelBuilderFailures.empty
    }

    def "unexpected exceptions are propagated as model builder failures"() {
        given:
        def failure = new RuntimeException("unexpected failure")
        def clientFailure = Stub(Failure)
        scope.getModel(_, _) >> { throw failure }
        failureFactory.create(failure) >> clientFailure

        when:
        def results = provider.getModelsAllowingFailures(requester, [target], String, null)

        then:
        results[0].model == null
        results[0].failures == [clientFailure]
        results[0].modelBuilderFailures == [failure]
    }
}
