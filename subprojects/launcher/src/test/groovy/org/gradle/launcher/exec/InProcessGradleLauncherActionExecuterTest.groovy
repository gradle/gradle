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

import spock.lang.Specification
import org.gradle.initialization.GradleLauncherFactory
import org.gradle.initialization.GradleLauncherAction

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

    def "executes the provided action using a default StartParameter"() {
        GradleLauncherAction<String> action = Mock()

        given:
        buildResult.failure >> null
        action.result >> '<result>'

        when:
        def result = executer.execute(action, param)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(!null, metaData) >> launcher
        1 * action.run(launcher) >> buildResult
    }

    def "executes the provided action using the provided StartParameter"() {
        TestAction action = Mock()
        def startParam = new StartParameter()

        given:
        action.configureStartParameter() >> startParam
        buildResult.failure >> null
        action.result >> '<result>'

        when:
        def result = executer.execute(action, param)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(startParam, metaData) >> launcher
        1 * action.run(launcher) >> buildResult
    }

    def "wraps build failure"() {
        def failure = new RuntimeException()
        GradleLauncherAction<String> action = Mock()

        given:
        buildResult.failure >> failure

        when:
        executer.execute(action, param)

        then:
        ReportedException e = thrown()
        e.cause == failure

        and:
        1 * factory.newInstance(!null, metaData) >> launcher
        1 * action.run(launcher) >> buildResult
    }
}

interface TestAction extends GradleLauncherAction<String>, InitializationAware {
}
