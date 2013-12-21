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
package org.gradle.launcher.cli

import org.gradle.StartParameter
import org.gradle.initialization.BuildClientMetaData
import org.gradle.launcher.exec.BuildActionParameters
import org.gradle.launcher.exec.BuildActionExecuter
import spock.lang.Specification
import org.gradle.api.logging.LogLevel

class RunBuildActionTest extends Specification {
    final BuildActionExecuter<BuildActionParameters> client = Mock()
    final StartParameter startParameter = Mock()
    final BuildClientMetaData clientMetaData = Mock()
    final File currentDir = new File('current-dir')
    final long startTime = 90
    final Map<String, String> systemProperties = [key: 'value']
    final Map<String, String> envVariables = [key2: 'value2']
    final RunBuildAction action = new RunBuildAction(client, startParameter, currentDir, clientMetaData, startTime, systemProperties, envVariables)

    def runsBuildUsingDaemon() {
        when:
        action.run()

        then:
        startParameter.logLevel >> LogLevel.ERROR
        1 * client.execute({!null}, {!null}) >> { args ->
            ExecuteBuildAction action = args[0]
            assert action.startParameter == startParameter
            BuildActionParameters build = args[1]
            assert build.clientMetaData == clientMetaData
            assert build.startTime == startTime
            assert build.systemProperties == systemProperties
        }
        0 * _._
    }
}
