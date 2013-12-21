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

import org.gradle.initialization.BuildAction
import org.gradle.launcher.daemon.client.DaemonClient
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.launcher.exec.ReportedException
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters
import spock.lang.Specification

class DaemonBuildActionExecuterTest extends Specification {
    final DaemonClient client = Mock()
    final BuildAction<String> action = Mock()
    final ProviderOperationParameters parameters = Mock()
    final DaemonParameters daemonParameters = Mock()
    final DaemonBuildActionExecuter executer = new DaemonBuildActionExecuter(client, daemonParameters)

    def unpacksReportedException() {
        def failure = new RuntimeException()

        when:
        executer.execute(action, parameters)

        then:
        BuildExceptionVersion1 e = thrown()
        e.cause == failure
        1 * client.execute(action, !null) >> { throw new ReportedException(failure) }
        _ * daemonParameters.effectiveSystemProperties >> [:]
    }
}
