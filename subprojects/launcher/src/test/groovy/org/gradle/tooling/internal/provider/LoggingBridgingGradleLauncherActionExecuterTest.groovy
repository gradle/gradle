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
import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1
import org.gradle.launcher.GradleLauncherActionExecuter
import org.gradle.logging.LoggingManagerInternal
import org.gradle.initialization.GradleLauncherAction
import org.gradle.api.internal.Factory

class LoggingBridgingGradleLauncherActionExecuterTest extends Specification {
    final GradleLauncherActionExecuter<BuildOperationParametersVersion1> target = Mock()
    final Factory<LoggingManagerInternal> loggingManagerFactory = Mock()
    final LoggingManagerInternal loggingManager = Mock()
    final GradleLauncherAction<String> action = Mock()
    final BuildOperationParametersVersion1 parameters = Mock()
    final LoggingBridgingGradleLauncherActionExecuter executer = new LoggingBridgingGradleLauncherActionExecuter(target, loggingManagerFactory)

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
}
