/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.steps;

import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionEngine.Execution;
import org.gradle.internal.execution.ExecutionRequest;
import org.gradle.internal.execution.UnitOfWork;

import java.time.Duration;

public class Result {

    private final Duration duration;
    private final Try<Execution> execution;

    protected Result(Duration duration, Try<Execution> execution) {
        this.duration = duration;
        this.execution = execution;
    }

    protected Result(Result parent) {
        this(parent.getDuration(), parent.getExecution());
    }

    public static Result failed(Throwable t, Duration duration) {
        return new Result(duration, Try.failure(t));
    }

    public static Result success(Duration duration, Execution outcome) {
        return new Result(duration, Try.successful(outcome));
    }

    /**
     * The elapsed wall clock time of executing the actual work, i.e. the time it took to execute the
     * {@link UnitOfWork#execute(ExecutionRequest)} method.
     *
     * The execution time refers to when and where the work was executed: if a previous result was reused,
     * then this method will return the time it took to produce the previous result.
     *
     * Note that reused work times might be different to what it would actually take to execute the work
     * in the current build for a number of reasons:
     *
     * <ul>
     *     <li>reused work could have happened on a remote machine with different hardware capabilities,</li>
     *     <li>there might have been more or less load on the machine producing the reused work,</li>
     *     <li>the work reused might have been executed incrementally,</li>
     *     <li>had there been no work to reuse, the local execution might have happened happen incrementally.</li>
     * </ul>
     */
    public Duration getDuration() {
        return duration;
    }

    public Try<Execution> getExecution() {
        return execution;
    }
}
