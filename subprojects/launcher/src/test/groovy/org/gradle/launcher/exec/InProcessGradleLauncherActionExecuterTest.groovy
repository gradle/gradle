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

import org.gradle.initialization.BuildController
import spock.lang.Specification
import org.gradle.initialization.GradleLauncherFactory
import org.gradle.initialization.BuildAction

import org.gradle.initialization.BuildRequestMetaData
import org.gradle.GradleLauncher
import org.gradle.BuildResult

import org.gradle.StartParameter

class InProcessGradleLauncherActionExecuterTest extends Specification {
    final GradleLauncherFactory factory = Mock()
    final GradleLauncher launcher = Mock()
    final BuildActionParameters param = Mock()
    final BuildRequestMetaData metaData = Mock()
    final BuildResult buildResult = Mock()
    final InProcessGradleLauncherActionExecuter executer = new InProcessGradleLauncherActionExecuter(factory)

    def setup() {
        _ * param.buildRequestMetaData >> metaData
    }

    def "creates a launcher using a default StartParameter when the action does not specify any"() {
        BuildAction<String> action = Mock()

        when:
        def result = executer.execute(action, param)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(!null, metaData) >> launcher
        1 * action.run(!null) >> { BuildController controller ->
            assert controller.launcher == launcher
            return '<result>'
        }
    }

    def "creates a launcher using StartParameter specified by the action"() {
        BuildAction<String> action = Mock()
        def startParam = new StartParameter()

        when:
        def result = executer.execute(action, param)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(startParam, metaData) >> launcher
        1 * action.run(!null) >> { BuildController controller ->
            controller.startParameter = startParam
            assert controller.launcher == launcher
            return '<result>'
        }
    }

    def "cannot set start parameters after launcher created"() {
        BuildAction<String> action = Mock()
        def startParam = new StartParameter()

        given:
        _ * action.run(!null) >> { BuildController controller ->
            controller.launcher
            controller.startParameter = startParam
        }
        _ * factory.newInstance(!null, metaData) >> launcher

        when:
        executer.execute(action, param)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot change start parameter after launcher has been created.'
    }

    def "runs build when requested by action"() {
        BuildAction<String> action = Mock()

        when:
        def result = executer.execute(action, param)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(!null, metaData) >> launcher
        1 * launcher.run() >> buildResult
        _ * buildResult.failure >> null
        1 * action.run(!null) >> { BuildController controller ->
            controller.run()
            return '<result>'
        }
    }

    def "configures build when requested by action"() {
        BuildAction<String> action = Mock()

        when:
        def result = executer.execute(action, param)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(!null, metaData) >> launcher
        1 * launcher.getBuildAnalysis() >> buildResult
        _ * buildResult.failure >> null
        1 * action.run(!null) >> { BuildController controller ->
            controller.configure()
            return '<result>'
        }
    }

    def "wraps build failure"() {
        def failure = new RuntimeException()
        BuildAction<String> action = Mock()

        given:
        buildResult.failure >> failure

        when:
        executer.execute(action, param)

        then:
        ReportedException e = thrown()
        e.cause == failure

        and:
        1 * factory.newInstance(!null, metaData) >> launcher
        1 * launcher.run() >> buildResult
        1 * action.run(!null) >> { BuildController controller ->
            controller.run()
        }
    }
}
