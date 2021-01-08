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
package org.gradle.launcher.cli

import org.gradle.api.Action
import org.gradle.initialization.ReportedException
import org.gradle.initialization.exception.InitializationException
import org.gradle.internal.exceptions.ContextAwareException
import org.gradle.internal.logging.LoggingOutputInternal
import org.gradle.internal.service.ServiceCreationException
import org.gradle.launcher.bootstrap.ExecutionListener
import spock.lang.Specification

class ExceptionReportingActionTest extends Specification {
    final Action<ExecutionListener> target = Mock()
    final ExecutionListener listener = Mock()
    final Action<Throwable> reporter = Mock()
    final LoggingOutputInternal loggingOutput = Mock()
    final ExceptionReportingAction action = new ExceptionReportingAction(reporter, loggingOutput, target)

    def "executes Action"() {
        when:
        action.execute(listener)

        then:
        1 * target.execute(listener)
        1 * loggingOutput.flush()
        0 * _._
    }

    def "reports exception thrown by Action"() {
        def failure = new RuntimeException()

        when:
        action.execute(listener)

        then:
        1 * target.execute(listener) >> { throw failure }
        1 * loggingOutput.flush()
        1 * reporter.execute(failure)
        1 * listener.onFailure(failure)
        0 * _._
    }

    def "service creation failure is reported as initialization exception"() {
        def failure = new ServiceCreationException("Service creation failed")
        RuntimeException reportedException

        when:
        action.execute(listener)

        then:
        1 * target.execute(listener) >> { throw failure }
        1 * loggingOutput.flush()
        1 * reporter.execute(_) >> { arguments -> reportedException = arguments[0] }
        reportedException instanceof ContextAwareException
        reportedException.getCause() instanceof InitializationException
        reportedException.getCause().getCause() == failure
        1 * listener.onFailure(failure)
        0 * _._
    }

    def "does not report already reported exception thrown by Action"() {
        def cause = new RuntimeException()
        def failure = new ReportedException(cause)

        when:
        action.execute(listener)

        then:
        1 * target.execute(listener) >> { throw failure }
        1 * loggingOutput.flush()
        1 * listener.onFailure(failure)
        0 * _._
    }
}
