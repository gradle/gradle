/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.buildtree

import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.ExecutionResult
import org.gradle.internal.build.NestedBuildState
import org.gradle.internal.exception.ExceptionAnalyser
import spock.lang.Specification

import java.util.function.Consumer

class DefaultBuildTreeFinishExecutorTest extends Specification {
    def buildStateRegistry = Mock(BuildStateRegistry)
    def exceptionAnalyser = Mock(ExceptionAnalyser)
    def buildController = Mock(BuildLifecycleController)
    def nestedBuild = Mock(NestedBuildState)
    def executor = new DefaultBuildTreeFinishExecutor(buildStateRegistry, exceptionAnalyser, buildController)

    def setup() {
        buildStateRegistry.visitBuilds(_) >> { Consumer visitor -> visitor.accept(nestedBuild) }
        nestedBuild.beforeModelDiscarded(_) >> ExecutionResult.succeeded()
    }

    def "finishes the root build when finishing a nested build throws"() {
        def originalFailure = new RuntimeException("original failure")
        def nestedFailure = new RuntimeException("could not finish nested build")
        def reportableFailure = new RuntimeException("reportable failure")

        when:
        def result = executor.finishBuildTree([originalFailure])

        then:
        result == reportableFailure

        and:
        1 * nestedBuild.finishBuild() >> { throw nestedFailure }

        and:
        // The failure thrown while finishing the nested build is collected
        // alongside the original failure, instead of escaping and skipping
        // the rest of the finish sequence.
        1 * exceptionAnalyser.transform([originalFailure, nestedFailure]) >> reportableFailure

        and:
        // The root build is still finished, so its buildFinished event fires
        // and the failure is reported to the user.
        1 * buildController.finishBuild(reportableFailure) >> ExecutionResult.succeeded()

        and:
        1 * exceptionAnalyser.transform([reportableFailure]) >> reportableFailure
    }

    def "finishes the root build when finishing a nested build fails"() {
        def originalFailure = new RuntimeException("original failure")
        def nestedFailure = new RuntimeException("nested build failed")
        def reportableFailure = new RuntimeException("reportable failure")

        when:
        def result = executor.finishBuildTree([originalFailure])

        then:
        result == reportableFailure

        and:
        1 * nestedBuild.finishBuild() >> ExecutionResult.failed(nestedFailure)

        and:
        1 * exceptionAnalyser.transform([originalFailure, nestedFailure]) >> reportableFailure

        and:
        1 * buildController.finishBuild(reportableFailure) >> ExecutionResult.succeeded()

        and:
        1 * exceptionAnalyser.transform([reportableFailure]) >> reportableFailure
    }

    def "returns null when there are no failures"() {
        when:
        def result = executor.finishBuildTree([])

        then:
        result == null

        and:
        1 * nestedBuild.finishBuild() >> ExecutionResult.succeeded()

        and:
        1 * buildController.finishBuild(null) >> ExecutionResult.succeeded()

        and:
        2 * exceptionAnalyser.transform([]) >> null
    }
}
