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
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildModelControllerServices
import org.gradle.internal.build.BuildState
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.BuildTreeFinishExecutor
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.buildtree.BuildTreeLifecycleControllerFactory
import org.gradle.internal.buildtree.BuildTreeState
import org.gradle.internal.exception.ExceptionAnalyser
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.util.Path
import spock.lang.Specification

import java.util.function.Function

class DefaultNestedBuildTest extends Specification {
    def owner = Mock(BuildState)
    def tree = Mock(BuildTreeState)
    def factory = Mock(BuildModelControllerServices)
    def controller = Mock(BuildLifecycleController)
    def gradle = Mock(GradleInternal)
    def action = Mock(Function)
    def services = new DefaultServiceRegistry()
    def buildDefinition = Mock(BuildDefinition)
    def buildIdentifier = Mock(BuildIdentifier)
    def exceptionAnalyzer = Mock(ExceptionAnalyser)
    def buildTreeController = Mock(BuildTreeLifecycleController)
    def buildTreeControllerFactory = Mock(BuildTreeLifecycleControllerFactory)
    BuildTreeFinishExecutor finishExecutor

    DefaultNestedBuild build() {
        _ * factory.servicesForBuild(buildDefinition, _, owner) >> Mock(BuildModelControllerServices.Supplier)
        _ * factory.newInstance(buildDefinition, _, owner, _) >> controller
        _ * buildDefinition.name >> "nested"
        services.add(Stub(BuildOperationExecutor))
        services.add(factory)
        services.add(exceptionAnalyzer)
        services.add(buildTreeControllerFactory)
        services.add(gradle)
        services.add(controller)
        services.add(Stub(DocumentationRegistry))
        services.add(Stub(BuildTreeWorkGraphController))
        _ * tree.services >> services
        _ * controller.gradle >> gradle
        _ * buildTreeControllerFactory.createController(_, _, _) >> { build, workExecutor, finishExecutor ->
            this.finishExecutor = finishExecutor
            buildTreeController
        }

        return new DefaultNestedBuild(buildIdentifier, Path.path(":a:b:c"), buildDefinition, owner, tree)
    }

    def "runs action and does not finish build"() {
        given:
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
        1 * buildTreeController.scheduleAndRunTasks() >> {
            finishExecutor.finishBuildTree([])
        }
        0 * controller.finishBuild(_)
    }

    def "can have null result"() {
        given:
        services.add(Stub(BuildModelParameters))
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
