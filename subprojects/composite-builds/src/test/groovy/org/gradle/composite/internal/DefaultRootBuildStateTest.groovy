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
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.deployment.internal.DefaultDeploymentRegistry
import org.gradle.initialization.RootBuildLifecycleListener
import org.gradle.initialization.exception.ExceptionAnalyser
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildLifecycleControllerFactory
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildtree.BuildTreeState
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.test.fixtures.work.TestWorkerLeaseService
import spock.lang.Specification

import java.util.function.Consumer
import java.util.function.Function

class DefaultRootBuildStateTest extends Specification {
    def factory = Mock(BuildLifecycleControllerFactory)
    def launcher = Mock(BuildLifecycleController)
    def gradle = Mock(GradleInternal)
    def listenerManager = Mock(ListenerManager)
    def lifecycleListener = Mock(RootBuildLifecycleListener)
    def action = Mock(Function)
    def buildTree = Mock(BuildTreeState)
    def buildDefinition = Mock(BuildDefinition)
    def projectStateRegistry = Mock(ProjectStateRegistry)
    def includedBuildControllers = Mock(IncludedBuildControllers)
    def exceptionAnalyzer = Mock(ExceptionAnalyser)
    DefaultRootBuildState build

    def setup() {
        _ * factory.newInstance(buildDefinition, _, null, _) >> launcher
        _ * listenerManager.getBroadcaster(RootBuildLifecycleListener) >> lifecycleListener
        def sessionServices = new DefaultServiceRegistry()
        sessionServices.add(new TestBuildOperationExecutor())
        sessionServices.add(new TestWorkerLeaseService())
        sessionServices.add(includedBuildControllers)
        sessionServices.add(exceptionAnalyzer)
        sessionServices.add(Stub(DefaultDeploymentRegistry))
        sessionServices.add(Stub(BuildStateRegistry))
        _ * launcher.gradle >> gradle
        _ * gradle.services >> sessionServices
        _ * buildTree.services >> sessionServices
        _ * projectStateRegistry.withLenientState(_) >> { args -> return args[0].create() }

        build = new DefaultRootBuildState(buildDefinition, buildTree, factory, listenerManager, projectStateRegistry)
    }

    def "has identifier"() {
        expect:
        build.buildIdentifier == DefaultBuildIdentifier.ROOT
    }

    def "stops launcher on stop"() {
        when:
        build.stop()

        then:
        1 * launcher.stop()
    }

    def "runs action that does nothing"() {
        when:
        def result = build.run(action)

        then:
        result == '<result>'

        1 * lifecycleListener.afterStart()
        1 * launcher.gradle >> gradle

        1 * action.apply(!null) >> { BuildTreeLifecycleController controller ->
            '<result>'
        }

        1 * lifecycleListener.beforeComplete()
        0 * launcher._
        0 * includedBuildControllers._
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
        1 * launcher.scheduleRequestedTasks()
        1 * includedBuildControllers.startTaskExecution()
        1 * launcher.executeTasks()
        1 * includedBuildControllers.awaitTaskCompletion(_)
        1 * launcher.finishBuild(null, _)

        and:
        1 * lifecycleListener.beforeComplete()
        0 * lifecycleListener._
    }

    def "configures and finishes build when requested by action"() {
        when:
        def result = build.run(action)

        then:
        result == '<result>'

        and:
        1 * action.apply(!null) >> { BuildTreeLifecycleController controller ->
            controller.fromBuildModel(false) {'<result>' }
        }

        and:
        1 * lifecycleListener.afterStart()

        and:
        1 * launcher.getConfiguredBuild() >> gradle
        1 * launcher.finishBuild(null, _)

        and:
        1 * lifecycleListener.beforeComplete()
        0 * lifecycleListener._
    }

    def "cannot request configuration after build has been run"() {
        given:
        action.apply(!null) >> { BuildTreeLifecycleController controller ->
            controller.scheduleAndRunTasks()
            controller.fromBuildModel(false) {'<result>' }
        }

        when:
        build.run(action)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot run more than one action for this build.'
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
        def transformedFailure = new RuntimeException()

        when:
        build.run(action)

        then:
        RuntimeException e = thrown()
        e == transformedFailure

        and:
        1 * action.apply(!null) >> { BuildTreeLifecycleController controller ->
            controller.scheduleAndRunTasks()
        }

        and:
        1 * lifecycleListener.afterStart()

        and:
        1 * launcher.executeTasks() >> { throw failure }
        2 * exceptionAnalyzer.transform(_) >> { ex ->
            assert ex[0] == [failure]
            return transformedFailure
        }
        1 * launcher.finishBuild(transformedFailure, _)

        and:
        1 * lifecycleListener.beforeComplete()
        0 * lifecycleListener._
    }

    def "forwards configure failure and cleans up"() {
        def failure = new RuntimeException()
        def transformedFailure = new RuntimeException()

        when:
        build.run(action)

        then:
        RuntimeException e = thrown()
        e == transformedFailure

        and:
        1 * action.apply(!null) >> { BuildTreeLifecycleController controller ->
            controller.fromBuildModel(false) {'<result>' }
        }

        and:
        1 * lifecycleListener.afterStart()

        and:
        1 * launcher.getConfiguredBuild() >> { throw failure }
        2 * exceptionAnalyzer.transform(_) >> { ex ->
            assert ex[0] == [failure]
            return transformedFailure
        }
        1 * launcher.finishBuild(transformedFailure, _)

        and:
        1 * lifecycleListener.beforeComplete()
        0 * lifecycleListener._
    }

    def "collects and transforms build execution and finish failures"() {
        def failure1 = new RuntimeException()
        def failure2 = new RuntimeException()
        def failure3 = new RuntimeException()
        def transformedFailure = new RuntimeException()
        def finalFailure = new RuntimeException()

        when:
        build.run(action)

        then:
        RuntimeException e = thrown()
        e == finalFailure

        and:
        1 * action.apply(!null) >> { BuildTreeLifecycleController controller ->
            controller.scheduleAndRunTasks()
        }

        and:
        1 * lifecycleListener.afterStart()

        and:
        1 * launcher.executeTasks() >> { throw failure1 }
        1 * includedBuildControllers.awaitTaskCompletion(_) >> { Consumer consumer -> consumer.accept(failure2) }
        1 * exceptionAnalyzer.transform(_) >> { ex ->
            assert ex[0] == [failure1, failure2]
            return transformedFailure
        }
        1 * launcher.finishBuild(transformedFailure, _) >> { Throwable throwable, Consumer consumer -> consumer.accept(failure3) }
        1 * exceptionAnalyzer.transform(_) >> { ex ->
            assert ex[0] == [failure1, failure2, failure3]
            return finalFailure
        }

        and:
        1 * lifecycleListener.beforeComplete()
        0 * lifecycleListener._
    }

    def "cannot run after configuration failure"() {
        when:
        build.run(action)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot run more than one action for this build.'

        and:
        1 * lifecycleListener.afterStart()
        1 * launcher.configuredBuild >> { throw new RuntimeException() }
        1 * action.apply(!null) >> { BuildTreeLifecycleController controller ->
            try {
                controller.fromBuildModel(false) {'<result>' }
            } catch (RuntimeException) {
                // Ignore
            }
            controller.scheduleAndRunTasks()
        }
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
