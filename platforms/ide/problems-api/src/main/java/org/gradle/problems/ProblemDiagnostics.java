/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.problems;

import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.problems.failure.Failure;

import javax.annotation.Nullable;
import java.util.List;

/**
 * An immutable set of diagnostic information for a problem.
 */
public interface ProblemDiagnostics {

    /**
     * A stack tracing failure associated with the problem.
     * <p>
     * Usually, if the problem was caused by an exception, the failure would correspond to that exception.
     * However, problems that are registered explicitly (e.g. deprecation warnings) will not have an associated exception.
     * In this case, the failure can be synthetic to provide the stack trace for the problem origin.
     * <p>
     * The failure can also be omitted due to limits. Its absence does not mean there was no exception causing the problem.
     */
    @Nullable
    Failure getFailure();

    /**
     * Returns an exception that can be thrown when this problem should result in an error.
     *
     * <p>Not every problem has a useful exception associated with it.</p>
     */
    @Nullable
    Throwable getException();

    /**
     * Returns the stack trace that can be reported to the user about where the problem occurred.
     *
     * <p>Not every problem has a meaningful stack, even when the problem has an associated exception. Returns an empty list in this case.</p>
     */
    List<StackTraceElement> getStack();

    /**
     * Returns the location of the problem, if known.
     */
    @Nullable
    Location getLocation();

    /**
     * Returns the user code context when this problem was triggered.
     */
    @Nullable
    UserCodeSource getSource();
}
