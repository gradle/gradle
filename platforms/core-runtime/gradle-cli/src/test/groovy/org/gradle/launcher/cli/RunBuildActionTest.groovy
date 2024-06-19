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

import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.logging.LogLevel
import org.gradle.configuration.GradleLauncherMetaData
import org.gradle.initialization.BuildRequestContext
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.initialization.ReportedException
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.daemon.client.execution.ClientBuildRequestContext
import org.gradle.internal.nativeintegration.console.ConsoleDetector
import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.exec.BuildActionExecutor
import org.gradle.launcher.exec.BuildActionParameters
import org.gradle.launcher.exec.BuildActionResult
import org.gradle.tooling.internal.provider.action.ExecuteBuildAction
import org.gradle.tooling.internal.provider.serialization.SerializedPayload
import spock.lang.Specification

class RunBuildActionTest extends Specification {
    final BuildActionExecutor<BuildActionParameters, BuildRequestContext> client = Mock()
    final StartParameterInternal startParameter = Mock()
    final GradleLauncherMetaData clientMetaData = Mock()
    final long startTime = 90
    final BuildActionParameters parameters = Mock()
    final ServiceRegistry sharedServices = Mock()
    final Stoppable stoppable = Mock()
    final RunBuildAction action = new RunBuildAction(client, startParameter, clientMetaData, startTime, parameters, sharedServices, stoppable)

    def runsBuildUsingDaemon() {
        when:
        action.run()

        then:
        startParameter.logLevel >> LogLevel.ERROR
        1 * sharedServices.get(ConsoleDetector) >> Stub(ConsoleDetector)
        1 * client.execute({ !null }, { !null }, { !null }) >> { ExecuteBuildAction action, BuildActionParameters build, ClientBuildRequestContext context ->
            assert action.startParameter == startParameter
            assert context.cancellationToken instanceof DefaultBuildCancellationToken
            assert context.client == clientMetaData
            assert context.startTime == startTime
            assert build == parameters
            return BuildActionResult.of(null)
        }
        1 * stoppable.stop()
        0 * _._
    }

    def throwsExceptionOnBuildFailure() {
        when:
        action.run()

        then:
        thrown(ReportedException)

        and:
        startParameter.logLevel >> LogLevel.ERROR
        1 * sharedServices.get(ConsoleDetector) >> Stub(ConsoleDetector)
        1 * client.execute(_, _, _) >> {
            return BuildActionResult.failed(new SerializedPayload("thing", []))
        }
        1 * stoppable.stop()
        0 * _._
    }
}
