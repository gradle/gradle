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
package org.gradle.launcher.cli;

import org.gradle.api.Action;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.logging.LoggingOutputInternal;
import org.gradle.launcher.bootstrap.ExecutionListener;

public class ExceptionReportingAction implements Action<ExecutionListener> {
    private final Action<ExecutionListener> action;
    private final Action<Throwable> reporter;
    private final LoggingOutputInternal loggingOutput;

    public ExceptionReportingAction(Action<ExecutionListener> action, Action<Throwable> reporter, LoggingOutputInternal loggingOutput) {
        this.action = action;
        this.reporter = reporter;
        this.loggingOutput = loggingOutput;
    }

    @Override
    public void execute(ExecutionListener executionListener) {
        try {
            try {
                action.execute(executionListener);
            } finally {
                loggingOutput.flush();
            }
        } catch (ReportedException e) {
            // Exception has already been reported
            executionListener.onFailure(e);
        } catch (Throwable t) {
            reporter.execute(t);
            executionListener.onFailure(t);
        }
    }
}
