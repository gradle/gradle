/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.launcher

import spock.lang.Specification
import org.gradle.StartParameter
import org.gradle.api.internal.project.ServiceRegistry
import org.gradle.initialization.GradleLauncherFactory
import org.gradle.GradleLauncher
import org.gradle.BuildResult
import org.gradle.initialization.BuildRequestMetaData

class RunBuildActionTest extends Specification {
    final StartParameter startParameter = new StartParameter()
    final ServiceRegistry loggingServices = Mock()
    final GradleLauncherFactory gradleLauncherFactory = Mock()
    final ExecutionListener completer = Mock()
    final GradleLauncher launcher = Mock()
    final BuildResult result = Mock()
    final BuildRequestMetaData requestMetaData = Mock()
    final RunBuildAction action = new RunBuildAction(startParameter, loggingServices, requestMetaData) {
        @Override
        GradleLauncherFactory createGradleLauncherFactory(ServiceRegistry loggingServices) {
            return gradleLauncherFactory
        }
    }

    def executesBuild() {
        when:
        action.execute(completer)

        then:
        1 * gradleLauncherFactory.newInstance(startParameter, requestMetaData) >> launcher
        1 * launcher.run() >> result
        _ * result.failure >> null
        0 * _._
    }

    def executesFailedBuild() {
        def failure = new RuntimeException()

        when:
        action.execute(completer)

        then:
        1 * gradleLauncherFactory.newInstance(startParameter, requestMetaData) >> launcher
        1 * launcher.run() >> result
        _ * result.failure >> failure
        1 * completer.onFailure(failure)
        0 * _._
    }

}
