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

import org.gradle.BuildResult
import org.gradle.api.Action
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.initialization.BuildRequestContext
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.initialization.DefaultGradleLauncher
import org.gradle.initialization.GradleLauncherFactory
import org.gradle.initialization.RootBuildLifecycleListener
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.invocation.BuildActionRunner
import org.gradle.internal.invocation.BuildController
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.launcher.exec.BuildActionParameters
import org.gradle.test.fixtures.work.TestWorkerLeaseService
import spock.lang.Specification

class DefaultRootBuildStateTest extends Specification {
    def factory = Mock(GradleLauncherFactory)
    final DefaultGradleLauncher launcher = Mock()
    final BuildRequestContext buildRequestContext = Mock()
    final BuildActionParameters param = Mock()
    final BuildRequestMetaData metaData = Mock()
    final BuildResult buildResult = Mock()
    final GradleInternal gradle = Mock()
    final BuildActionRunner actionRunner = Mock()
    final StartParameterInternal startParameter = Mock()
    final ListenerManager listenerManager = Mock()
    final BuildOperationExecutor buildOperationExecutor = Mock()
    final WorkerLeaseService workerLeaseService = new TestWorkerLeaseService()
    final RootBuildLifecycleListener lifecycleListener = Mock()
    def action = Mock(Action)
    def sessionServices = Mock(ServiceRegistry)
    def buildDefinition = Mock(BuildDefinition)
    def build = new DefaultRootBuildState(buildDefinition, buildRequestContext, factory, listenerManager, sessionServices)

    def setup() {
        _ * param.buildRequestMetaData >> metaData
        _ * param.envVariables >> [:]
        _ * sessionServices.get(ListenerManager) >> listenerManager
        _ * sessionServices.get(BuildOperationExecutor) >> buildOperationExecutor
        _ * sessionServices.get(WorkerLeaseService) >> workerLeaseService
        _ * sessionServices.get(GradleLauncherFactory) >> factory
        _ * listenerManager.getBroadcaster(RootBuildLifecycleListener) >> lifecycleListener
        _ * launcher.getGradle() >> gradle
        _ * gradle.services >> sessionServices
    }

    def "creates launcher and runs action after notifying listeners"() {
        when:
        def result = build.run(action)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(buildDefinition, buildRequestContext, sessionServices) >> launcher

        then:
        1 * lifecycleListener.afterStart()

        then:
        1 * action.execute(!null) >> { BuildController controller ->
            controller.result = '<result>'
        }

        then:
        1 * lifecycleListener.beforeComplete()

        then:
        1 * launcher.stop()
    }

    def "can have null result"() {
        when:
        def result = build.run(action)

        then:
        result == null

        and:
        1 * factory.newInstance(buildDefinition, buildRequestContext, sessionServices) >> launcher
        1 * action.execute(!null) >> { BuildController controller ->
            assert !controller.hasResult()
            controller.result = null
            assert controller.hasResult()
        }
        1 * launcher.stop()
    }

    def "runs build when requested by action"() {
        when:
        def result = build.run(action)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(buildDefinition, buildRequestContext, sessionServices) >> launcher
        1 * launcher.executeTasks() >> gradle
        1 * action.execute(!null) >> { BuildController controller ->
            assert controller.run() == gradle
            controller.result = '<result>'
        }
        1 * launcher.stop()
    }

    def "configures build when requested by action"() {
        when:
        def result = build.run(action)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(buildDefinition, buildRequestContext, sessionServices) >> launcher
        1 * launcher.getConfiguredBuild() >> gradle
        1 * action.execute(!null) >> { BuildController controller ->
            assert controller.configure() == gradle
            controller.result = '<result>'
        }
        1 * launcher.stop()
    }

    def "cannot request configuration after build has been run"() {
        given:
        action.execute(!null) >> { BuildController controller ->
            controller.run()
            controller.configure()
        }

        when:
        build.run(action)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot use launcher after build has completed.'

        and:
        1 * factory.newInstance(buildDefinition, buildRequestContext, sessionServices) >> launcher
        1 * launcher.executeTasks() >> gradle
        1 * launcher.stop()
    }

    def "forwards action failure and cleans up"() {
        def failure = new RuntimeException()

        when:
        build.run(action)

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        1 * factory.newInstance(buildDefinition, buildRequestContext, sessionServices) >> launcher
        1 * action.execute(!null) >> { BuildController controller -> throw failure }
        1 * lifecycleListener.beforeComplete()
        1 * launcher.stop()
    }

    def "forwards build failure and cleans up"() {
        def failure = new RuntimeException()

        when:
        build.run(action)

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        1 * factory.newInstance(buildDefinition, buildRequestContext, sessionServices) >> launcher
        1 * launcher.executeTasks() >> { throw failure }
        1 * action.execute(!null) >> { BuildController controller ->
            controller.run()
        }
        1 * lifecycleListener.beforeComplete()
        1 * launcher.stop()
    }

    def "forwards configure failure and cleans up"() {
        def failure = new RuntimeException()

        when:
        build.run(action)

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        1 * factory.newInstance(buildDefinition, buildRequestContext, sessionServices) >> launcher
        1 * launcher.getConfiguredBuild() >> { throw failure }
        1 * action.execute(!null) >> { BuildController controller ->
            controller.configure()
        }
        1 * lifecycleListener.beforeComplete()
        1 * launcher.stop()
    }

    def "cannot run after configuration failure"() {
        when:
        build.run(action)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot use launcher after build has completed.'

        and:
        1 * factory.newInstance(buildDefinition, buildRequestContext, sessionServices) >> launcher
        1 * launcher.configuredBuild >> { throw new RuntimeException() }
        1 * action.execute(!null) >> { BuildController controller ->
            try {
                controller.configure()
            } catch (RuntimeException) {
                // Ignore
            }
            controller.run()
        }
        1 * lifecycleListener.beforeComplete()
        1 * launcher.stop()
    }
}
