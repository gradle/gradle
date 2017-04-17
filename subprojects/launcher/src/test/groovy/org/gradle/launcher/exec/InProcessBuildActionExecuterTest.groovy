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

package org.gradle.launcher.exec

import org.gradle.BuildResult
import org.gradle.StartParameter
import org.gradle.api.internal.GradleInternal
import org.gradle.initialization.BuildRequestContext
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.initialization.DefaultGradleLauncher
import org.gradle.initialization.GradleLauncherFactory
import org.gradle.initialization.RootBuildLifecycleListener
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.invocation.BuildActionRunner
import org.gradle.internal.invocation.BuildController
import org.gradle.internal.service.ServiceRegistry
import spock.lang.Specification

class InProcessBuildActionExecuterTest extends Specification {
    final GradleLauncherFactory factory = Mock()
    final DefaultGradleLauncher launcher = Mock()
    final BuildRequestContext buildRequestContext = Mock()
    final BuildActionParameters param = Mock()
    final BuildRequestMetaData metaData = Mock()
    final BuildResult buildResult = Mock()
    final GradleInternal gradle = Mock()
    final BuildActionRunner actionRunner = Mock()
    final StartParameter startParameter = Mock()
    final ListenerManager listenerManager = Mock()
    final RootBuildLifecycleListener lifecycleListener = Mock()
    BuildAction action = Mock() {
        getStartParameter() >> startParameter
    }
    final ServiceRegistry sessionServices = Mock()
    final InProcessBuildActionExecuter executer = new InProcessBuildActionExecuter(factory, actionRunner)

    def setup() {
        _ * param.buildRequestMetaData >> metaData
        _ * param.envVariables >> [:]
        _ * sessionServices.get(ListenerManager) >> listenerManager
        _ * listenerManager.getBroadcaster(RootBuildLifecycleListener) >> lifecycleListener
    }

    def "creates launcher and forwards action to action runner after notifying listeners"() {
        when:
        def result = executer.execute(action, buildRequestContext, param, sessionServices)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(startParameter, buildRequestContext, sessionServices) >> launcher

        then:
        1 * lifecycleListener.afterStart()

        then:
        1 * actionRunner.run(action, !null) >> { BuildAction a, BuildController controller ->
            controller.result = '<result>'
        }

        then:
        1 * lifecycleListener.beforeComplete()

        then:
        1 * launcher.stop()
    }

    def "can have null result"() {
        when:
        def result = executer.execute(action, buildRequestContext, param, sessionServices)

        then:
        result == null

        and:
        1 * factory.newInstance(startParameter, buildRequestContext, sessionServices) >> launcher
        1 * actionRunner.run(action, !null) >> { BuildAction a, BuildController controller ->
            assert !controller.hasResult()
            controller.result = null
            assert controller.hasResult()
        }
        1 * launcher.stop()
    }

    def "runs build when requested by action"() {
        when:
        def result = executer.execute(action, buildRequestContext, param, sessionServices)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(startParameter, buildRequestContext, sessionServices) >> launcher
        1 * launcher.run() >> buildResult
        _ * buildResult.gradle >> gradle
        1 * actionRunner.run(action, !null) >> { BuildAction a, BuildController controller ->
            assert controller.run() == gradle
            controller.result = '<result>'
        }
        1 * launcher.stop()
    }

    def "configures build when requested by action"() {
        when:
        def result = executer.execute(action, buildRequestContext, param, sessionServices)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(startParameter, buildRequestContext, sessionServices) >> launcher
        1 * launcher.getBuildAnalysis() >> buildResult
        _ * buildResult.gradle >> gradle
        1 * actionRunner.run(action, !null) >> { BuildAction a, BuildController controller ->
            assert controller.configure() == gradle
            controller.result = '<result>'
        }
        1 * launcher.stop()
    }

    def "cannot request configuration after build has been run"() {
        given:
        actionRunner.run(action, !null) >> { BuildAction a, BuildController controller ->
            controller.run()
            controller.configure()
        }

        when:
        executer.execute(action, buildRequestContext, param, sessionServices)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot use launcher after build has completed.'

        and:
        1 * factory.newInstance(startParameter, buildRequestContext, sessionServices) >> launcher
        1 * launcher.run() >> buildResult
        1 * launcher.stop()
    }

    def "forwards action failure and cleans up"() {
        def failure = new RuntimeException()

        when:
        executer.execute(action, buildRequestContext, param, sessionServices)

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        1 * factory.newInstance(startParameter, buildRequestContext, sessionServices) >> launcher
        1 * actionRunner.run(action, !null) >> { BuildAction action, BuildController controller -> throw failure }
        1 * lifecycleListener.beforeComplete()
        1 * launcher.stop()
    }

    def "forwards build failure and cleans up"() {
        def failure = new RuntimeException()

        when:
        executer.execute(action, buildRequestContext, param, sessionServices)

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        1 * factory.newInstance(startParameter, buildRequestContext, sessionServices) >> launcher
        1 * launcher.run() >> { throw failure }
        1 * actionRunner.run(action, !null) >> { BuildAction action, BuildController controller ->
            controller.run()
        }
        1 * lifecycleListener.beforeComplete()
        1 * launcher.stop()
    }

    def "forwards configure failure and cleans up"() {
        def failure = new RuntimeException()

        when:
        executer.execute(action, buildRequestContext, param, sessionServices)

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        1 * factory.newInstance(startParameter, buildRequestContext, sessionServices) >> launcher
        1 * launcher.buildAnalysis >> { throw failure }
        1 * actionRunner.run(action, !null) >> { BuildAction action, BuildController controller ->
            controller.configure()
        }
        1 * lifecycleListener.beforeComplete()
        1 * launcher.stop()
    }

    def "cannot run after configuration failure"() {
        when:
        executer.execute(action, buildRequestContext, param, sessionServices)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot use launcher after build has completed.'

        and:
        1 * factory.newInstance(startParameter, buildRequestContext, sessionServices) >> launcher
        1 * launcher.buildAnalysis >> { throw new RuntimeException() }
        1 * actionRunner.run(action, !null) >> { BuildAction action, BuildController controller ->
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
