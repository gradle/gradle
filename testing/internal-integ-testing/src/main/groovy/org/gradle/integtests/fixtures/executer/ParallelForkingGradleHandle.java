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

package org.gradle.integtests.fixtures.executer;

import org.gradle.api.Action;
import org.gradle.internal.Factory;
import org.gradle.process.internal.BaseExecHandleBuilder;
import org.gradle.util.internal.IncubationLogger;

import java.io.PipedOutputStream;

import static java.lang.String.format;

public class ParallelForkingGradleHandle extends ForkingGradleHandle {

    public ParallelForkingGradleHandle(PipedOutputStream stdinPipe, boolean isDaemon, Action<ExecutionResult> resultAssertion, String outputEncoding, Factory<BaseExecHandleBuilder> execHandleFactory, DurationMeasurement durationMeasurement) {
        super(stdinPipe, isDaemon, resultAssertion, outputEncoding, execHandleFactory, durationMeasurement);
    }

    @Override
    protected ExecutionResult toExecutionResult(String output, String error) {
        return new ParallelExecutionResult(output, error);
    }

    @Override
    protected ExecutionResult toExecutionFailure(String output, String error) {
        return new ParallelExecutionResult(output, error);
    }

    /**
     * Need a different output comparator for parallel execution.
     */
    private static class ParallelExecutionResult extends OutputScrapingExecutionFailure {
        public ParallelExecutionResult(String output, String error) {
            super(output, error, true);
        }

        @Override
        public String getNormalizedOutput() {
            String output = super.getNormalizedOutput();
            String parallelWarningPrefix = String.format(IncubationLogger.INCUBATION_MESSAGE, ".*");
            return output.replaceFirst(format("(?m)%s.*$\n", parallelWarningPrefix), "");
        }

        @Override
        public ExecutionResult assertOutputEquals(String expectedOutput, boolean ignoreExtraLines, boolean ignoreLineOrder) {
            // We always ignore line order for matching out of parallel builds
            super.assertOutputEquals(expectedOutput, ignoreExtraLines, true);
            return this;
        }
    }
}
