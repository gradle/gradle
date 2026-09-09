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

package org.gradle.internal.problems;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.problems.failure.FailureFactory;
import org.gradle.problems.Location;
import org.gradle.problems.ProblemDiagnostics;
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory;
import org.gradle.problems.buildtree.ProblemStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

public class DefaultProblemDiagnosticsFactory implements ProblemDiagnosticsFactory {

    private static class CopyStackTraceTransFormer implements ProblemStream.StackTraceTransformer {
        @Override
        public List<StackTraceElement> transform(StackTraceElement[] original) {
            return ImmutableList.copyOf(original);
        }

    }

    private static final ProblemStream.StackTraceTransformer NO_OP = new CopyStackTraceTransFormer();

    /// Caps the full stack traces captured per stream, since capturing one is expensive.
    public static final String MAX_STACKTRACE_COUNT_PROPERTY = "org.gradle.internal.problem.diagnostics.stacktrace-count.max";

    private static final int DEFAULT_MAX_STACKTRACE_COUNT = 50;

    /// Caps the cheap bounded captures past the full cap, keeping the stack walk cost negligible.
    ///
    /// Past this cap a problem is located by its user code source, such as the script that
    /// reported it, rather than by a line within it.
    public static final String MAX_BOUNDED_CAPTURES_PROPERTY = "org.gradle.internal.problem.diagnostics.bounded-captures.max";

    private static final int DEFAULT_MAX_BOUNDED_CAPTURES = 2000;

    private final FailureFactory failureFactory;
    private final ProblemLocationAnalyzer locationAnalyzer;
    private final UserCodeApplicationContext userCodeContext;
    private final int maxStackTraces;
    private final int maxBoundedCaptures;
    private final BoundedCallerStackCapturer boundedCallerStackCapturer;

    @Inject
    public DefaultProblemDiagnosticsFactory(
        FailureFactory failureFactory,
        ProblemLocationAnalyzer locationAnalyzer,
        UserCodeApplicationContext userCodeContext,
        InternalOptions internalOptions,
        BoundedCallerStackCapturer boundedCallerStackCapturer
    ) {
        this(
            failureFactory,
            locationAnalyzer,
            userCodeContext,
            internalOptions.getInt(MAX_STACKTRACE_COUNT_PROPERTY, DEFAULT_MAX_STACKTRACE_COUNT),
            internalOptions.getInt(MAX_BOUNDED_CAPTURES_PROPERTY, DEFAULT_MAX_BOUNDED_CAPTURES),
            boundedCallerStackCapturer
        );
    }

    @VisibleForTesting
    DefaultProblemDiagnosticsFactory(
        FailureFactory failureFactory,
        ProblemLocationAnalyzer locationAnalyzer,
        UserCodeApplicationContext userCodeContext,
        int maxStackTraces,
        int maxBoundedCaptures,
        BoundedCallerStackCapturer boundedCallerStackCapturer
    ) {
        this.failureFactory = failureFactory;
        this.locationAnalyzer = locationAnalyzer;
        this.userCodeContext = userCodeContext;
        this.maxStackTraces = maxStackTraces;
        this.maxBoundedCaptures = maxBoundedCaptures;
        this.boundedCallerStackCapturer = boundedCallerStackCapturer;
    }

    @Override
    public ProblemStream newStream() {
        return new DefaultProblemStream(maxStackTraces, maxBoundedCaptures);
    }

    @Override
    public ProblemStream newUnlimitedStream() {
        return new DefaultProblemStream(maxStackTraces, Integer.MAX_VALUE);
    }

    @Override
    public ProblemDiagnostics forException(Throwable exception) {
        return diagnosticsForThrownException(exception);
    }

    /// Diagnostics for a problem described by a thrown exception, blaming the first user code in it.
    private ProblemDiagnostics diagnosticsForThrownException(Throwable exception) {
        return diagnostics(exception, true, exception, NO_OP);
    }

    /// Diagnostics for a problem reported with an exception, blaming the deepest user code in it.
    private ProblemDiagnostics diagnosticsForReportedProblem(Throwable exception) {
        return diagnostics(exception, false, exception, NO_OP);
    }

    /// Diagnostics for a problem located from a captured stack, which is then discarded.
    private ProblemDiagnostics diagnosticsForCapturedStack(@Nullable Throwable capture, ProblemStream.StackTraceTransformer transformer) {
        return diagnostics(capture, false, null, transformer);
    }

    private ProblemDiagnostics diagnostics(
        @Nullable Throwable throwable,
        boolean fromException,
        @Nullable Throwable exceptionToReport,
        ProblemStream.StackTraceTransformer transformer
    ) {
        UserCodeApplicationContext.Application applicationContext = userCodeContext.current();

        if (applicationContext == null && throwable == null) {
            return NoOpProblemDiagnosticsFactory.EMPTY_DIAGNOSTICS;
        }

        List<StackTraceElement> stackTrace = Collections.emptyList();
        Failure failure = null;
        Location location = null;
        if (throwable != null) {
            stackTrace = transformer.transform(throwable.getStackTrace());
            failure = failureFactory.create(throwable);
            location = locationAnalyzer.locationForUsage(failure, fromException);
        }

        UserCodeSource source = applicationContext != null ? applicationContext.getSource() : null;
        // A throwable that is not reported was created only to hold a stack, so its type describes
        // nothing about the problem and must not reach the report as the problem's failure.
        Failure reportedFailure = exceptionToReport == null ? null : failure;
        return new DefaultProblemDiagnostics(reportedFailure, exceptionToReport, stackTrace, location, source);
    }

    @NullMarked
    private class DefaultProblemStream implements ProblemStream {
        private final StackTraceCapturer capturer;

        public DefaultProblemStream(int fullBudget, int boundedBudget) {
            this.capturer = new StackTraceCapturer(fullBudget, boundedBudget, boundedCallerStackCapturer);
        }

        @Override
        public ProblemDiagnostics forThrownException(Throwable exception) {
            return diagnosticsForThrownException(exception);
        }

        @Override
        public ProblemDiagnostics forCurrentCaller() {
            return diagnosticsForCapturedStack(capturer.captureLocation(), NO_OP);
        }

        @Override
        public ProblemDiagnostics forCurrentCallerWithException(ExceptionCreator exceptionCreator) {
            Throwable retained = capturer.captureRetainableException(exceptionCreator);
            if (retained != null) {
                return diagnosticsForReportedProblem(retained);
            }
            // Not affordable within the budget, so locate the problem without an exception.
            return diagnosticsForCapturedStack(capturer.captureLocation(), NO_OP);
        }

        @Override
        public ProblemDiagnostics forCurrentCaller(StackTraceTransformer transformer) {
            return diagnosticsForCapturedStack(capturer.captureLocation(), transformer);
        }


    }

    private static class DefaultProblemDiagnostics implements ProblemDiagnostics {
        private final Failure failure;
        private final Throwable exception;
        private final List<StackTraceElement> stackTrace;
        private final Location location;
        private final UserCodeSource source;

        public DefaultProblemDiagnostics(
            @Nullable Failure stackTracingFailure,
            @Nullable Throwable exception,
            List<StackTraceElement> stackTrace,
            @Nullable Location location,
            @Nullable UserCodeSource source
        ) {
            this.failure = stackTracingFailure;
            this.exception = exception;
            this.stackTrace = stackTrace;
            this.location = location;
            this.source = source;
        }

        @Nullable
        @Override
        public Failure getFailure() {
            return failure;
        }

        @Nullable
        @Override
        public Throwable getException() {
            return exception;
        }

        @Override
        public List<StackTraceElement> getStack() {
            return stackTrace;
        }

        @Nullable
        @Override
        public Location getLocation() {
            return location;
        }

        @Nullable
        @Override
        public UserCodeSource getSource() {
            return source;
        }
    }
}
