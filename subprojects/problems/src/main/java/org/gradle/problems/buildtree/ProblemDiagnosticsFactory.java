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

package org.gradle.problems.buildtree;

import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.problems.ProblemDiagnostics;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A factory for producing {@link ProblemDiagnostics} for a problem.
 */
@ServiceScope(Scopes.BuildTree.class)
public interface ProblemDiagnosticsFactory {
    /**
     * Returns diagnostics based on the state of the calling thread.
     *
     * @param transformer A transformer to use to sanitize the stack trace.
     */
    ProblemDiagnostics forCurrentCaller(StackTraceTransformer transformer);

    /**
     * Returns diagnostics based on the state of the calling thread.
     *
     * @param exception The exception that represents the failure.
     */
    ProblemDiagnostics forCurrentCaller(@Nullable Throwable exception);

    /**
     * Returns diagnostics based on given exception. Does not use any state from the calling thread.
     */
    ProblemDiagnostics forException(Throwable exception);

    interface StackTraceTransformer {
        List<StackTraceElement> transform(StackTraceElement[] original);
    }
}
