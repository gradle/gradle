/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.provider

import org.gradle.BuildResult
import org.gradle.GradleLauncher
import org.gradle.initialization.GradleLauncherAction
import org.gradle.initialization.GradleLauncherFactory
import org.gradle.launcher.InitializationAware
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1
import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1
import spock.lang.Specification

class EmbeddedGradleLauncherActionExecuterTest extends Specification {
    final BuildOperationParametersVersion1 parameters = Mock()
    final GradleLauncherFactory gradleLauncherFactory = Mock()
    final GradleLauncher gradleLauncher = Mock()
    final BuildResult buildResult = Mock()
    final EmbeddedGradleLauncherActionExecuter executer = new EmbeddedGradleLauncherActionExecuter(gradleLauncherFactory)

    def executesActionAndReturnsResult() {
        GradleLauncherAction<String> action = Mock()

        when:
        def result = executer.execute(action, parameters)

        then:
        result == 'result'
        1 * gradleLauncherFactory.newInstance(!null) >> gradleLauncher
        1 * action.run(gradleLauncher) >> buildResult
        1 * action.result >> 'result'
    }

    def actionCanConfigureStartParameters() {
        TestAction action = Mock()

        when:
        def result = executer.execute(action, parameters)

        then:
        result == 'result'
        1 * gradleLauncherFactory.newInstance(!null) >> gradleLauncher
        1 * action.configureStartParameter(!null)
        1 * action.run(gradleLauncher) >> buildResult
        1 * action.result >> 'result'
    }

    def wrapsBuildFailure() {
        GradleLauncherAction<String> action = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        executer.execute(action, parameters)

        then:
        BuildExceptionVersion1 e = thrown()
        e.cause == failure
        1 * gradleLauncherFactory.newInstance(!null) >> gradleLauncher
        1 * action.run(gradleLauncher) >> buildResult
        buildResult.failure >> failure
    }
}

interface TestAction extends GradleLauncherAction<String>, InitializationAware {}

