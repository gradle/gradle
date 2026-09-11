/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.problems.buildtree.ProblemStream;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/// Captures the stack trace that locates a problem, within per-stream budgets.
final class StackTraceCapturer {

    private final AtomicInteger remainingFull;
    private final AtomicInteger remainingBounded;
    private final BoundedCallerStackCapturer boundedCallerStackCapturer;

    StackTraceCapturer(int fullBudget, int boundedBudget, BoundedCallerStackCapturer boundedCallerStackCapturer) {
        this.remainingFull = new AtomicInteger(fullBudget);
        this.remainingBounded = new AtomicInteger(boundedBudget);
        this.boundedCallerStackCapturer = boundedCallerStackCapturer;
    }

    /// Captures a throwable that locates the calling thread, never one to retain as an exception.
    @Nullable
    Throwable captureLocation() {
        if (remainingFull.getAndDecrement() > 0) {
            return new Exception();
        }
        return captureBoundedLocation();
    }

    /// Captures an exception for the caller to retain, while the full budget lasts.
    ///
    /// There is no bounded fallback: a bounded capture locates the call without being an exception anyone
    /// can surface. Use [#captureLocation()] for a problem that could not afford one.
    @Nullable
    Throwable captureRetainableException(ProblemStream.ExceptionCreator exceptionCreator) {
        if (remainingFull.getAndDecrement() > 0) {
            return exceptionCreator.create();
        }
        return null;
    }

    /// Captures a cheap partial stack, enough to locate the caller.
    @Nullable
    private Throwable captureBoundedLocation() {
        if (remainingBounded.getAndDecrement() > 0) {
            return boundedCallerStackCapturer.captureCallerStack();
        }
        return null;
    }
}
