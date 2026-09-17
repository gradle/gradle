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

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.problems.ProblemDiagnostics;

/**
 * A factory for producing {@link ProblemDiagnostics} for a problem.
 */
@ServiceScope(Scope.BuildTree.class)
public interface ProblemDiagnosticsFactory {
    /**
     * Creates a stream that limits how many full stack traces it captures, and limits the cheaper partial
     * captures it falls back on as well. Problems reported once both run out say which script they came
     * from, but not which line.
     *
     * <p>Each stream counts its own captures, so one kind of problem cannot exhaust the limits of another.</p>
     */
    ProblemStream newStream();

    /**
     * Creates a stream that limits full stack traces like {@link #newStream()}, but falls back on as many
     * partial captures as it needs, so every problem keeps its line.
     *
     * <p>Used where every problem is shown individually and a missing line would be noticed, namely
     * {@code --warning-mode=all} and {@code --warning-mode=fail}.</p>
     */
    ProblemStream newUnlimitedStream();

    /**
     * Returns diagnostics based on given exception. Does not use any state from the calling thread.
     *
     * <p>This method is intended to be used for inspecting exceptions that may have been thrown in some other context, such as in a different thread or process.</p>
     */
    ProblemDiagnostics forException(Throwable exception);
}
