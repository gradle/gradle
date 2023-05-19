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
import org.gradle.internal.featurelifecycle.NoOpProblemDiagnosticsFactory;
import org.gradle.problems.Location;
import org.gradle.problems.ProblemDiagnostics;
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultProblemDiagnosticsFactory implements ProblemDiagnosticsFactory {
    private static final StackTraceTransformer NO_OP = ImmutableList::copyOf;
    private final ProblemLocationAnalyzer locationAnalyzer;
    private final AtomicInteger remainingStackTraces = new AtomicInteger();

    @Inject
    public DefaultProblemDiagnosticsFactory(ProblemLocationAnalyzer locationAnalyzer) {
        this(locationAnalyzer, 50);
    }

    @VisibleForTesting
    DefaultProblemDiagnosticsFactory(ProblemLocationAnalyzer locationAnalyzer, int maxStackTraces) {
        this.locationAnalyzer = locationAnalyzer;
        remainingStackTraces.set(maxStackTraces);
    }

    @Override
    public ProblemDiagnostics forCurrentCaller(@Nullable Throwable exception) {
        if (exception == null) {
            return locationFromStackTrace(getThrowable(), false, NO_OP);
        } else {
            return locationFromStackTrace(exception, true, NO_OP);
        }
    }

    @Override
    public ProblemDiagnostics forCurrentCaller(StackTraceTransformer transformer) {
        return locationFromStackTrace(getThrowable(), false, transformer);
    }

    @Override
    public ProblemDiagnostics forException(Throwable exception) {
        return locationFromStackTrace(exception, true, NO_OP);
    }

    private Exception getThrowable() {
        if (remainingStackTraces.getAndDecrement() > 0) {
            return new Exception();
        } else {
            return null;
        }
    }

    private ProblemDiagnostics locationFromStackTrace(@Nullable Throwable throwable, boolean fromException, StackTraceTransformer transformer) {
        if (throwable == null) {
            return NoOpProblemDiagnosticsFactory.EMPTY_DIAGNOSTICS;
        }

        List<StackTraceElement> stackTrace = transformer.transform(throwable.getStackTrace());
        Location location = locationAnalyzer.locationForUsage(stackTrace, fromException);
        return new DefaultProblemDiagnostics(fromException ? throwable : null, stackTrace, location);
    }

    private static class DefaultProblemDiagnostics implements ProblemDiagnostics {
        private final Throwable exception;
        private final List<StackTraceElement> stackTrace;
        private final Location location;

        public DefaultProblemDiagnostics(@Nullable Throwable exception, List<StackTraceElement> stackTrace, @Nullable Location location) {
            this.exception = exception;
            this.stackTrace = stackTrace;
            this.location = location;
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
    }
}
