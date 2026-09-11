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

import java.util.List;

/// Produces [ProblemDiagnostics] for one logical set of problems, capping how many stack traces it captures.
///
/// Capturing a full trace is expensive, so each stream degrades as its budget runs out: a full trace, then
/// a cheap bounded walk, then nothing. Never assume a stack or a location is present.
///
/// | Method | Stack describes | Retains exception | Bounded fallback |
/// |---|---|---|---|
/// | [#forCurrentCaller()] | calling thread | no | yes |
/// | [#forCurrentCaller(StackTraceTransformer)] | calling thread | no | yes |
/// | [#forCurrentCallerWithException] | calling thread | within the full budget | yes |
/// | [#forThrownException] | the given exception | yes | not budgeted |
@ServiceScope(Scope.BuildTree.class)
public interface ProblemStream {

    /// Locates the calling thread, for a problem with no exception of its own.
    ProblemDiagnostics forCurrentCaller();

    /// As [#forCurrentCaller()], but `transformer` filters the reported stack alone.
    ///
    /// This exists only because callers still sanitize for themselves. Move sanitizing behind this service
    /// and remove this method.
    ProblemDiagnostics forCurrentCaller(StackTraceTransformer transformer);

    /// As [#forCurrentCaller()], but also retains an exception for the caller to rethrow.
    ///
    /// Only the budget for a full capture covers that exception, so handle its absence past the budget.
    ProblemDiagnostics forCurrentCallerWithException(ExceptionCreator exceptionCreator);

    /// Describes `exception` and retains it, ignoring the calling thread's state.
    ///
    /// Not budgeted: creating the exception already paid for the stack, so the result always has one.
    ProblemDiagnostics forThrownException(Throwable exception);

    interface StackTraceTransformer {
        List<StackTraceElement> transform(StackTraceElement[] original);
    }

    /// Creates the exception a problem retains for the caller to rethrow.
    interface ExceptionCreator {

        /// Creates the exception where the problem is reported, so its stack locates that problem.
        Throwable create();
    }
}
