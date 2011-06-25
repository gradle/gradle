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

import org.gradle.initialization.BuildClientMetaData
import org.gradle.initialization.ParsedCommandLine
import spock.lang.Specification

class DaemonBuildActionTest extends Specification {
    final DaemonClient client = Mock()
    final ParsedCommandLine commandLine = Mock()
    final BuildClientMetaData clientMetaData = Mock()
    final File currentDir = new File('current-dir')
    final long startTime = 90
    final Map<String, String> systemProperties = [key: 'value']
    final DaemonBuildAction action = new DaemonBuildAction(client, commandLine, currentDir, clientMetaData, startTime, systemProperties)

    def runsBuildUsingDaemon() {
        when:
        action.run()

        then:
        1 * client.execute({!null}, {!null}) >> { args ->
            ExecuteBuildAction action = args[0]
            assert action.currentDir == currentDir
            assert action.args == commandLine
            BuildActionParameters build = args[1]
            assert build.clientMetaData == clientMetaData
            assert build.startTime == startTime
            assert build.systemProperties == systemProperties
        }
        0 * _._
    }
}
