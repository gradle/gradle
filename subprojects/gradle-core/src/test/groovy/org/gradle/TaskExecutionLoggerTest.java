/*
 * Copyright 2009 the original author or authors.
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
package org.gradle;

import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionResult;
import org.gradle.api.logging.Logger;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class TaskExecutionLoggerTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final Logger logger = context.mock(Logger.class);
    private final Task task = context.mock(Task.class);
    private final TaskExecutionResult result = context.mock(TaskExecutionResult.class);
    private final TaskExecutionLogger executionLogger = new TaskExecutionLogger(logger);

    @Test
    public void logsTaskExecution() {
        context.checking(new Expectations() {{
            allowing(task).getPath();
            will(returnValue("path"));
            allowing(result).getSkipMessage();
            will(returnValue(null));
        }});

        executionLogger.beforeExecute(task);

        context.checking(new Expectations() {{
            one(logger).lifecycle("path");
        }});

        executionLogger.beforeActions(task);
        executionLogger.afterActions(task);
        executionLogger.afterExecute(task, result);
    }

    @Test
    public void logsSkippedTaskExecution() {
        context.checking(new Expectations() {{
            allowing(task).getPath();
            will(returnValue("path"));
            allowing(result).getSkipMessage();
            will(returnValue("skipped"));
        }});

        executionLogger.beforeExecute(task);

        context.checking(new Expectations() {{
            one(logger).lifecycle("{} {}", "path", "skipped");
        }});

        executionLogger.afterExecute(task, result);
    }
}
