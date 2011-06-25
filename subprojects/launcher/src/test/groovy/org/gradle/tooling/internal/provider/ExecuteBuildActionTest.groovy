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

import spock.lang.Specification
import org.gradle.StartParameter
import org.gradle.GradleLauncher
import org.gradle.BuildResult

class ExecuteBuildActionTest extends Specification {
    final ExecuteBuildAction action = new ExecuteBuildAction(['a', 'b'])

    def setsTaskNamesOnStartParameter() {
        StartParameter startParameter = Mock()

        when:
        action.configureStartParameter(startParameter)

        then:
        1 * startParameter.setTaskNames(['a', 'b'])
        0 * _._
    }

    def runsBuild() {
        GradleLauncher launcher = Mock()
        BuildResult buildResult = Mock()

        when:
        def result = action.run(launcher)

        then:
        result == buildResult
        1 * launcher.run() >> buildResult
        0 * _._
    }
}
