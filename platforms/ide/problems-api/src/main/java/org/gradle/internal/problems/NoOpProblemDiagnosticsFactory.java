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

import com.google.common.base.Supplier;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.problems.Location;
import org.gradle.problems.ProblemDiagnostics;
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory;
import org.gradle.problems.buildtree.ProblemStream;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class NoOpProblemDiagnosticsFactory implements ProblemDiagnosticsFactory {
    public static final ProblemDiagnostics EMPTY_DIAGNOSTICS = new ProblemDiagnostics() {
        @Nullable
        @Override
        public Failure getFailure() {
            return null;
        }

        @Nullable
        @Override
        public Throwable getException() {
            return null;
        }

        @Override
        public List<StackTraceElement> getStack() {
            return Collections.emptyList();
        }

        @Nullable
        @Override
        public Location getLocation() {
            return null;
        }

        @Nullable
        @Override
        public UserCodeSource getSource() {
            return null;
        }
    };

    public static final ProblemStream EMPTY_STREAM = new ProblemStream() {
        @Override
        public ProblemDiagnostics forCurrentCaller(ProblemStream.StackTraceTransformer transformer) {
            return EMPTY_DIAGNOSTICS;
        }

        @Override
        public ProblemDiagnostics forCurrentCaller(@Nullable Throwable exception) {
            return EMPTY_DIAGNOSTICS;
        }

        @Override
        public ProblemDiagnostics forCurrentCaller() {
            return EMPTY_DIAGNOSTICS;
        }

        @Override
        public ProblemDiagnostics forCurrentCaller(Supplier<? extends Throwable> exceptionFactory) {
            return EMPTY_DIAGNOSTICS;
        }
    };

    @Override
    public ProblemStream newStream() {
        return EMPTY_STREAM;
    }

    @Override
    public ProblemStream newUnlimitedStream() {
        return EMPTY_STREAM;
    }

    @Override
    public ProblemDiagnostics forException(Throwable exception) {
        return EMPTY_DIAGNOSTICS;
    }
}
