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
import org.gradle.initialization.BuildAction
import org.gradle.internal.Factory
import org.gradle.launcher.exec.GradleLauncherActionExecuter
import org.gradle.logging.LoggingManagerInternal
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters
import spock.lang.Specification

class LoggingBridgingGradleLauncherActionExecuterTest extends Specification {
    final GradleLauncherActionExecuter<ProviderOperationParameters> target = Mock()
    final Factory<LoggingManagerInternal> loggingManagerFactory = Mock()
    final LoggingManagerInternal loggingManager = Mock()
    final BuildAction<String> action = Mock()
    final ProviderOperationParameters parameters = Mock()

    //declared type-lessly to work around groovy eclipse plugin bug
    final executer = new LoggingBridgingGradleLauncherActionExecuter(target, loggingManagerFactory)

    def configuresLoggingWhileActionIsExecuting() {
        when:
        executer.execute(action, parameters)

        then:
        1 * loggingManagerFactory.create() >> loggingManager
        1 * loggingManager.addOutputEventListener(!null)
        1 * loggingManager.start()
        1 * target.execute(action, parameters)
        1 * loggingManager.stop()
    }

    def restoresLoggingWhenActionFails() {
        def failure = new RuntimeException()

        when:
        executer.execute(action, parameters)

        then:
        RuntimeException e = thrown()
        e == failure
        1 * loggingManagerFactory.create() >> loggingManager
        1 * loggingManager.start()
        1 * target.execute(action, parameters) >> {throw failure}
        1 * loggingManager.stop()
    }

    def "sets log level accordingly"() {
        given:
        loggingManagerFactory.create() >> loggingManager
        parameters.getBuildLogLevel() >> LogLevel.QUIET

        when:
        executer.execute(action, parameters)
        
        then:
        1 * loggingManager.setLevel(LogLevel.QUIET)
    }
}
