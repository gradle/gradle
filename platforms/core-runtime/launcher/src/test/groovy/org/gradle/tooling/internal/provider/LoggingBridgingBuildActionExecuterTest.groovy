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

import org.gradle.api.logging.LogLevel
import org.gradle.initialization.BuildRequestContext
import org.gradle.internal.daemon.client.execution.ClientBuildRequestContext
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.launcher.exec.BuildActionExecutor
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters
import spock.lang.Specification

class LoggingBridgingBuildActionExecuterTest extends Specification {
    final BuildActionExecutor<ConnectionOperationParameters, BuildRequestContext> target = Mock()
    final LoggingManagerInternal loggingManager = Mock()
    final BuildAction action = Mock()
    final ClientBuildRequestContext buildRequestContext = Mock()
    final ConnectionOperationParameters parameters = Stub()
    final ProviderOperationParameters providerParameters = Stub()

    //declared type-lessly to work around groovy eclipse plugin bug
    final executer = new LoggingBridgingBuildActionExecuter(target, loggingManager)

    def configuresLoggingWhileActionIsExecuting() {
        when:
        executer.execute(action, parameters, buildRequestContext)

        then:
        1 * loggingManager.addOutputEventListener(!null)
        1 * loggingManager.start()
        1 * target.execute(action, parameters, buildRequestContext)
        1 * loggingManager.stop()
    }

    def restoresLoggingWhenActionFails() {
        def failure = new RuntimeException()

        when:
        executer.execute(action, parameters, buildRequestContext)

        then:
        RuntimeException e = thrown()
        e == failure
        1 * loggingManager.start()
        1 * target.execute(action, parameters, buildRequestContext) >> { throw failure }
        1 * loggingManager.stop()
    }

    def "sets log level accordingly"() {
        given:
        parameters.operationParameters >> providerParameters
        providerParameters.getBuildLogLevel() >> LogLevel.QUIET

        when:
        executer.execute(action, parameters, buildRequestContext)

        then:
        1 * loggingManager.setLevelInternal(LogLevel.QUIET)
    }
}
