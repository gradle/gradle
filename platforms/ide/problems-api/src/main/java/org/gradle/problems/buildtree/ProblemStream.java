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

/// Produces [ProblemDiagnostics] describing where a problem came from.
///
/// Capturing a full stack trace is expensive, so a stream limits how many it takes. Past that limit a
/// cheaper partial capture, walking only as far as the calling script, still locates the problem. How
/// many of each a stream allows is decided by [ProblemDiagnosticsFactory] when it creates the stream, so
/// a caller cannot assume a stack or a location is present.
///
/// A caller that does not need a full stack says so, and the stream spends the cheaper budget instead.
/// A caller whose problem must be located whatever the budget says so too, and is never refused.
///
/// | Method | Stack describes | Retains exception | Spends the full budget | Falls back to a partial capture |
/// |---|---|---|---|---|
/// | [#forCurrentCaller()] | calling thread | no | yes | yes |
/// | [#forCurrentCaller(StackTraceTransformer)] | calling thread | no | yes | yes |
/// | [#forCurrentCallerLocationOnly] | calling thread | no | never | always |
/// | [#forCurrentCallerAlwaysLocated] | calling thread | no | yes | yes, and never limited |
/// | [#forCurrentCallerWithException] | calling thread | while full captures last | yes | yes |
/// | [#forThrownException] | the given exception | yes | no | never limited |
@ServiceScope(Scope.BuildTree.class)
public interface ProblemStream {

    /// Locates the calling thread, for a problem with no exception of its own.
    ProblemDiagnostics forCurrentCaller();

    /// As [#forCurrentCaller()], but `transformer` filters the reported stack alone.
    ///
    /// This exists only because callers still sanitize for themselves. Move sanitizing behind this service
    /// and remove this method.
    ProblemDiagnostics forCurrentCaller(StackTraceTransformer transformer);

    /// Locates the calling thread without a full stack, for a problem that reports no stack of its own.
    ///
    /// Only the partial-capture budget is spent, so a caller that would discard a full stack anyway does not
    /// deny one to a problem that reports it. Past that budget there is no location, as with [#forCurrentCaller()].
    ProblemDiagnostics forCurrentCallerLocationOnly();

    /// Locates the calling thread whatever the budgets say, for a problem that must always be located.
    ///
    /// Never refused, so reserve it for problems that cannot be reported without a location. A full capture
    /// while the budget allows, so such a problem is described as well as any other; past it a partial
    /// capture, which still locates it.
    ProblemDiagnostics forCurrentCallerAlwaysLocated();

    /// As [#forCurrentCaller()], but also retains an exception for the caller to rethrow.
    ///
    /// Only a full capture can produce that exception, so expect it to be absent once those run out.
    ProblemDiagnostics forCurrentCallerWithException(ExceptionCreator exceptionCreator);

    /// Describes `exception` and retains it, ignoring the calling thread's state.
    ///
    /// Never limited: creating the exception already paid for its stack, so the result always has one.
    ProblemDiagnostics forThrownException(Throwable exception);

    interface StackTraceTransformer {
        List<StackTraceElement> transform(StackTraceElement[] original);
    }

    /// Creates the exception a problem retains for the caller to rethrow.
    @FunctionalInterface
    interface ExceptionCreator {

        /// Creates the exception where the problem is reported, so its stack locates that problem.
        Throwable create();
    }
}
