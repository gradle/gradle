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

import org.gradle.launcher.DaemonClient
import spock.lang.Specification
import org.gradle.launcher.ReportedException
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1
import org.gradle.initialization.GradleLauncherAction
import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1

class DaemonGradleLauncherActionExecuterTest extends Specification {
    final DaemonClient client = Mock()
    final GradleLauncherAction<String> action = Mock()
    final BuildOperationParametersVersion1 parameters = Mock()
    final DaemonGradleLauncherActionExecuter executer = new DaemonGradleLauncherActionExecuter(client)

    def unpacksReportedException() {
        def failure = new RuntimeException()

        when:
        executer.execute(action, parameters)

        then:
        BuildExceptionVersion1 e = thrown()
        e.cause == failure
        1 * client.execute(action, !null) >> { throw new ReportedException(failure) }
    }
}
