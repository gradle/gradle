/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.fixtures;

import org.gradle.internal.Factory;
import org.gradle.process.internal.AbstractExecHandleBuilder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class ParallelForkingGradleHandle extends ForkingGradleHandle {

    public ParallelForkingGradleHandle(String outputEncoding, Factory<? extends AbstractExecHandleBuilder> execHandleFactory) {
        super(outputEncoding, execHandleFactory);
    }

    @Override
    protected ExecutionResult toExecutionResult(String output, String error) {
        return new ParallelExecutionResult(transformStandardOutput(output), transformErrorOutput(error));
    }

    @Override
    protected ExecutionResult toExecutionFailure(String output, String error) {
        return new ParallelExecutionResult(transformStandardOutput(output), transformErrorOutput(error));
    }

    /**
     * Need a different output comparator for parallel execution.
     */
    private static class ParallelExecutionResult extends OutputScrapingExecutionFailure {
        public ParallelExecutionResult(String output, String error) {
            super(output, error);
        }

        @Override
        public ExecutionResult assertTasksExecuted(String... taskPaths) {
            Set<String> expectedTasks = new HashSet<String>(Arrays.asList(taskPaths));
            assertThat(String.format("Expected tasks %s not found in process output:%n%s", expectedTasks, getOutput()), new HashSet<String>(getExecutedTasks()), equalTo(expectedTasks));
            return this;
        }

        @Override
        public String getOutput() {
            String output = super.getOutput();
            String parallelWarningPrefix = "Parallel project execution is an \"incubating\" feature";
            if (output.startsWith(parallelWarningPrefix)) {
                return output.replaceFirst(String.format("(?m)%s.+$\n", parallelWarningPrefix), "");
            }
            return output;
        }

        @Override
        public ExecutionResult assertOutputEquals(String expectedOutput, boolean ignoreExtraLines) {
            new ParallelOutputMatcher().assertOutputMatches(expectedOutput, getOutput(), ignoreExtraLines);
            return this;
        }
    }
}
