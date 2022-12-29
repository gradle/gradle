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

import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.deployment.internal.DefaultDeploymentRegistry
import org.gradle.initialization.RootBuildLifecycleListener
import org.gradle.initialization.exception.ExceptionAnalyser
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildModelControllerServices
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.buildtree.BuildTreeLifecycleControllerFactory
import org.gradle.internal.buildtree.BuildTreeState
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.service.DefaultServiceRegistry
import spock.lang.Specification

import java.util.function.Function

class DefaultRootBuildStateTest extends Specification {
    def factory = Mock(BuildModelControllerServices)
    def controller = Mock(BuildLifecycleController)
    def gradle = Mock(GradleInternal)
    def listenerManager = Mock(ListenerManager)
    def lifecycleListener = Mock(RootBuildLifecycleListener)
    def action = Mock(Function)
    def buildTree = Mock(BuildTreeState)
    def buildDefinition = Mock(BuildDefinition)
    def exceptionAnalyzer = Mock(ExceptionAnalyser)
    def buildTreeController = Mock(BuildTreeLifecycleController)
    def buildTreeControllerFactory = Mock(BuildTreeLifecycleControllerFactory)
    DefaultRootBuildState build

    def setup() {
        _ * factory.servicesForBuild(buildDefinition, _, null) >> Mock(BuildModelControllerServices.Supplier)
        _ * listenerManager.getBroadcaster(RootBuildLifecycleListener) >> lifecycleListener
        def services = new DefaultServiceRegistry()
        services.add(new TestBuildOperationExecutor())
        services.add(gradle)
        services.add(exceptionAnalyzer)
        services.add(controller)
        services.add(factory)
        services.add(Stub(DocumentationRegistry))
        services.add(Stub(DefaultDeploymentRegistry))
        services.add(Stub(BuildStateRegistry))
        services.add(buildTreeControllerFactory)

        _ * buildTreeControllerFactory.createRootBuildController(_, _, _) >> buildTreeController
        _ * controller.gradle >> gradle
        _ * gradle.services >> services
        _ * buildTree.services >> services

        build = new DefaultRootBuildState(buildDefinition, buildTree, listenerManager)
    }

    def "has identifier"() {
        expect:
        build.buildIdentifier == DefaultBuildIdentifier.ROOT
    }

    def "runs action that does nothing"() {
        when:
        def result = build.run(action)

        then:
        result == '<result>'

        1 * lifecycleListener.afterStart()
        1 * controller.gradle >> gradle

        1 * action.apply(!null) >> { BuildTreeLifecycleController controller ->
            '<result>'
        }

        1 * lifecycleListener.beforeComplete()
        0 * controller._
        0 * lifecycleListener._
    }

    def "can have null result"() {
        when:
        def result = build.run(action)

        then:
        result == null

        and:
        1 * action.apply(!null) >> { BuildTreeLifecycleController controller ->
            return null
        }
    }

    def "runs tasks and finishes build when requested by action"() {
        when:
        def result = build.run(action)

        then:
        result == '<result>'

        and:
        1 * action.apply(!null) >> { BuildTreeLifecycleController controller ->
            controller.scheduleAndRunTasks()
            return '<result>'
        }

        and:
        1 * lifecycleListener.afterStart()

        and:
        1 * buildTreeController.scheduleAndRunTasks()

        and:
        1 * lifecycleListener.beforeComplete()
        0 * lifecycleListener._
    }

    def "forwards action failure and cleans up"() {
        def failure = new RuntimeException()

        when:
        build.run(action)

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        1 * action.apply(!null) >> { BuildTreeLifecycleController controller -> throw failure }
        1 * lifecycleListener.afterStart()
        1 * lifecycleListener.beforeComplete()
        0 * lifecycleListener._
    }

    def "forwards build failure and cleans up"() {
        def failure = new RuntimeException()

        when:
        build.run(action)

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        1 * action.apply(!null) >> { BuildTreeLifecycleController controller ->
            controller.scheduleAndRunTasks()
        }

        and:
        1 * lifecycleListener.afterStart()

        and:
        1 * buildTreeController.scheduleAndRunTasks() >> { throw failure }

        and:
        1 * lifecycleListener.beforeComplete()
        0 * lifecycleListener._
    }

    def "cannot run multiple actions"() {
        given:
        action.apply(!null) >> { BuildTreeLifecycleController controller ->
            controller.scheduleAndRunTasks()
        }

        build.run(action)

        when:
        build.run(action)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot run more than one action for a build.'

        and:
        0 * lifecycleListener._
    }

}
