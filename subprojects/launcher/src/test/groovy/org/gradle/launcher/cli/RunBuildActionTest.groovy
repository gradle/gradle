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
import org.gradle.api.logging.LogLevel
import org.gradle.initialization.BuildClientMetaData
import org.gradle.initialization.BuildRequestContext
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.cli.action.ExecuteBuildAction
import org.gradle.launcher.exec.BuildActionExecuter
import org.gradle.launcher.exec.BuildActionParameters
import spock.lang.Specification

class RunBuildActionTest extends Specification {
    final BuildActionExecuter<BuildActionParameters> client = Mock()
    final StartParameter startParameter = Mock()
    final BuildClientMetaData clientMetaData = Mock()
    final File currentDir = new File('current-dir')
    final long startTime = 90
    final Map<String, String> systemProperties = [key: 'value']
    final BuildActionParameters parameters = Mock()
    final ServiceRegistry sharedServices = Mock()
    final Stoppable stoppable = Mock()
    final RunBuildAction action = new RunBuildAction(client, startParameter, clientMetaData, startTime, parameters, sharedServices, stoppable)

    def runsBuildUsingDaemon() {
        when:
        action.run()

        then:
        startParameter.logLevel >> LogLevel.ERROR
        1 * client.execute({ !null }, { !null }, { !null }, { !null }) >> { ExecuteBuildAction action, BuildRequestContext context, BuildActionParameters build, ServiceRegistry services ->
            assert action.startParameter == startParameter
            assert context.cancellationToken instanceof DefaultBuildCancellationToken
            assert context.client == clientMetaData
            assert context.startTime == startTime
            assert build == parameters
            assert services == sharedServices
        }
        1 * stoppable.stop()
        0 * _._
    }
}
