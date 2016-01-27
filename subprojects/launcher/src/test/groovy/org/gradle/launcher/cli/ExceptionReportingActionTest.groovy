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
import spock.lang.Specification
import org.gradle.launcher.bootstrap.ExecutionListener
import org.gradle.initialization.ReportedException

class ExceptionReportingActionTest extends Specification {
    final Action<ExecutionListener> target = Mock()
    final ExecutionListener listener = Mock()
    final Action<Throwable> reporter = Mock()
    final ExceptionReportingAction action = new ExceptionReportingAction(target, reporter)

    def executesAction() {
        when:
        action.execute(listener)

        then:
        1 * target.execute(listener)
        0 * _._
    }

    def reportsExceptionThrownByAction() {
        def failure = new RuntimeException()

        when:
        action.execute(listener)

        then:
        1 * target.execute(listener) >> { throw failure }
        1 * reporter.execute(failure)
        1 * listener.onFailure(failure)
        0 * _._
    }

    def doesNotReportAlreadyReportedExceptionThrownByAction() {
        def cause = new RuntimeException()
        def failure = new ReportedException(cause)

        when:
        action.execute(listener)

        then:
        1 * target.execute(listener) >> { throw failure }
        1 * listener.onFailure(cause)
        0 * _._
    }
}
