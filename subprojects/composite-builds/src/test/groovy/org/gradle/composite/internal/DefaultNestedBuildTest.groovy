/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.composite.internal

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.initialization.exception.ExceptionAnalyser
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildLifecycleControllerFactory
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.ExecutionResult
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.buildtree.BuildTreeState
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.util.Path
import spock.lang.Specification

import java.util.function.Function

class DefaultNestedBuildTest extends Specification {
    def owner = Mock(BuildState)
    def tree = Mock(BuildTreeState)
    def factory = Mock(BuildLifecycleControllerFactory)
    def controller = Mock(BuildLifecycleController)
    def gradle = Mock(GradleInternal)
    def action = Mock(Function)
    def sessionServices = new DefaultServiceRegistry()
    def buildDefinition = Mock(BuildDefinition)
    def buildIdentifier = Mock(BuildIdentifier)
    def projectStateRegistry = Mock(ProjectStateRegistry)
    def exceptionAnalyzer = Mock(ExceptionAnalyser)
    def workGraph = Mock(BuildTreeWorkGraph)

    DefaultNestedBuild build() {
        _ * owner.currentPrefixForProjectsInChildBuilds >> Path.path(":owner")
        _ * factory.newInstance(buildDefinition, _, owner, _) >> controller
        _ * buildDefinition.name >> "nested"
        sessionServices.add(Stub(BuildOperationExecutor))
        sessionServices.add(exceptionAnalyzer)
        sessionServices.add(new TestBuildTreeLifecycleControllerFactory(workGraph))
        sessionServices.add(gradle)
        sessionServices.add(Stub(DocumentationRegistry))
        sessionServices.add(Stub(BuildTreeWorkGraphController))
        _ * tree.services >> sessionServices
        _ * controller.gradle >> gradle

        return new DefaultNestedBuild(buildIdentifier, Path.path(":a:b:c"), buildDefinition, owner, tree, factory, projectStateRegistry)
    }

    def "stops controller on stop"() {
        sessionServices.add(Stub(BuildModelParameters))
        def build = build()

        when:
        build.stop()

        then:
        1 * controller.stop()
    }

    def "runs action and finishes build when model is not required by root build"() {
        given:
        sessionServices.add(new BuildModelParameters(false, false, false, false, false))
        def build = build()

        when:
        def result = build.run(action)

        then:
        result == '<result>'

        then:
        1 * action.apply(!null) >> { BuildTreeLifecycleController controller ->
            controller.scheduleAndRunTasks()
            '<result>'
        }
        1 * workGraph.runWork() >> ExecutionResult.succeeded()
        1 * controller.finishBuild(_) >> ExecutionResult.succeeded()
    }

    def "runs action but does not finish build when model is required by root build"() {
        given:
        sessionServices.add(new BuildModelParameters(false, false, false, true, false))
        def build = build()

        when:
        def result = build.run(action)

        then:
        result == '<result>'

        then:
        1 * action.apply(!null) >> { BuildTreeLifecycleController controller ->
            controller.scheduleAndRunTasks()
            '<result>'
        }
        1 * workGraph.runWork() >> ExecutionResult.succeeded()
        0 * controller.finishBuild(_, _)
    }

    def "can have null result"() {
        given:
        sessionServices.add(Stub(BuildModelParameters))
        def build = build()

        when:
        def result = build.run(action)

        then:
        result == null

        and:
        1 * action.apply(!null) >> { BuildTreeLifecycleController controller ->
            return null
        }
    }
}
