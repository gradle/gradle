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
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import org.gradle.internal.buildtree.BuildModelParameters;
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
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultProblemDiagnosticsFactory implements ProblemDiagnosticsFactory {

    private static class CopyStackTraceTransFormer implements ProblemStream.StackTraceTransformer {
        @Override
        public List<StackTraceElement> transform(StackTraceElement[] original) {
            return ImmutableList.copyOf(original);
        }

    }

    private static final ProblemStream.StackTraceTransformer NO_OP = new CopyStackTraceTransFormer();

    private static final Supplier<Throwable> EXCEPTION_FACTORY = new Supplier<Throwable>() {
        @Override
        public Throwable get() {
            return new Exception();
        }
    };

    private static final int MAX_STACKTRACE_COUNT = 50;
    private static final int ISOLATED_PROJECTS_MAX_STACKTRACE_COUNT = 5000;

    // Budget for bounded location captures past the cap: capping the count keeps the stack walk cost
    // bounded and negligible at scale. Builds with more distinct call sites lose locations past it.
    private static final int MAX_BOUNDED_CAPTURES = 2000;

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
        BuildModelParameters buildModelParameters,
        BoundedCallerStackCapturer boundedCallerStackCapturer
    ) {
        this(failureFactory, locationAnalyzer, userCodeContext, getMaxStackTraces(buildModelParameters), MAX_BOUNDED_CAPTURES, boundedCallerStackCapturer);
    }

    private static int getMaxStackTraces(BuildModelParameters buildModelParameters) {
        return buildModelParameters.isIsolatedProjects() ? ISOLATED_PROJECTS_MAX_STACKTRACE_COUNT : MAX_STACKTRACE_COUNT;
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
        return new DefaultProblemStream();
    }

    @Override
    public ProblemStream newUnlimitedStream() {
        DefaultProblemStream defaultProblemStream = new DefaultProblemStream();
        defaultProblemStream.remainingStackTraces.set(Integer.MAX_VALUE);
        return defaultProblemStream;
    }

    @Override
    public ProblemDiagnostics forException(Throwable exception) {
        return locationFromStackTrace(exception, true, true, NO_OP);
    }

    private ProblemDiagnostics locationFromStackTrace(@Nullable Throwable throwable, boolean fromException, boolean keepException, ProblemStream.StackTraceTransformer transformer) {
        UserCodeApplicationContext.Application applicationContext = userCodeContext.current();

        if (applicationContext == null && throwable == null) {
            return NoOpProblemDiagnosticsFactory.EMPTY_DIAGNOSTICS;
        }

        List<StackTraceElement> stackTrace = Collections.emptyList();
        Failure stackTracingFailure = null;
        Location location = null;
        if (throwable != null) {
            stackTrace = transformer.transform(throwable.getStackTrace());
            stackTracingFailure = failureFactory.create(throwable);
            location = locationAnalyzer.locationForUsage(stackTracingFailure, fromException);
        }

        UserCodeSource source = applicationContext != null ? applicationContext.getSource() : null;
        return new DefaultProblemDiagnostics(stackTracingFailure, keepException ? throwable : null, stackTrace, location, source);
    }

    @NullMarked
    private class DefaultProblemStream implements ProblemStream {
        private final AtomicInteger remainingStackTraces = new AtomicInteger();
        private final AtomicInteger remainingBoundedCaptures = new AtomicInteger();

        public DefaultProblemStream() {
            remainingStackTraces.set(maxStackTraces);
            remainingBoundedCaptures.set(maxBoundedCaptures);
        }

        @Override
        public ProblemDiagnostics forCurrentCaller(@Nullable Throwable exception) {
            if (exception == null) {
                return locationFromStackTrace(getImplicitCallerThrowable(), false, false, NO_OP);
            } else {
                return locationFromStackTrace(exception, true, true, NO_OP);
            }
        }

        @Override
        public ProblemDiagnostics forCurrentCaller() {
            return locationFromStackTrace(getImplicitCallerThrowable(), false, false, NO_OP);
        }

        @Override
        public ProblemDiagnostics forCurrentCaller(Supplier<? extends Throwable> exceptionFactory) {
            return locationFromStackTrace(getImplicitThrowable(exceptionFactory), false, true, NO_OP);
        }

        @Override
        public ProblemDiagnostics forCurrentCaller(StackTraceTransformer transformer) {
            return locationFromStackTrace(getImplicitCallerThrowable(), false, false, transformer);
        }

        /**
         * Within the limit, captures the full caller stack as before. Past it, captures a cheap bounded
         * prefix for location inference, up to a further budget, then stops: the bounded walk is cheap per
         * problem but not free, and a build firing tens of thousands of them (an IDE sync) would otherwise
         * pay it on every one, while ordinary builds stay well within the budget and keep all locations.
         */
        @Nullable
        private Throwable getImplicitCallerThrowable() {
            if (remainingStackTraces.getAndDecrement() > 0) {
                return EXCEPTION_FACTORY.get();
            }
            if (remainingBoundedCaptures.getAndDecrement() > 0) {
                return boundedCallerStackCapturer.captureCallerStack();
            }
            return null;
        }

        @Nullable
        private Throwable getImplicitThrowable(Supplier<? extends Throwable> factory) {
            if (remainingStackTraces.getAndDecrement() > 0) {
                return factory.get();
            } else {
                return null;
            }
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
