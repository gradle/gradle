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

/**
 * A factory for producing {@link ProblemDiagnostics} for a problem.
 */
@ServiceScope(Scopes.BuildTree.class)
public interface ProblemDiagnosticsFactory {
    /**
     * Creates a new stream of problems. Each problem stream produces diagnostics for some logical set of problems, applying limits to
     * the number of stack traces captured. Each stream has its own limits.
     */
    ProblemStream newStream();

    /**
     * Creates a new stream of problems without any limits.
     */
    ProblemStream newUnlimitedStream();

    /**
     * Returns diagnostics based on given exception. Does not use any state from the calling thread.
     *
     * <p>This method is intended to be used for inspecting exceptions that may have been thrown in some other context, such as in a different thread or process.</p>
     */
    ProblemDiagnostics forException(Throwable exception);
}
